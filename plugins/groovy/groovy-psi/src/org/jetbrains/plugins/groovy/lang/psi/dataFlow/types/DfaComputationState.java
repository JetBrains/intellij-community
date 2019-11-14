// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;

import java.util.HashMap;
import java.util.Map;

class DfaComputationState {
  private VariableDescriptor myVariableDescriptor = null;
  private final Map<GrControlFlowOwner, TypeDfaState> entranceStates = new HashMap<>();
  private int computationDepth = 0;

  @Nullable
  TypeDfaState getState(GrControlFlowOwner block) {
    return entranceStates.get(block);
  }

  @Nullable
  VariableDescriptor getVariableDescriptor() {
    return myVariableDescriptor;
  }

  void initializeDfaPhase(@NotNull VariableDescriptor variableDescriptor) {
    computationDepth++;
    this.myVariableDescriptor = variableDescriptor;
  }

  void setState(GrControlFlowOwner block, @NotNull TypeDfaState state) {
    this.entranceStates.put(block, state);
  }

  @Nullable
  PsiType getLastVariableType(GrControlFlowOwner owner, @NotNull VariableDescriptor descriptor) {
    TypeDfaState ownerState = entranceStates.get(owner);
    if (ownerState == null) {
      return null;
    }
    DFAType dfaType = ownerState.getVariableType(descriptor);
    if (dfaType == null) {
      return null;
    }
    return dfaType.getResultType();
  }

  void terminateDfaPhase() {
    computationDepth--;
    if (computationDepth == 0) {
      myVariableDescriptor = null;
    }
  }
}
