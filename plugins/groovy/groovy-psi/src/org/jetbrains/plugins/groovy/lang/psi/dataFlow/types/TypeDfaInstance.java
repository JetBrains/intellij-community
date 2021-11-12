// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.*;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class TypeDfaInstance implements DfaInstance<TypeDfaState> {

  private final Instruction[] myFlow;
  private final DFAFlowInfo myFlowInfo;
  private final InferenceCache myCache;
  private final PsiManager myManager;
  private final InitialTypeProvider myTypeProvider;

  TypeDfaInstance(Instruction @NotNull [] flow,
                  @NotNull DFAFlowInfo flowInfo,
                  @NotNull InferenceCache cache,
                  @NotNull PsiManager manager,
                  @NotNull InitialTypeProvider typeProvider) {
    myFlow = flow;
    myManager = manager;
    myFlowInfo = flowInfo;
    myCache = cache;
    myTypeProvider = typeProvider;
  }

  @Override
  public void fun(@NotNull final TypeDfaState state, @NotNull final Instruction instruction) {
    if (instruction instanceof ReadWriteVariableInstruction) {
      handleReadWriteVariable(state, (ReadWriteVariableInstruction)instruction);
    }
    else if (instruction instanceof MixinTypeInstruction) {
      handleMixin(state, (MixinTypeInstruction)instruction);
    }
    else if (instruction instanceof ArgumentsInstruction) {
      handleArguments(state, (ArgumentsInstruction)instruction);
    }
    else if (instruction instanceof NegatingGotoInstruction) {
      handleNegation(state, (NegatingGotoInstruction)instruction);
    }
    else if (instruction instanceof FunctionalBlockBeginInstruction) {
      handleStartFunctionalExpression(state, (FunctionalBlockBeginInstruction)instruction);
    }
    else if (instruction instanceof FunctionalBlockEndInstruction) {
      handleFunctionalExpression(state, (FunctionalBlockEndInstruction)instruction);
    }
    myCache.publishDescriptor(state, instruction);
  }

  private void handleStartFunctionalExpression(TypeDfaState state, FunctionalBlockBeginInstruction instruction) {
    state.addClosureState(state);
  }

  private static boolean hasNoChanges(@NotNull TypeDfaState baseDfaState, @NotNull TypeDfaState newDfaState) {
    var oldMap = baseDfaState.getVarTypes();
    for (var entry : newDfaState.getVarTypes().entrySet()) {
      if (!oldMap.containsKey(entry.getKey()) || !oldMap.get(entry.getKey()).equals(entry.getValue())) {
        return false;
      }
    }
    return true;
  }

  private void handleFunctionalExpression(@NotNull TypeDfaState state, @NotNull FunctionalBlockEndInstruction instruction) {
    GrFunctionalExpression block = instruction.getStartNode().getElement();
    ClosureFrame currentClosureFrame = state.popTopClosureFrame();
    assert currentClosureFrame != null : "Encountered end of closure without closure start";
    List<VariableDescriptor> toRemove = new ArrayList<>();
    for (var newDescriptor : state.getVarTypes().keySet()) {
      if (newDescriptor instanceof ResolvedVariableDescriptor && ((ResolvedVariableDescriptor)newDescriptor).getVariable().getContext() == block) {
        toRemove.add(newDescriptor);
      }
    }
    for (var descr : toRemove) {
      state.getVarTypes().remove(descr);
    }
    if (hasNoChanges(currentClosureFrame.getStartState(), state)) {
      return;
    }
    // todo: more accurate handling of flushing type
    InvocationKind kind = FunctionalExpressionFlowUtil.getInvocationKind(block);
    if (kind.equals(InvocationKind.IN_PLACE_ONCE)) {
      return;
    }
    switch (kind) {
      case IN_PLACE_UNKNOWN:
        List<VariableDescriptor> descriptors = new ArrayList<>();
        for (var descriptor : state.getVarTypes().keySet()) {
          if (!currentClosureFrame.getStartState().containsVariable(descriptor)) {
            descriptors.add(descriptor);
          }
        }
        for (var descr : descriptors) {
          state.removeBinding(descr, myFlowInfo.getVarIndexes());
        }
        state.joinState(currentClosureFrame.getStartState(), myManager, myFlowInfo.getVarIndexes());
        break;
      case UNKNOWN:
        var reassignments = currentClosureFrame.getReassignments();
        TypeDfaState initialState = currentClosureFrame.getStartState();
        for (var newBinding : reassignments.entrySet()) {
          VariableDescriptor descriptor = newBinding.getKey();
          List<DFAType> assignments = newBinding.getValue();
          PsiType upperBoundByWrites =
            TypesUtil
              .getLeastUpperBound(assignments.stream().map(dfaType -> dfaType.getResultType(myManager)).filter(it -> it != null).toArray(PsiType[]::new),
                                  myManager);
          DFAType existingType = initialState.getVariableType(descriptor);
          if (existingType == null) existingType = DFAType.create(null);
          DFAType flushedType = existingType.addFlushingType(upperBoundByWrites, myManager);
          state.putType(descriptor, flushedType);
        }
        break;
    }
  }


  private void handleMixin(@NotNull final TypeDfaState state, @NotNull final MixinTypeInstruction instruction) {
    final VariableDescriptor descriptor = instruction.getVariableDescriptor();
    if (descriptor == null) return;

    updateVariableType(state, instruction, descriptor, () -> {
      ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow);
      if (originalInstr != null) {
        assert !originalInstr.isWrite();
      }

      DFAType original = state.getOrCreateVariableType(descriptor);
      original.addMixin(instruction.inferMixinType(), instruction.getConditionInstruction());
      return original;
    });
  }

  private void handleReadWriteVariable(@NotNull TypeDfaState state, @NotNull ReadWriteVariableInstruction instruction) {
    final PsiElement element = instruction.getElement();
    if (element == null) return;
    VariableDescriptor descriptor = instruction.getDescriptor();
    if (instruction.isWrite()) {
      updateVariableType(
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
    else if (myFlowInfo.getInterestingDescriptors().contains(descriptor)){
      DFAType type = state.getVariableType(descriptor);
      if (type == null) {
        DFAType thisInitialType = myTypeProvider.initialType(descriptor);
        if (thisInitialType != null) {
          // todo: implement as flushing type
          updateVariableType(state, instruction, descriptor, () -> thisInitialType);
        }
      }
    }
  }

  private void handleArguments(TypeDfaState state, ArgumentsInstruction instruction) {
    for (Map.Entry<VariableDescriptor, Collection<Argument>> entry : instruction.getArguments().entrySet()) {
      final VariableDescriptor descriptor = entry.getKey();
      final Collection<Argument> arguments = entry.getValue();
      handleArgument(state, instruction, descriptor, arguments);
    }
  }

  private void handleArgument(TypeDfaState state, ArgumentsInstruction instruction, VariableDescriptor descriptor, Collection<Argument> arguments) {
    updateVariableType(state, instruction, descriptor, () -> {
      final DFAType result = state.getOrCreateVariableType(descriptor);
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
                                  @NotNull VariableDescriptor descriptor,
                                  @NotNull Computable<DFAType> computation) {
    if (!myFlowInfo.getInterestingInstructions().contains(instruction)) {
      state.removeBinding(descriptor, myFlowInfo.getVarIndexes());
      return;
    }
    else {
      state.restoreBinding(descriptor,myFlowInfo.getVarIndexes());
    }

    DFAType type = myCache.getCachedInferredType(descriptor, instruction);
    if (type == null) {
      if (myFlowInfo.getAcyclicInstructions().contains(instruction) && !myFlowInfo.getDependentOnSharedVariables().contains(instruction)) {
        type = computation.compute();
      }
      else {
        type = TypeInferenceHelper.doInference(state.getBindings(), computation);
      }
    }

    DFAType existingDfaType = state.getVariableType(descriptor);
    if (existingDfaType != null) {
      type = type.addFlushingType(existingDfaType.getFlushingType(), myManager);
    }
    state.putType(descriptor, type);
  }

  private static void handleNegation(@NotNull TypeDfaState state, @NotNull NegatingGotoInstruction negation) {
    for (Map.Entry<VariableDescriptor, DFAType> entry : state.getVarTypes().entrySet()) {
      entry.setValue(entry.getValue().negate(negation));
    }
  }

  //private void handleFunctionalExpression(@NotNull TypeDfaState state, @NotNull Instruction instruction) {
  //  if (!FunctionalExpressionFlowUtil.isNestedFlowProcessingAllowed()) {
  //    return;
  //  }
  //  GrFunctionalExpression block = Objects.requireNonNull((GrFunctionalExpression)instruction.getElement());
  //  if (CompileStaticUtil.isCompileStatic(block)) {
  //    return;
  //  }
  //  GrControlFlowOwner blockFlowOwner = FunctionalExpressionFlowUtil.getControlFlowOwner(block);
  //  if (blockFlowOwner == null) {
  //    return;
  //  }
  //  if (!myFlowInfo.getInterestingInstructions().contains(instruction)) {
  //    ControlFlowUtils.getForeignVariableDescriptors(blockFlowOwner).forEach(descriptor -> state.removeBinding(descriptor, myFlowInfo.getVarIndexes()));
  //    return;
  //  }
  //  if (instruction.num() > lastInterestingInstructionIndex) {
  //    return;
  //  }
  //  ControlFlowUtils.getForeignVariableDescriptors(blockFlowOwner).forEach(descriptor1 -> state.restoreBinding(descriptor1, myFlowInfo.getVarIndexes()));
  //  InvocationKind kind = FunctionalExpressionFlowUtil.getInvocationKind(block);
  //  Map<VariableDescriptor, DFAType> initialTypes = state.getVarTypes();
  //  switch (kind) {
  //    case IN_PLACE_ONCE:
  //      handleClosureDFAResult(instruction, blockFlowOwner, initialTypes, state::putType);
  //      break;
  //    case IN_PLACE_UNKNOWN:
  //      handleClosureDFAResult(instruction, blockFlowOwner, initialTypes, (descriptor, dfaType) -> {
  //        DFAType existingType = state.getVariableType(descriptor);
  //        if (existingType != null) {
  //          DFAType mergedType = DFAType.create(dfaType, existingType, block.getManager());
  //          state.putType(descriptor, mergedType);
  //        }
  //      });
  //      break;
  //    case UNKNOWN:
  //      runWithCycleCheck(instruction, () -> {
  //        for (VariableDescriptor descriptor : myFlowInfo.getInterestingDescriptors()) {
  //          var initialTypesWithNullizedDescriptorType = new HashMap<VariableDescriptor, DFAType>(initialTypes);
  //          initialTypesWithNullizedDescriptorType.putIfAbsent(descriptor, DFAType.create(null));
  //          PsiType upperBoundByWrites = TypeDfaInstanceUtilKt.getLeastUpperBoundByAllWrites(blockFlowOwner, initialTypesWithNullizedDescriptorType, descriptor);
  //          if (upperBoundByWrites != PsiType.NULL) {
  //            DFAType existingType = state.getVariableType(descriptor);
  //            if (existingType == null) existingType = DFAType.create(null);
  //            DFAType flushedType = existingType.addFlushingType(upperBoundByWrites, myManager);
  //            state.putType(descriptor, flushedType);
  //          }
  //        }
  //        return null;
  //      });
  //  }
  //}

  //private void handleClosureDFAResult(@NotNull Instruction instruction,
  //                                    @NotNull GrControlFlowOwner block,
  //                                    @NotNull Map<VariableDescriptor, DFAType> initialTypes,
  //                                    @NotNull BiConsumer<? super VariableDescriptor, ? super DFAType> typeConsumer) {
  //  InferenceCache blockCache = TypeInferenceHelper.getInferenceCache(block);
  //  Instruction[] blockFlow = block.getControlFlow();
  //  Instruction lastBlockInstruction = blockFlow[blockFlow.length - 1];
  //  runWithCycleCheck(instruction, () -> {
  //    for (VariableDescriptor outerDescriptor : myFlowInfo.getInterestingDescriptors()) {
  //      PsiType descriptorType = blockCache.getInferredType(outerDescriptor, lastBlockInstruction, false, initialTypes);
  //      if (descriptorType != null) {
  //        typeConsumer.accept(outerDescriptor, DFAType.create(descriptorType));
  //      }
  //    }
  //    return null;
  //  });
  //}
}
