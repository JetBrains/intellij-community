/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.flow.instruction;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.BinopInstruction;
import com.intellij.codeInspection.dataFlow.instructions.CheckReturnValueInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.GrDataFlowRunner;
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaValueFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.dataFlow.StandardInstructionVisitor.forceNotNull;
import static com.intellij.codeInspection.dataFlow.StandardInstructionVisitor.handleConstantComparison;
import static com.intellij.codeInspection.dataFlow.value.DfaRelation.EQ;
import static com.intellij.codeInspection.dataFlow.value.DfaRelation.UNDEFINED;

public class GrGenericStandardInstructionVisitor<V extends GrGenericStandardInstructionVisitor<V>> extends GrInstructionVisitor<V> {

  public GrGenericStandardInstructionVisitor(GrDataFlowRunner<V> runner) {
    super(runner);
  }

  private static final Object ANY_VALUE = new Object();
  //private final Set<BinopInstruction> myReachable = new THashSet<BinopInstruction>();
  //private final Set<BinopInstruction> myCanBeNullInInstanceof = new THashSet<BinopInstruction>();
  private final MultiMap<PushInstruction, Object> myPossibleVariableValues = MultiMap.createSet();
  private final Set<PsiElement> myNotToReportReachability = new THashSet<PsiElement>();
  //private final Set<JavaInstanceofInstruction> myUsefulInstanceofs = new THashSet<JavaInstanceofInstruction>();

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<GrMethodCallInstruction, Nullness> myReturnTypeNullability =
    new FactoryMap<GrMethodCallInstruction, Nullness>() {
      @Override
      protected Nullness create(GrMethodCallInstruction key) {
        final GrExpression callExpression = key.getCall();
        if (callExpression instanceof GrNewExpression) {
          return Nullness.NOT_NULL;
        }
        return DfaPsiUtil.getElementNullability(key.getReturnType(), key.getTargetMethod());
      }
    };

  @Override
  public DfaInstructionState<V>[] visitAssignGroovy(GrAssignInstruction<V> instruction, DfaMemoryState memState) {
    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)dfaDest;

      DfaValueFactory factory = myRunner.getFactory();
      if (dfaSource instanceof DfaVariableValue && factory.getVarFactory().getAllQualifiedBy(var).contains(dfaSource)) {
        Nullness nullability = memState.isNotNull(dfaSource)
                               ? Nullness.NOT_NULL
                               : ((DfaVariableValue)dfaSource).getInherentNullability();
        dfaSource = factory.createTypeValue(((DfaVariableValue)dfaSource).getVariableType(), nullability);
      }

      if (var.getInherentNullability() == Nullness.NOT_NULL) {
        checkNotNullable(memState, dfaSource, NullabilityProblem.assigningToNotNull, instruction.getRExpression());
      }
      final PsiModifierListOwner psi = var.getPsiVariable();
      if (!(psi instanceof PsiField) || !psi.hasModifierProperty(PsiModifier.VOLATILE)) {
        memState.setVarValue(var, dfaSource);
      }
      if (var.getInherentNullability() == Nullness.NULLABLE && !memState.isNotNull(dfaSource) && instruction.isInitializer()) {
        DfaMemoryStateImpl stateImpl = (DfaMemoryStateImpl)memState;
        stateImpl.setVariableState(var, stateImpl.getVariableState(var).withNullability(Nullness.NULLABLE));
      }
    }
    else if (dfaDest instanceof DfaTypeValue && ((DfaTypeValue)dfaDest).isNotNull()) {
      checkNotNullable(memState, dfaSource, NullabilityProblem.assigningToNotNull, instruction.getRExpression());
    }

    memState.push(dfaDest);

    return nextInstruction(instruction, myRunner, memState);
  }

  @Override
  public DfaInstructionState<V>[] visitCheckReturnValue(CheckReturnValueInstruction<V> instruction, DfaMemoryState state) {
    final DfaValue retValue = state.pop();
    final PsiElement instructionReturn = instruction.getReturn();
    final GrMethod containingMethod = PsiTreeUtil.getParentOfType(instructionReturn, GrMethod.class);
    if (containingMethod != null) {
      if (NullableNotNullManager.isNotNull(containingMethod)) {
        checkNotNullable(state, retValue, NullabilityProblem.nullableReturn, instructionReturn);
      }
    }
    return nextInstruction(instruction, myRunner, state);
  }

  @Override
  public DfaInstructionState<V>[] visitMemberReference(GrMemberReferenceInstruction<V> instruction, DfaMemoryState state) {
    final DfaValue qualifier = state.pop();
    final DfaValue value = instruction.getValue();
    if (instruction.isSafe()) {
      final DfaConstValue nullValue = myRunner.getFactory().getConstFactory().getNull();

      final DfaMemoryState notNullState = state.createCopy();
      final DfaMemoryState nullState = state.createCopy();

      final DfaValue isNullRelation = myRunner.getFactory().getRelationFactory().createRelation(qualifier, nullValue, EQ, false);
      final DfaValue isNotNullRelation = isNullRelation.createNegated();

      final List<DfaInstructionState<V>> result = new ArrayList<DfaInstructionState<V>>();
      if (notNullState.applyCondition(isNotNullRelation)) {
        //check(value, state, instruction.getExpression());
        notNullState.push(instruction.getValue());
        result.add(new DfaInstructionState<V>(myRunner.getInstruction(instruction.getIndex() + 1), notNullState));
      }
      if (nullState.applyCondition(isNullRelation)) {
        //markNull(instruction.getExpression());
        nullState.push(nullValue);
        result.add(new DfaInstructionState<V>(myRunner.getInstruction(instruction.getIndex() + 1), nullState));
      }
      //noinspection unchecked
      return result.toArray(new DfaInstructionState[result.size()]);
    }
    else {
      if (!checkNotNullable(state, qualifier, NullabilityProblem.fieldAccessNPE, instruction.getExpression())) {
        forceNotNull(myRunner.getFactory(), state, qualifier);
      }
      //check(value, state, instruction.getExpression());
      state.push(value);
      return nextInstruction(instruction, myRunner, state);
    }
  }

  @Override
  public DfaInstructionState<V>[] visitPush(PushInstruction<V> instruction, DfaMemoryState memState) {
    //check(instruction.getValue(), memState, instruction.getPlace());
    return super.visitPush(instruction, memState);
  }

  @Override
  public DfaInstructionState<V>[] visitMethodCallGroovy(final GrMethodCallInstruction<V> instruction, final DfaMemoryState state) {
    for (final GrNamedArgument ignored : instruction.getNamedArguments()) {
      state.pop();
    }

    for (final GrExpression expression : instruction.getExpressionArguments()) {
      final DfaValue arg = state.pop();
      final Nullness nullability = instruction.getParameterNullability(expression);
      if (nullability == Nullness.NOT_NULL) {
        if (!checkNotNullable(state, arg, NullabilityProblem.passingNullableToNotNullParameter, expression)) {
          forceNotNull(myRunner.getFactory(), state, arg);
        }
      }
      else if (nullability == Nullness.UNKNOWN) {
        checkNotNullable(state, arg, NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter, expression);
      }
    }

    for (final GrClosableBlock ignored : instruction.getClosureArguments()) {
      state.pop();
    }
    

    final DfaValue qualifier = state.pop();
    final DfaValue methodResultValue = getMethodResultValue(instruction);
    if (instruction.isSafeCall()) {
      final DfaConstValue nullValue = myRunner.getFactory().getConstFactory().getNull();

      final DfaMemoryState notNullState = state.createCopy();
      final DfaMemoryState nullState = state.createCopy();

      final DfaValue isNullRelation = myRunner.getFactory().getRelationFactory().createRelation(qualifier, nullValue, EQ, false);
      final DfaValue isNotNullRelation = isNullRelation.createNegated();

      final List<DfaInstructionState<V>> result = new ArrayList<DfaInstructionState<V>>();
      if (notNullState.applyCondition(isNotNullRelation)) {
        //check(methodResultValue, memState, instruction.getCallExpression());
        notNullState.push(methodResultValue);
        if (instruction.shouldFlushFields()) {
          notNullState.flushFields();
        }
        result.add(new DfaInstructionState<V>(myRunner.getInstruction(instruction.getIndex() + 1), notNullState));
      }
      if (nullState.applyCondition(isNullRelation)) {
        //markNull(instruction.getCall());
        nullState.push(nullValue);
        result.add(new DfaInstructionState<V>(myRunner.getInstruction(instruction.getIndex() + 1), nullState));
      }
      //noinspection unchecked
      return result.toArray(new DfaInstructionState[result.size()]);
    }
    else {
      if (!checkNotNullable(state, qualifier, NullabilityProblem.callNPE, instruction.getCall())) {
        forceNotNull(myRunner.getFactory(), state, qualifier);
      }
      //check(methodResultValue, memState, instruction.getCallExpression());
      state.push(methodResultValue);
      if (instruction.shouldFlushFields()) {
        state.flushFields();
      }
      return nextInstruction(instruction, myRunner, state);
    }
  }


  @NotNull
  private DfaValue getMethodResultValue(GrMethodCallInstruction instruction) {
    final GrDfaValueFactory factory = myRunner.getFactory();
    DfaValue precalculated = instruction.getPrecalculatedReturnValue();
    if (precalculated != null) {
      return precalculated;
    }

    final PsiType type = instruction.getReturnType();

    if (type != null && (type instanceof PsiClassType || type.getArrayDimensions() > 0)) {
      Nullness nullability = myReturnTypeNullability.get(instruction);
      if (nullability == Nullness.UNKNOWN && factory.isUnknownMembersAreNullable()) {
        nullability = Nullness.NULLABLE;
      }
      return factory.createTypeValue(type, nullability);
    }
    return DfaUnknownValue.getInstance();
  }


  protected boolean checkNotNullable(DfaMemoryState state,
                                     DfaValue value,
                                     NullabilityProblem problem,
                                     PsiElement anchor) {
    boolean notNullable = state.checkNotNullable(value);
    if (notNullable && problem != NullabilityProblem.passingNullableArgumentToNonAnnotatedParameter) {
      state.applyCondition(myRunner.getFactory().getRelationFactory().createRelation(
        value, myRunner.getFactory().getConstFactory().getNull(), DfaRelation.NE, false
      ));
    }
    return notNullable;
  }

  @Override
  public DfaInstructionState<V>[] visitBinop(BinopInstruction<V> instruction, DfaMemoryState memState) {
    //myReachable.add(instruction);

    DfaValue dfaRight = memState.pop();
    DfaValue dfaLeft = memState.pop();

    final DfaRelation opSign = instruction.getOperationSign();
    if (opSign != UNDEFINED) {
      DfaInstructionState[] states = handleConstantComparison(instruction, myRunner, memState, dfaRight, dfaLeft, opSign);
      if (states == null) {
        states = handleRelationBinop(instruction, myRunner, memState, dfaRight, dfaLeft);
      }
      if (states != null) {
        //noinspection unchecked
        return states;
      }

      if (DfaRelation.PLUS == opSign) {
        memState.push(myRunner.getFactory().getTypeFactory().getNonNullStringValue(instruction.getPsiAnchor(), instruction.getProject()));
      }
      else {
        //if (instruction instanceof JavaInstanceofInstruction) {
        //  handleInstanceof((JavaInstanceofInstruction)instruction, dfaRight, dfaLeft);
        //}
        memState.push(DfaUnknownValue.getInstance());
      }
    }
    else {
      memState.push(DfaUnknownValue.getInstance());
    }

    instruction.setTrueReachable();  // Not a branching instruction actually.
    instruction.setFalseReachable();

    return nextInstruction(instruction, myRunner.getInstructions(), memState);
  }

  @Nullable
  private DfaInstructionState<V>[] handleRelationBinop(BinopInstruction<V> instruction,
                                                       AbstractDataFlowRunner<V> runner,
                                                       DfaMemoryState memState,
                                                       DfaValue dfaRight, DfaValue dfaLeft) {
    DfaValueFactory factory = runner.getFactory();
    final Instruction<V> next = runner.getInstruction(instruction.getIndex() + 1);
    DfaRelationValue dfaRelation = factory.getRelationFactory().createRelation(dfaLeft, dfaRight, instruction.getOperationSign(), false);
    if (dfaRelation == null) {
      return null;
    }

    //myCanBeNullInInstanceof.add(instruction);

    ArrayList<DfaInstructionState<V>> states = new ArrayList<DfaInstructionState<V>>();

    final DfaMemoryState trueCopy = memState.createCopy();
    if (trueCopy.applyCondition(dfaRelation)) {
      trueCopy.push(factory.getConstFactory().getTrue());
      instruction.setTrueReachable();
      states.add(new DfaInstructionState<V>(next, trueCopy));
    }

    //noinspection UnnecessaryLocalVariable
    DfaMemoryState falseCopy = memState;
    if (falseCopy.applyCondition(dfaRelation.createNegated())) {
      falseCopy.push(factory.getConstFactory().getFalse());
      instruction.setFalseReachable();
      states.add(new DfaInstructionState<V>(next, falseCopy));
      //if (instruction instanceof JavaInstanceofInstruction && !falseCopy.isNull(dfaLeft)) {
      //myUsefulInstanceofs.add((JavaInstanceofInstruction)instruction);
      //}
    }

    //noinspection unchecked
    DfaInstructionState<V>[] result = new DfaInstructionState[states.size()];
    return states.toArray(result);
  }

  @SuppressWarnings("unused")
  public void skipConstantConditionReporting(@Nullable PsiElement anchor) {
    ContainerUtil.addIfNotNull(myNotToReportReachability, anchor);
  }

  //public final void check(DfaValue value, DfaMemoryState state, PsiElement element) {
  //  if (state.isNull(value)) {
  //    markNull(element);
  //  }
  //  else if (state.isNotNull(value)) {
  //    markNotNull(element);
  //  }
  //}

  public void markNotNull(PsiElement element) {
  }

  public void markNull(PsiElement element) {
  }
}
