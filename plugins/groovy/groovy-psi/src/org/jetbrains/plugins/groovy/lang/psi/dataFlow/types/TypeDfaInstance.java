// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.MixinTypeInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;

import java.util.Collection;
import java.util.Map;
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
    else if (instruction instanceof ArgumentsInstruction) {
      handleArguments(state, (ArgumentsInstruction)instruction);
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

  private void handleArguments(TypeDfaState state, ArgumentsInstruction instruction) {
    for (Map.Entry<String, Collection<Argument>> entry : instruction.getArguments().entrySet()) {
      final String variableName = entry.getKey();
      final Collection<Argument> arguments = entry.getValue();
      handleArgument(state, instruction, variableName, arguments);
    }
  }

  private void handleArgument(TypeDfaState state, ArgumentsInstruction instruction, String variableName, Collection<Argument> arguments) {
    updateVariableType(state, instruction, variableName, () -> {
      final DFAType result = state.getOrCreateVariableType(variableName).negate(instruction);
      final GroovyResolveResult[] results = instruction.getElement().multiResolve(false);
      for (GroovyResolveResult variant : results) {
        if (!(variant instanceof GroovyMethodResult)) continue;

        GroovyMethodCandidate candidate = ((GroovyMethodResult)variant).getCandidate();
        if (candidate == null) continue;

        ArgumentMapping mapping = candidate.getArgumentMapping();
        if (mapping == null) continue;

        for (Argument argument : arguments) {
          PsiType parameterType = mapping.expectedType(argument);
          if (parameterType == null) continue;

          PsiType typeToMixin = variant.getSubstitutor().substitute(parameterType);
          result.addMixin(typeToMixin, null);
        }
      }
      return result;
    });
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
