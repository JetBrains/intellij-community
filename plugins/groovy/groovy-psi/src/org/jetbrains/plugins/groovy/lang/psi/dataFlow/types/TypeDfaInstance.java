// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.*;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ArgumentsInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ClosureFlowUtil;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InvocationKind;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAType;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.api.ArgumentMapping;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCandidate;

import java.util.*;
import java.util.function.BiConsumer;

class TypeDfaInstance implements DfaInstance<TypeDfaState> {

  private final Instruction[] myFlow;
  private final GrControlFlowOwner myOwner;
  private final Set<Instruction> myInteresting;
  private final Set<Instruction> myAcyclicInstructions;
  private final InferenceCache myCache;
  private final InitialTypeProvider myInitialTypeProvider;
  private final DfaComputationState myDfaComputationState;

  TypeDfaInstance(Instruction @NotNull [] flow,
                  @NotNull Couple<Set<Instruction>> interesting,
                  @NotNull InferenceCache cache,
                  @NotNull InitialTypeProvider initialTypeProvider,
                  @NotNull GrControlFlowOwner owner,
                  @NotNull DfaComputationState state) {
    myFlow = flow;
    myOwner = owner;
    myInteresting = interesting.first;
    myAcyclicInstructions = interesting.second;
    myCache = cache;
    myInitialTypeProvider = initialTypeProvider;
    myDfaComputationState = state;
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
    else if (instruction.getElement() instanceof GrClosableBlock) {
      handleClosureBlock(state, instruction);
    }
    else if (instruction.getElement() == null) {
      handleNullInstruction(state, instruction);
    }
  }

  private void handleNullInstruction(@NotNull TypeDfaState state, @NotNull Instruction instruction) {
    if (instruction.num() == 0) {
      TypeDfaState currentEntranceState = myDfaComputationState.getEntranceState(myOwner);
      if (currentEntranceState == null) {
        return;
      }
      currentEntranceState.getVarTypes().forEach(state::putType);
    }
    else if (instruction.num() == myFlow.length - 1) {
      myDfaComputationState.putExitState(myOwner, state);
    }
  }

  private void handleMixin(@NotNull final TypeDfaState state, @NotNull final MixinTypeInstruction instruction) {
    final VariableDescriptor descriptor = instruction.getVariableDescriptor();
    if (descriptor == null) return;

    updateVariableType(state, instruction, descriptor, () -> {
      ReadWriteVariableInstruction originalInstr = instruction.getInstructionToMixin(myFlow);
      assert originalInstr != null && !originalInstr.isWrite();

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
          if (initializerType == null && descriptor instanceof ResolvedVariableDescriptor) {
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
      DFAType type = state.getVariableType(descriptor);
      if (type == null && myInteresting.contains(instruction)) {
        PsiType initialType = myInitialTypeProvider.initialType(descriptor, myDfaComputationState);
        if (initialType != null) {
          updateVariableType(state, instruction, descriptor, () -> DFAType.create(initialType));
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
                                  @NotNull Computable<? extends DFAType> computation) {
    if (!myInteresting.contains(instruction)) {
      state.removeBinding(descriptor);
      return;
    }

    DFAType type = myCache.getCachedInferredType(descriptor, instruction);
    if (type == null) {
      if (myAcyclicInstructions.contains(instruction)) {
        type = computation.compute();
      }
      else {
        type = TypeInferenceHelper.doInference(state.getBindings(), computation);
      }
    }
    state.putType(descriptor, type);
  }

  private static void handleNegation(@NotNull TypeDfaState state, @NotNull NegatingGotoInstruction negation) {
    for (Map.Entry<VariableDescriptor, DFAType> entry : state.getVarTypes().entrySet()) {
      entry.setValue(entry.getValue().negate(negation));
    }
  }

  private void handleClosureBlock(@NotNull TypeDfaState state, @NotNull Instruction instruction) {
    GrClosableBlock element = Objects.requireNonNull((GrClosableBlock)instruction.getElement());
    if (Arrays.stream(element.getControlFlow()).filter(it -> it instanceof ReadWriteVariableInstruction)
      .noneMatch(it -> ((ReadWriteVariableInstruction)it).getDescriptor().getName().equals(myDfaComputationState.getTargetDescriptor().getName()))) {
      return;
    }
    InvocationKind kind = ClosureFlowUtil.getInvocationKind(element);
    switch (kind) {
      case EXACTLY_ONCE:
        handleClosureDFAResult(state, instruction, state::putType);
        break;
      case INVOKED_INPLACE:
        handleClosureDFAResult(state, instruction, (descriptor, dfaType) -> {
          DFAType existingType = state.getVariableType(descriptor);
          if (existingType != null) {
            DFAType mergedType = DFAType.create(dfaType, existingType, element.getManager());
            state.putType(descriptor, mergedType);
          }
        });
        break;
      case UNKNOWN:
        break;
    }
  }

  private void handleClosureDFAResult(@NotNull TypeDfaState state,
                                      @NotNull Instruction instruction,
                                      @NotNull BiConsumer<? super VariableDescriptor, ? super DFAType> typeConsumer) {
    GrClosableBlock block = Objects.requireNonNull((GrClosableBlock)instruction.getElement());
    InferenceCache blockCache = TypeInferenceHelper.getInferenceCache(block);
    VariableDescriptor targetDescriptor = myDfaComputationState.getTargetDescriptor();
    VariableDescriptor blockDescriptor = blockCache.findDescriptor(targetDescriptor.getName());
    if (blockDescriptor == null) {
      return;
    }
    int lastInterestingInstruction = myInteresting.stream().mapToInt(Instruction::num).max().orElse(Integer.MAX_VALUE);
    if (instruction.num() <= lastInterestingInstruction) {
      Instruction[] blockFlow = block.getControlFlow();
      myDfaComputationState.putEntranceState(block, state);
      Instruction lastBlockInstruction = blockFlow[blockFlow.length - 1];
      blockCache.getInferredType(blockDescriptor, lastBlockInstruction, false, myDfaComputationState);
      TypeDfaState blockExitState = myDfaComputationState.getExitState(block);
      if (blockExitState != null) {
        for (Map.Entry<VariableDescriptor, DFAType> entry : blockExitState.getVarTypes().entrySet()) {
          VariableDescriptor descriptor = myCache.findDescriptor(entry.getKey().getName());
          typeConsumer.accept(descriptor, entry.getValue());
        }
      }
    }
    else if (myDfaComputationState.isVisited(block)) {
      myDfaComputationState.putEntranceState(block, state);
    }
  }
}
