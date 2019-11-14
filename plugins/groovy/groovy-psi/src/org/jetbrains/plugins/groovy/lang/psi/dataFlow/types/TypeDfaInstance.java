// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Couple;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class TypeDfaInstance implements DfaInstance<TypeDfaState> {

  private final Instruction[] myFlow;
  private final Set<Instruction> myInteresting;
  private final Set<Instruction> myAcyclicInstructions;
  private final InferenceCache myCache;
  private final InitialTypeProvider myInitialTypeProvider;

  TypeDfaInstance(Instruction @NotNull [] flow,
                  @NotNull Couple<Set<Instruction>> interesting,
                  @NotNull InferenceCache cache,
                  @NotNull InitialTypeProvider initialTypeProvider) {
    myFlow = flow;
    myInteresting = interesting.first;
    myAcyclicInstructions = interesting.second;
    myCache = cache;
    myInitialTypeProvider = initialTypeProvider;
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
      handleClosureBlock(state, (GrClosableBlock)instruction.getElement());
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
        PsiType initialType = myInitialTypeProvider.initialType(descriptor);
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

  private void handleClosureBlock(@NotNull TypeDfaState state, @NotNull GrClosableBlock element) {
    InvocationKind kind = ClosureFlowUtil.getInvocationKind(element);
    switch (kind) {
      case EXACTLY_ONCE:
        collectClosureBlockResults(state, element, (nestedState) -> {
          for (Map.Entry<VariableDescriptor, DFAType> entry : nestedState.getVarTypes().entrySet()) {
            VariableDescriptor descriptor = myCache.findDescriptor(entry.getKey().getName());
            DFAType inferredType = entry.getValue();
            state.putType(descriptor, inferredType);
          }
        });
        break;
      case UNDETERMINED:
        collectClosureBlockResults(state, element, (nestedState) -> {
          for (Map.Entry<VariableDescriptor, DFAType> entry : nestedState.getVarTypes().entrySet()) {
            VariableDescriptor descriptor = myCache.findDescriptor(entry.getKey().getName());
            DFAType inferredType = entry.getValue();
            DFAType existingType = state.getVariableType(descriptor);
            if (existingType != null) {
              state.putType(descriptor, DFAType.create(inferredType, existingType, element.getManager()));
            }
          }
        });
        break;
      case UNKNOWN:
        break;
    }
  }

  private void collectClosureBlockResults(@NotNull TypeDfaState state,
                                          @NotNull GrClosableBlock block,
                                          @NotNull Consumer<? super TypeDfaState> typeProducer) {
    InferenceCache nestedCache = TypeInferenceHelper.getInferenceCache(block);
    VariableDescriptor targetDescriptor = myCache.getTargetDescriptor();
    if (targetDescriptor == null) {
      return;
    }
    VariableDescriptor nestedDescriptor = nestedCache.findDescriptor(targetDescriptor.getName());
    if (nestedDescriptor == null) {
      return;
    }
    Instruction[] nestedFlow = block.getControlFlow();
    Instruction lastNestedInstruction = nestedFlow[nestedFlow.length - 1];
    myCache.saveCurrentState(block, state);
    TypeInferenceHelper.getInferredType(nestedDescriptor, lastNestedInstruction, block);
    TypeDfaState lastState = nestedCache.getCurrentState(block);
    if (lastState != null) {
      typeProducer.accept(lastState);
    }
  }
}
