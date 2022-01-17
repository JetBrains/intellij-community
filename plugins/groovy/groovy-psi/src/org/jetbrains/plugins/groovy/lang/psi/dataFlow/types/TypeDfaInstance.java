// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

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
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;
import org.jetbrains.plugins.groovy.lang.resolve.api.PsiCallParameter;

import java.util.*;
import java.util.function.Supplier;

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
      newState = handleStartFunctionalExpression(state, instruction);
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
        state = state.withNewType(index, DFAType.create(initialType));
      }
    }
    return state;
  }

  private static TypeDfaState handleStartFunctionalExpression(TypeDfaState state,
                                                              @NotNull Instruction instruction) {
    return state.withNewClosureState(new ClosureFrame(state, instruction.num()));
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
    if (currentClosureFrame.getStartInstructionState() == state || hasNoChanges(currentClosureFrame.getStartInstructionState(), state.getRawVarTypes())) {
      return currentClosureFrame.getStartInstructionState().withRemovedBindings(state.getRemovedBindings());
    }
    InvocationKind kind = getInvocationKind(block, state, instruction);
    TypeDfaState newState = state.withoutTopClosureState();
    switch (kind) {
      case IN_PLACE_ONCE:
        return newState;
      case UNKNOWN:
        // todo: separate handling for UNKNOWN
      case IN_PLACE_UNKNOWN:
        List<Integer> toRemove = new ArrayList<>();
        DefinitionMap definitionMap = myCache.getDefinitionMaps(currentClosureFrame.getStartInstructionNumber());
        for (int newDescriptorId : state.getRawVarTypes().keySet()) {
          if (definitionMap.getDefinitions(newDescriptorId) == null) {
            // local variable, should be removed
            toRemove.add(newDescriptorId);
          }
        }
        Int2ObjectMap<DFAType> stateTypes = toRemove.isEmpty() ? state.getRawVarTypes() : new Int2ObjectOpenHashMap<>(state.getRawVarTypes());
        for (int descr : toRemove) {
          stateTypes.remove(descr);
        }
        return TypeDfaState.merge(newState.withNewMap(stateTypes), currentClosureFrame.getStartInstructionState(), myManager);
    }
    return newState;
  }

  @NotNull
  private InvocationKind getInvocationKind(GrFunctionalExpression block,
                                           @NotNull TypeDfaState state,
                                           @NotNull FunctionalBlockEndInstruction instruction) {
    if (myFlowInfo.getAcyclicInstructions().contains(instruction)) {
      return FunctionalExpressionFlowUtil.getInvocationKind(block, true);
    }
    else {
      return runWithoutCaching(state, () -> FunctionalExpressionFlowUtil.getInvocationKind(block, false));
    }
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
                                          @NotNull VariableDescriptor descriptor,
                                          @NotNull Supplier<DFAType> computation) {
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
        type = computation.get();
      }
      else {
        type = runWithoutCaching(state, computation);
      }
    }

    return state.withNewType(index, type);
  }

  private <T> T runWithoutCaching(@NotNull TypeDfaState state, Supplier<T> computation) {
    Map<VariableDescriptor, DFAType> unwrappedVariables = getCurrentVariableTypes(state);
    return TypeInferenceHelper.doInference(unwrappedVariables, computation);
  }

  @NotNull
  private Map<VariableDescriptor, DFAType> getCurrentVariableTypes(@NotNull TypeDfaState state) {
    Map<VariableDescriptor, DFAType> unwrappedVariables = new HashMap<>();
    for (var entry : state.getRawVarTypes().int2ObjectEntrySet()) {
      if (!state.isProhibited(entry.getIntKey())) {
        unwrappedVariables.put(myFlowInfo.getReverseVarIndexes()[entry.getIntKey()], entry.getValue());
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
