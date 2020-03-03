// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DfaComputationState {
  private final Set<GrControlFlowOwner> visitedFlowOwners = new HashSet<>();
  private final Map<GrControlFlowOwner, TypeDfaState> entranceStates = new HashMap<>();
  private final Map<GrControlFlowOwner, TypeDfaState> exitStates = new HashMap<>();

  public void markOwnerAsVisited(@NotNull GrControlFlowOwner owner) {
    visitedFlowOwners.add(owner);
  }

  public void putEntranceState(@NotNull GrControlFlowOwner owner, @NotNull TypeDfaState entranceState) {
    entranceStates.put(owner, entranceState);
  }

  public void putExitState(@NotNull GrControlFlowOwner owner, @NotNull TypeDfaState exitState) {
    exitStates.put(owner, exitState);
  }

  @Nullable
  public TypeDfaState getEntranceState(@NotNull GrControlFlowOwner owner) {
    return entranceStates.get(owner);
  }

  @Nullable
  public TypeDfaState getExitState(@NotNull GrControlFlowOwner owner) {
    return exitStates.get(owner);
  }

  public boolean isVisited(@Nullable GrControlFlowOwner block) {
    return visitedFlowOwners.contains(block);
  }
}
