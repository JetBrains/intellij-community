// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.FunctionalBlockBeginInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.FunctionalBlockEndInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

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
    TypeDfaState state = new TypeDfaState();
    for (VariableDescriptor descriptor : myFlowInfo.getInterestingDescriptors()) {
      PsiType initialType = myInitialTypeProvider.initialType(descriptor);
      if (initialType != null) {
        state = state.withNewType(descriptor, DFAType.create(initialType), myFlowInfo.getVarIndexes());
      }
    }
    return state;
  }

  private static TypeDfaState handleStartFunctionalExpression(TypeDfaState state) {
    return state.withNewClosureState(state);
  }

  //private static boolean hasNoChanges(@NotNull TypeDfaState baseDfaState, @NotNull TypeDfaState newDfaState) {
  //  var oldMap = baseDfaState.getRawVarTypes();
  //  for (var entry : newDfaState.getRawVarTypes().entrySet()) {
  //    if (!oldMap.containsKey(entry.getKey()) || !oldMap.get(entry.getKey()).equals(entry.getValue())) {
  //      return false;
  //    }
  //  }
  //  return true;
  //}

  private TypeDfaState handleFunctionalExpression(@NotNull TypeDfaState state, @NotNull FunctionalBlockEndInstruction instruction) {
    //GrFunctionalExpression block = instruction.getStartNode().getElement();
    //ClosureFrame currentClosureFrame = state.popTopClosureFrame();
    //assert currentClosureFrame != null : "Encountered end of closure without closure start";
    //List<VariableDescriptor> toRemove = new ArrayList<>();
    //Map<VariableDescriptor, DFAType> stateTypes = new HashMap<>(state.getRawVarTypes());
    //for (var newDescriptor : stateTypes.keySet()) {
    //  if (newDescriptor instanceof ResolvedVariableDescriptor && ((ResolvedVariableDescriptor)newDescriptor).getVariable().getContext() == block) {
    //    toRemove.add(newDescriptor);
    //  }
    //}
    //for (var descr : toRemove) {
    //  stateTypes.remove(descr);
    //}
    ////if (hasNoChanges(currentClosureFrame.getStartState(), stateTypes)) {
    ////  return state;
    ////}
    //InvocationKind kind = FunctionalExpressionFlowUtil.getInvocationKind(block);
    //if (kind.equals(InvocationKind.IN_PLACE_ONCE)) {
    //  // plain inlining
    //  return state.withNewMap(stateTypes);
    //}
    //switch (kind) {
    //  case IN_PLACE_UNKNOWN:
    //    List<VariableDescriptor> localDescriptors = new ArrayList<>();
    //    for (var descriptor : state.getRawVarTypes().keySet()) {
    //      if (!currentClosureFrame.getStartState().containsVariable(descriptor)) {
    //        localDescriptors.add(descriptor);
    //      }
    //    }
    //    for (var descr : localDescriptors) {
    //      state.getRawVarTypes().remove(descr);
    //    }
    //    state.joinState(currentClosureFrame.getStartState(), myManager, myFlowInfo.getVarIndexes());
    //    break;
    //  case UNKNOWN:
    //    var reassignments = currentClosureFrame.getReassignments();
    //    TypeDfaState initialState = currentClosureFrame.getStartState();
    //    for (var newBinding : reassignments.entrySet()) {
    //      VariableDescriptor descriptor = newBinding.getKey();
    //      List<DFAType> assignments = newBinding.getValue();
    //      PsiType upperBoundByWrites =
    //        TypesUtil.getLeastUpperBound(assignments.stream().map(dfaType -> dfaType.getResultType(myManager)).filter(it -> it != null).toArray(PsiType[]::new), myManager);
    //      DFAType existingType = initialState.getVariableType(descriptor, myFlowInfo.getVarIndexes());
    //      if (existingType == null) existingType = DFAType.create(null);
    //      DFAType flushedType = existingType.addFlushingType(upperBoundByWrites, myManager);
    //      state = state.withNewType(descriptor, flushedType, myFlowInfo.getVarIndexes());
    //    }
    //    break;
    //}
    return state;
  }


  private TypeDfaState handleMixin(@NotNull final TypeDfaState state, @NotNull final MixinTypeInstruction instruction) {
    final VariableDescriptor descriptor = instruction.getVariableDescriptor();
    if (descriptor == null) return state;

    return updateVariableType(state, instruction, descriptor, () -> {
      ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow);
      if (originalInstr != null) {
        assert !originalInstr.isWrite();
      }

      DFAType original = state.getOrCreateVariableType(descriptor, myFlowInfo.getVarIndexes());
      original.addMixin(instruction.inferMixinType(), instruction.getConditionInstruction());
      return original;
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
      final DFAType result = state.getOrCreateVariableType(descriptor, myFlowInfo.getVarIndexes());
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

  private TypeDfaState updateVariableType(@NotNull TypeDfaState state,
                                          @NotNull Instruction instruction,
                                          @NotNull VariableDescriptor descriptor,
                                          @NotNull Computable<DFAType> computation) {
    if (!myFlowInfo.getInterestingInstructions().contains(instruction)) {
      return state.withRemovedBinding(descriptor, myFlowInfo.getVarIndexes());
    }

    DFAType type = myCache.getCachedInferredType(descriptor, instruction);
    if (type == null) {
      if (myFlowInfo.getAcyclicInstructions().contains(instruction)) {
        type = computation.compute();
      }
      else {
        // todo bad, don't use raw types here
        type = TypeInferenceHelper.doInference(new HashMap<>(state.getRawVarTypes()), computation);
      }
    }

    DFAType existingDfaType = state.getVariableType(descriptor, myFlowInfo.getVarIndexes());
    if (existingDfaType != null) {
      type = type.addFlushingType(existingDfaType.getFlushingType(), myManager);
    }
    return state.withNewType(descriptor, type, myFlowInfo.getVarIndexes());
  }

  private TypeDfaState handleNegation(@NotNull TypeDfaState state, @NotNull NegatingGotoInstruction negation) {
    TypeDfaState newState = state;
    for (Map.Entry<VariableDescriptor, DFAType> entry : state.getRawVarTypes().entrySet()) {
      newState = newState.withNewType(entry.getKey(), entry.getValue().negate(negation), myFlowInfo.getVarIndexes());
    }
    return newState;
  }
}
