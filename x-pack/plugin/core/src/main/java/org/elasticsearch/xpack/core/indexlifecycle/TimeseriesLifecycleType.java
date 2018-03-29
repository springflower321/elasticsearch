/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.core.indexlifecycle;

import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.util.set.Sets;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the lifecycle of an index from creation to deletion. A
 * {@link TimeseriesLifecycleType} is made up of a set of {@link Phase}s which it will
 * move through. Soon we will constrain the phases using some kinda of lifecycle
 * type which will allow only particular {@link Phase}s to be defined, will
 * dictate the order in which the {@link Phase}s are executed and will define
 * which {@link LifecycleAction}s are allowed in each phase.
 */
public class TimeseriesLifecycleType implements LifecycleType {
    public static final TimeseriesLifecycleType INSTANCE = new TimeseriesLifecycleType();

    public static final String TYPE = "timeseries";
    static final List<String> VALID_PHASES = Arrays.asList("hot", "warm", "cold", "delete");
    static final Set<String> VALID_HOT_ACTIONS = Sets.newHashSet(RolloverAction.NAME);
    static final Set<String> VALID_WARM_ACTIONS = Sets.newHashSet(AllocateAction.NAME, ReplicasAction.NAME,
        ShrinkAction.NAME, ForceMergeAction.NAME);
    static final Set<String> VALID_COLD_ACTIONS = Sets.newHashSet(AllocateAction.NAME, ReplicasAction.NAME);
    static final Set<String> VALID_DELETE_ACTIONS = Sets.newHashSet(DeleteAction.NAME);

    private TimeseriesLifecycleType() {
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public String getWriteableName() {
        return TYPE;
    }

    public List<Phase> getOrderedPhases(Map<String, Phase> phases) {
        return VALID_PHASES.stream().map(p -> phases.getOrDefault(p, null))
            .filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<LifecycleAction> getOrderedActions(Phase phase) {
        Map<String, LifecycleAction> actions = phase.getActions();
        switch (phase.getName()) {
            case "hot":
                return Stream.of(RolloverAction.NAME).map(a -> actions.getOrDefault(a, null))
                    .filter(Objects::nonNull).collect(Collectors.toList());
            case "warm":
                return Stream.of(AllocateAction.NAME, ShrinkAction.NAME, ForceMergeAction.NAME, ReplicasAction.NAME)
                    .map(a -> actions.getOrDefault(a, null)).filter(Objects::nonNull).collect(Collectors.toList());
            case "cold":
                return Stream.of(ReplicasAction.NAME, AllocateAction.NAME).map(a -> actions.getOrDefault(a, null))
                    .filter(Objects::nonNull).collect(Collectors.toList());
            case "delete":
                return Stream.of(DeleteAction.NAME).map(a -> actions.getOrDefault(a, null))
                    .filter(Objects::nonNull).collect(Collectors.toList());
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public void validate(Collection<Phase> phases) {
        Set<String> allowedPhases = new HashSet<>(VALID_PHASES);
        Map<String, Set<String>> allowedActions = new HashMap<>(allowedPhases.size());
        allowedActions.put("hot", VALID_HOT_ACTIONS);
        allowedActions.put("warm", VALID_WARM_ACTIONS);
        allowedActions.put("cold", VALID_COLD_ACTIONS);
        allowedActions.put("delete", VALID_DELETE_ACTIONS);
        phases.forEach(phase -> {
            if (allowedPhases.contains(phase.getName()) == false) {
                throw new IllegalArgumentException("Timeseries lifecycle does not support phase [" + phase.getName() + "]");
            }
            phase.getActions().forEach((actionName, action) -> {
                if (allowedActions.get(phase.getName()).contains(actionName) == false) {
                    throw new IllegalArgumentException("invalid action [" + actionName + "] " +
                        "defined in phase [" + phase.getName() +"]");
                }
            });
        });
    }
}