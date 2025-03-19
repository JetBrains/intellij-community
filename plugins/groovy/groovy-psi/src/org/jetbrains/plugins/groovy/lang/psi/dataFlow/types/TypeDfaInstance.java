// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

class TypeDfaInstance implements DfaInstance<TypeDfaState> {

  private final GroovyControlFlow myFlow;
  private final DFAFlowInfo myFlowInfo;
  private final InferenceCache myCache;
  private final PsiManager myManager;
  private final InitialTypeProvider myInitialTypeProvider;

  TypeDfaInstance(@NotNull GroovyControlFlow flow,
                  @NotNull DFAFlowInfo flowInfo,
                  @NotNull InferenceCache cache,
                  @NotNull PsiManager manager,
                  @NotNull InitialTypeProvider typeProvider) {
    myFlow = flow;
    myManager = manager;
    myFlowInfo = flowInfo;
    myCache = cache;
    myInitialTypeProvider = typeProvider;
  }

  @Override
  public TypeDfaState fun(final @NotNull TypeDfaState state, final @NotNull Instruction instruction) {
    TypeDfaState newState;
    if (instruction.num() == 0) {
      newState = handleStartInstruction();
    }
    else if (instruction instanceof ReadWriteVariableInstruction) {
      newState = handleReadWriteVariable(state, (ReadWriteVariableInstruction)instruction);
    }
    else if (instruction instanceof MixinTypeInstruction) {
      newState = handleMixin(state, (MixinTypeInstruction)instruction);
    }
    else if (instruction instanceof ArgumentsInstruction) {
      newState = handleArguments(state, (ArgumentsInstruction)instruction);
    }
    else if (instruction instanceof NegatingGotoInstruction) {
      newState = handleNegation(state, (NegatingGotoInstruction)instruction);
    }
    else {
      newState = state;
    }
    myCache.publishDescriptor(newState, instruction);
    return newState;
  }

  private TypeDfaState handleStartInstruction() {
    TypeDfaState state = TypeDfaState.EMPTY_STATE;
    for (int descriptor : myFlowInfo.getInterestingDescriptors()) {
      PsiType initialType = myInitialTypeProvider.initialType(descriptor);
      if (initialType != null) {
        state = state.withNewType(descriptor, DFAType.create(initialType));
      }
    }
    return state;
  }

  private static boolean hasNoChanges(@NotNull TypeDfaState baseDfaState, @NotNull Int2ObjectMap<DFAType> newDfaTypes) {
    var oldMap = baseDfaState.getRawVarTypes();
    for (var entry : newDfaTypes.int2ObjectEntrySet()) {
      if (!oldMap.containsKey(entry.getIntKey()) || !oldMap.get(entry.getIntKey()).equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private TypeDfaState handleMixin(final @NotNull TypeDfaState state, final @NotNull MixinTypeInstruction instruction) {
    final int descriptor = instruction.getVariableDescriptor();
    if (descriptor == 0) return state;

    return updateVariableType(state, instruction, descriptor, () -> {
      ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow.getFlow());
      if (originalInstr != null) {
        assert !originalInstr.isWrite();
      }

      return state.getNotNullDFAType(descriptor).withNewMixin(instruction.inferMixinType(), instruction.getConditionInstruction());
    });
  }

  private TypeDfaState handleReadWriteVariable(@NotNull TypeDfaState state, @NotNull ReadWriteVariableInstruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null) return state;
    int descriptor = instruction.getDescriptor();
    if (instruction.isWrite()) {
      return updateVariableType(
        state, instruction, descriptor,
        () -> {
          PsiType initializerType = TypeInferenceHelper.getInitializerType(element);
          VariableDescriptor actualDescriptor = myFlow.getVarIndices()[descriptor];
          if (initializerType == null &&
              actualDescriptor instanceof ResolvedVariableDescriptor &&
              TypeInferenceHelper.isSimpleEnoughForAugmenting(myFlow.getFlow())) {
            GrVariable variable = ((ResolvedVariableDescriptor)actualDescriptor).getVariable();
            PsiType augmentedType = TypeAugmenter.Companion.inferAugmentedType(variable);
            return DFAType.create(augmentedType);
          }
          else {
            return DFAType.create(initializerType);
          }
        }
      );
    }
    else {
      return state;
    }
  }

  private TypeDfaState handleArguments(TypeDfaState state, ArgumentsInstruction instruction) {
    TypeDfaState newState = state;
    for (Map.Entry<Integer, Collection<Argument>> entry : instruction.getArguments().entrySet()) {
      final int descriptor = entry.getKey();
      final Collection<Argument> arguments = entry.getValue();
      newState = handleArgument(newState, instruction, descriptor, arguments);
    }
    return newState;
  }

  private TypeDfaState handleArgument(TypeDfaState state, ArgumentsInstruction instruction, int descriptorId, Collection<Argument> arguments) {
    return updateVariableType(state, instruction, descriptorId, () -> {
      DFAType result = state.getNotNullDFAType(descriptorId);
      final GroovyResolveResult[] results = instruction.getElement().multiResolve(false);
      for (GroovyResolveResult variant : results) {
        if (!(variant instanceof GroovyMethodResult)) continue;

        GroovyMethodCandidate candidate = ((GroovyMethodResult)variant).getCandidate();
        if (candidate == null) continue;

        ArgumentMapping<PsiCallParameter> mapping = candidate.getArgumentMapping();
        if (mapping == null) continue;

        for (Argument argument : arguments) {
          PsiType parameterType = mapping.expectedType(argument);
          if (parameterType == null) continue;

          PsiType typeToMixin = variant.getSubstitutor().substitute(parameterType);
          result = result.withNewMixin(typeToMixin, null);
        }
      }
      return result;
    });
  }

  private TypeDfaState updateVariableType(@NotNull TypeDfaState state,
                                          @NotNull Instruction instruction,
                                          int descriptorId,
                                          @NotNull Supplier<DFAType> computation) {
    if (descriptorId == 0) {
      return state;
    }
    if (!myFlowInfo.getInterestingInstructions().contains(instruction)) {
      return state.withRemovedBinding(descriptorId);
    }

    DFAType type = myCache.getCachedInferredType(descriptorId, instruction);
    if (type == null) {
      if (myFlowInfo.getAcyclicInstructions().contains(instruction)) {
        type = computation.get();
      }
      else {
        type = runWithoutCaching(state, computation);
      }
    }

    return state.withNewType(descriptorId, type);
  }

  private <T> T runWithoutCaching(@NotNull TypeDfaState state, Supplier<? extends T> computation) {
    Map<VariableDescriptor, DFAType> unwrappedVariables = getCurrentVariableTypes(state);
    return TypeInferenceHelper.doInference(unwrappedVariables, computation);
  }

  private @NotNull Map<VariableDescriptor, DFAType> getCurrentVariableTypes(@NotNull TypeDfaState state) {
    Map<VariableDescriptor, DFAType> unwrappedVariables = new HashMap<>();
    for (var entry : state.getRawVarTypes().int2ObjectEntrySet()) {
      if (!state.isProhibited(entry.getIntKey())) {
        unwrappedVariables.put(myFlow.getVarIndices()[entry.getIntKey()], entry.getValue());
      }
    }
    return unwrappedVariables;
  }

  private static TypeDfaState handleNegation(@NotNull TypeDfaState state, @NotNull NegatingGotoInstruction negation) {
    TypeDfaState newState = state;
    for (Int2ObjectMap.Entry<DFAType> entry : state.getRawVarTypes().int2ObjectEntrySet()) {
      DFAType before = entry.getValue();
      DFAType after = before.withNegated(negation);
      if (before != after) {
        newState = newState.withNewType(entry.getIntKey(), after);
      }
    }
    return newState;
  }
}
