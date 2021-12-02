// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;

import java.util.*;

class TypeDfaInstance implements DfaInstance<TypeDfaState> {

  private final Instruction[] myFlow;
  private final DFAFlowInfo myFlowInfo;
  private final InferenceCache myCache;
  private final PsiManager myManager;
  private final InitialTypeProvider myInitialTypeProvider;

  TypeDfaInstance(Instruction @NotNull [] flow,
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
  public TypeDfaState fun(@NotNull final TypeDfaState state, @NotNull final Instruction instruction) {
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
    else if (instruction instanceof FunctionalBlockBeginInstruction) {
      newState = handleStartFunctionalExpression(state);
    }
    else if (instruction instanceof FunctionalBlockEndInstruction) {
      newState = handleFunctionalExpression(state, (FunctionalBlockEndInstruction)instruction);
    }
    else {
      newState = state;
    }
    myCache.publishDescriptor(newState, instruction);
    return newState;
  }

  private TypeDfaState handleStartInstruction() {
    TypeDfaState state = TypeDfaState.EMPTY_STATE;
    for (VariableDescriptor descriptor : myFlowInfo.getInterestingDescriptors()) {
      PsiType initialType = myInitialTypeProvider.initialType(descriptor);
      if (initialType != null) {
        int index = myFlowInfo.getVarIndexes().get(descriptor);
        state = state.withNewType(index, DFAType.create(initialType, PsiType.NULL));
      }
    }
    return state;
  }

  private static TypeDfaState handleStartFunctionalExpression(TypeDfaState state) {
    return state.withNewClosureState(new ClosureFrame(state));
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

  private TypeDfaState handleFunctionalExpression(@NotNull TypeDfaState state, @NotNull FunctionalBlockEndInstruction instruction) {
    GrFunctionalExpression block = instruction.getStartNode().getElement();
    ClosureFrame currentClosureFrame = state.getTopClosureFrame();
    assert currentClosureFrame != null : "Encountered end of closure without closure start";
    List<Integer> toRemove = new ArrayList<>();
    Int2ObjectMap<DFAType> stateTypes = new Int2ObjectOpenHashMap<>(state.getRawVarTypes());
    for (int newDescriptorId : stateTypes.keySet()) {
      VariableDescriptor newDescriptor = myFlowInfo.getReverseVarIndexes()[newDescriptorId];
      if (newDescriptor instanceof ResolvedVariableDescriptor && ((ResolvedVariableDescriptor)newDescriptor).getVariable().getContext() == block) {
        toRemove.add(newDescriptorId);
      }
    }
    for (int descr : toRemove) {
      stateTypes.remove(descr);
    }
    if (currentClosureFrame.getStartState() == state || hasNoChanges(currentClosureFrame.getStartState(), stateTypes)) {
      return currentClosureFrame.getStartState();
    }
    InvocationKind kind = FunctionalExpressionFlowUtil.getInvocationKind(block);
    TypeDfaState newState = state.withNewMap(stateTypes).withoutTopClosureState();
    switch (kind) {
      case IN_PLACE_ONCE:
        return newState;
      case UNKNOWN:
        // todo: separate handling for UNKNOWN
      case IN_PLACE_UNKNOWN:
        List<Integer> localDescriptors = new ArrayList<>();
        for (int descriptor : stateTypes.keySet()) {
          if (!currentClosureFrame.getStartState().containsVariable(descriptor)) {
            localDescriptors.add(descriptor);
          }
        }
        for (int descr : localDescriptors) {
          stateTypes.remove(descr);
        }
        return TypeDfaState.merge(newState, currentClosureFrame.getStartState(), myManager);
    }
    return newState;
  }

  private TypeDfaState handleMixin(@NotNull final TypeDfaState state, @NotNull final MixinTypeInstruction instruction) {
    final VariableDescriptor descriptor = instruction.getVariableDescriptor();
    if (descriptor == null) return state;

    return updateVariableType(state, instruction, descriptor, () -> {
      ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow);
      if (originalInstr != null) {
        assert !originalInstr.isWrite();
      }

      return state.getNotNullDFAType(descriptor, myFlowInfo.getVarIndexes()).withNewMixin(instruction.inferMixinType(), instruction.getConditionInstruction());
    });
  }

  private TypeDfaState handleReadWriteVariable(@NotNull TypeDfaState state, @NotNull ReadWriteVariableInstruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null) return state;
    VariableDescriptor descriptor = instruction.getDescriptor();
    if (instruction.isWrite()) {
      return updateVariableType(
        state, instruction, descriptor,
        () -> {
          PsiType initializerType = TypeInferenceHelper.getInitializerType(element);
          if (initializerType == null &&
              descriptor instanceof ResolvedVariableDescriptor &&
              TypeInferenceHelper.isSimpleEnoughForAugmenting(myFlow)) {
            GrVariable variable = ((ResolvedVariableDescriptor)descriptor).getVariable();
            PsiType augmentedType = TypeAugmenter.Companion.inferAugmentedType(variable);
            return DFAType.create(augmentedType, PsiType.NULL);
          }
          else {
            return DFAType.create(initializerType, PsiType.NULL);
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
    for (Map.Entry<VariableDescriptor, Collection<Argument>> entry : instruction.getArguments().entrySet()) {
      final VariableDescriptor descriptor = entry.getKey();
      final Collection<Argument> arguments = entry.getValue();
      newState = handleArgument(newState, instruction, descriptor, arguments);
    }
    return newState;
  }

  private TypeDfaState handleArgument(TypeDfaState state, ArgumentsInstruction instruction, VariableDescriptor descriptor, Collection<Argument> arguments) {
    return updateVariableType(state, instruction, descriptor, () -> {
      DFAType result = state.getNotNullDFAType(descriptor, myFlowInfo.getVarIndexes());
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
          result = result.withNewMixin(typeToMixin, null);
        }
      }
      return result;
    });
  }

  private TypeDfaState updateVariableType(@NotNull TypeDfaState state,
                                          @NotNull Instruction instruction,
                                          @NotNull VariableDescriptor descriptor,
                                          @NotNull Computable<DFAType> computation) {
    int index = ((Object2IntMap<VariableDescriptor>)myFlowInfo.getVarIndexes()).getInt(descriptor);
    if (index == 0) {
      return state;
    }
    if (!myFlowInfo.getInterestingInstructions().contains(instruction)) {
      return state.withRemovedBinding(index);
    }

    DFAType type = myCache.getCachedInferredType(descriptor, instruction);
    if (type == null) {
      if (myFlowInfo.getAcyclicInstructions().contains(instruction)) {
        type = computation.compute();
      }
      else {
        Map<VariableDescriptor, DFAType> unwrappedVariables = new HashMap<>();
        for (var entry : state.getRawVarTypes().int2ObjectEntrySet()) {
          if (!state.isProhibited(entry.getIntKey())) {
            unwrappedVariables.put(myFlowInfo.getReverseVarIndexes()[entry.getIntKey()], entry.getValue());
          }
        }
        type = TypeInferenceHelper.doInference(unwrappedVariables, computation);
      }
    }

    DFAType existingDfaType = state.getVariableType(descriptor, myFlowInfo.getVarIndexes());
    if (existingDfaType != null) {
      type = type.withFlushingType(existingDfaType.getFlushingType(), myManager);
    }
    return state.withNewType(index, type);
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
