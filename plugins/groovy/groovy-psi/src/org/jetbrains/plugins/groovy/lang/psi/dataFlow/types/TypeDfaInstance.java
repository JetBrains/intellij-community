// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;

class TypeDfaInstance implements DfaInstance<TypeDfaState> {

  private final Instruction[] myFlow;
  private final DFAFlowInfo myFlowInfo;
  private final InferenceCache myCache;
  private final PsiManager myManager;
  private final int lastInterestingInstructionIndex;

  TypeDfaInstance(Instruction @NotNull [] flow,
                  @NotNull DFAFlowInfo flowInfo,
                  @NotNull InferenceCache cache,
                  @NotNull PsiManager manager) {
    myFlow = flow;
    myManager = manager;
    myFlowInfo = flowInfo;
    myCache = cache;
    lastInterestingInstructionIndex = flowInfo.getInterestingInstructions().stream().mapToInt(Instruction::num).max().orElse(0);
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
  }

  private void handleStartFunctionalExpression(TypeDfaState state, FunctionalBlockBeginInstruction instruction) {
    state.addClosureState(state);
  }

  private void handleFunctionalExpression(@NotNull TypeDfaState state, @NotNull FunctionalBlockEndInstruction instruction) {
    GrFunctionalExpression block = instruction.getElement();
    InvocationKind kind = FunctionalExpressionFlowUtil.getInvocationKind(block);
    switch (kind) {
      case IN_PLACE_ONCE:
        break;
      case IN_PLACE_UNKNOWN:
        TypeDfaState previousClosureState = state.getTopClosureState();
        assert previousClosureState != null : "Encountered end of closure without closure start";
        for (var newDescriptor : state.getVarTypes().keySet()) {
          if (!previousClosureState.containsVariable(newDescriptor)) {
            state.removeBinding(newDescriptor);
          }
        }
        state.joinState(previousClosureState, myManager);
        state.popClosureState();
        break;
      case UNKNOWN:
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
        type = TypeInferenceHelper.doInference(state.getBindings(), true, computation);
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

  private void handleClosureDFAResult(@NotNull Instruction instruction,
                                      @NotNull GrControlFlowOwner block,
                                      @NotNull Map<VariableDescriptor, DFAType> initialTypes,
                                      @NotNull BiConsumer<? super VariableDescriptor, ? super DFAType> typeConsumer) {
    InferenceCache blockCache = TypeInferenceHelper.getInferenceCache(block);
    Instruction[] blockFlow = block.getControlFlow();
    Instruction lastBlockInstruction = blockFlow[blockFlow.length - 1];
    runWithCycleCheck(instruction, () -> {
      for (VariableDescriptor outerDescriptor : myFlowInfo.getInterestingDescriptors()) {
        PsiType descriptorType = blockCache.getInferredType(outerDescriptor, lastBlockInstruction, false, initialTypes);
        if (descriptorType != null) {
          typeConsumer.accept(outerDescriptor, DFAType.create(descriptorType));
        }
      }
      return null;
    });
  }

  private void runWithCycleCheck(@NotNull Instruction instruction, @NotNull Computable<?> action) {
    if (myFlowInfo.getAcyclicInstructions().contains(instruction)) {
      action.get();
    }
    else {
      TypeInferenceHelper.doInference(Collections.emptyMap(), false, action);
    }
  }
}
