// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;

import java.util.Set;

class TypeDfaInstance implements DfaInstance<TypeDfaState> {

  private final Instruction[] myFlow;
  private final Set<Instruction> myInteresting;
  private final Set<Instruction> myAcyclicInstructions;
  private final InferenceCache myCache;

  TypeDfaInstance(@NotNull Instruction[] flow, @NotNull Couple<Set<Instruction>> interesting, @NotNull InferenceCache cache) {
    myFlow = flow;
    myInteresting = interesting.first;
    myAcyclicInstructions = interesting.second;
    myCache = cache;
  }

  @Override
  public void fun(@NotNull final TypeDfaState state, @NotNull final Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction) {
      handleVariableWrite(state, (ReadWriteVariableInstruction)instruction);
    }
    else if (instruction instanceof MixinTypeInstruction) {
      handleMixin(state, (MixinTypeInstruction)instruction);
    }
  }

  private void handleMixin(@NotNull final TypeDfaState state, @NotNull final MixinTypeInstruction instruction) {
    final String varName = instruction.getVariableName();
    if (varName == null) return;

    updateVariableType(state, instruction, varName, () -> {
      ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow);
      assert originalInstr != null && !originalInstr.isWrite();

      DFAType original = state.getVariableType(varName);
      if (original == null) {
        original = DFAType.create(null);
      }
      original = original.negate(originalInstr);
      original.addMixin(instruction.inferMixinType(), instruction.getConditionInstruction());
      return original;
    });
  }

  private void handleVariableWrite(TypeDfaState state, ReadWriteVariableInstruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element != null && instruction.isWrite()) {
      updateVariableType(
        state, instruction, instruction.getVariableName(),
        () -> DFAType.create(TypeInferenceHelper.getInitializerType(element))
      );
    }
  }

  private void updateVariableType(@NotNull TypeDfaState state,
                                  @NotNull Instruction instruction,
                                  @NotNull String variableName,
                                  @NotNull Computable<? extends DFAType> computation) {
    if (!myInteresting.contains(instruction)) {
      state.removeBinding(variableName);
      return;
    }

    DFAType type = myCache.getCachedInferredType(variableName, instruction);
    if (type == null) {
      if (myAcyclicInstructions.contains(instruction)) {
        type = computation.compute();
      }
      else {
        type = TypeInferenceHelper.doInference(state.getBindings(instruction), computation);
      }
    }
    state.putType(variableName, type);
  }

  @Override
  @NotNull
  public TypeDfaState initial() {
    return new TypeDfaState();
  }
}
