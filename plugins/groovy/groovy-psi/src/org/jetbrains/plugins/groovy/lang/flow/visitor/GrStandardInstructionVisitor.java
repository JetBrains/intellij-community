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
package org.jetbrains.plugins.groovy.lang.flow.visitor;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.instructions.BinopInstruction;
import com.intellij.codeInspection.dataFlow.instructions.CheckReturnValueInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.GrDataFlowRunner;
import org.jetbrains.plugins.groovy.lang.flow.GrDfaMemoryState;
import org.jetbrains.plugins.groovy.lang.flow.instruction.*;
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaConstValueFactory;
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaValueFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.arithmetic.GrRangeExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.codeInspection.dataFlow.StandardInstructionVisitor.forceNotNull;
import static com.intellij.codeInspection.dataFlow.StandardInstructionVisitor.handleConstantComparison;
import static com.intellij.codeInspection.dataFlow.value.DfaRelation.UNDEFINED;
import static org.jetbrains.plugins.groovy.lang.flow.visitor.GrNullabilityProblem.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_RANGE;

public class GrStandardInstructionVisitor extends GrInstructionVisitor {

  private final GrDfaValueFactory myFactory;
  private final GrMethodCallHelper myHelper;

  public GrStandardInstructionVisitor(GrDataFlowRunner runner) {
    super(runner);
    myFactory = runner.getFactory();
    myHelper = new GrMethodCallHelper(this);
  }

  public GrDfaValueFactory getFactory() {
    return myFactory;
  }

  @Override
  public DfaInstructionState[] visitBoxInstruction(GrBoxInstruction instruction, DfaMemoryState state) {
    final DfaValue value = state.pop();
    final DfaValue boxed = myFactory.getBoxedFactory().createBoxed(value);
    state.push(boxed == null ? value : boxed);
    return nextInstruction(instruction, state);
  }

  @Override
  public DfaInstructionState[] visitUnboxInstruction(GrUnboxInstruction instruction, DfaMemoryState state) {
    final DfaValue value = state.pop();
    if (!checkNotNullable(state, value, unboxingNullable, instruction.getAnchor())) {
      forceNotNull(myRunner, state, value);
    }
    state.push(myFactory.getBoxedFactory().createUnboxed(value));
    return nextInstruction(instruction, state);
  }

  @Override
  public DfaInstructionState[] visitAssign(GrAssignInstruction instruction, DfaMemoryState memState) {
    DfaValue dfaSource = memState.pop();
    DfaValue dfaDest = memState.pop();

    if (dfaDest instanceof DfaVariableValue) {
      DfaVariableValue var = (DfaVariableValue)dfaDest;

      if (dfaSource instanceof DfaVariableValue && myFactory.getVarFactory().getAllQualifiedBy(var).contains(dfaSource)) {
        Nullness nullability = memState.isNotNull(dfaSource)
                               ? Nullness.NOT_NULL
                               : ((DfaVariableValue)dfaSource).getInherentNullability();
        dfaSource = myFactory.createTypeValue(((DfaVariableValue)dfaSource).getVariableType(), nullability);
      }

      if (var.getInherentNullability() == Nullness.NOT_NULL) {
        checkNotNullable(memState, dfaSource, assigningToNotNull, instruction.getRExpression());
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
      checkNotNullable(memState, dfaSource, assigningToNotNull, instruction.getRExpression());
    }

    memState.push(dfaDest);

    return nextInstruction(instruction, memState);
  }

  @Override
  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction, DfaMemoryState state) {
    final DfaValue retValue = state.pop();
    final PsiElement instructionReturn = instruction.getReturn();
    final PsiElement containingMethod = PsiTreeUtil.getParentOfType(instructionReturn, GrMethod.class, GrClosableBlock.class);
    if (containingMethod instanceof PsiModifierListOwner) {
      if (NullableNotNullManager.isNotNull((PsiModifierListOwner)containingMethod)) {
        checkNotNullable(state, retValue, nullableReturn, instructionReturn);
      }
    }
    return nextInstruction(instruction, state);
  }

  @Override
  public DfaInstructionState[] visitDereference(GrDereferenceInstruction instruction, DfaMemoryState state) {
    final DfaValue qualifier = state.pop();
    if (!checkNotNullable(state, qualifier, fieldAccessNPE, instruction.getExpression())) {
      forceNotNull(myRunner, state, qualifier);
    }
    return nextInstruction(instruction, state);
  }

  @Override
  public DfaInstructionState[] visitMethodCall(GrMethodCallInstruction instruction, DfaMemoryState state) {
    final DfaValue[] argValues = myHelper.popAndCheckCallArguments(instruction, state);
    final DfaValue qualifier = state.pop();
    if (instruction.requiresNotNullQualifier() && !checkNotNullable(state, qualifier, callNPE, instruction.getCall())) {
      forceNotNull(myRunner, state, qualifier);
    }

    final Set<DfaMemoryState> finalStates = ContainerUtil.newLinkedHashSet();
    LinkedHashSet<DfaMemoryState> currentStates = ContainerUtil.newLinkedHashSet(state);
    final PsiMethod method = instruction.getTargetMethod();
    if (method != null) {
      for (MethodContract contract : ControlFlowAnalyzer.getMethodContracts(method)) {
        currentStates = myHelper.addContractResults(argValues, contract, currentStates, instruction, finalStates);
      }
    }
    for (DfaMemoryState currentState : currentStates) {
      currentState.push(myHelper.getMethodResultValue(instruction));
      finalStates.add(currentState);
    }

    @SuppressWarnings("unchecked")
    DfaInstructionState[] result = new DfaInstructionState[finalStates.size()];
    int i = 0;
    for (DfaMemoryState finalState : finalStates) {
      if (instruction.shouldFlushFields()) {
        finalState.flushFields();
      }
      result[i++] = new DfaInstructionState(myRunner.getInstruction(instruction.getIndex() + 1), finalState);
    }
    return result;
  }

  protected boolean checkNotNullable(@NotNull DfaMemoryState state,
                                     @NotNull DfaValue value,
                                     @NotNull GrNullabilityProblem problem,
                                     @Nullable PsiElement anchor) {
    boolean notNullable = state.checkNotNullable(value);
    if (notNullable && problem != passingNullableArgumentToNonAnnotatedParameter) {
      state.applyCondition(myRunner.getFactory().getRelationFactory().createRelation(
        value, myRunner.getFactory().getConstFactory().getNull(), DfaRelation.NE, false
      ));
    }
    report(notNullable, state.isEphemeral(), problem, anchor);
    return notNullable;
  }

  protected void report(boolean ok, boolean ephemeral, @NotNull GrNullabilityProblem problem, @Nullable PsiElement anchor) {
  }

  @Override
  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DfaMemoryState memState) {
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
        memState.push(DfaUnknownValue.getInstance());
      }
    }
    else {
      memState.push(DfaUnknownValue.getInstance());
    }

    instruction.setTrueReachable();  // Not a branching instruction actually.
    instruction.setFalseReachable();

    return nextInstruction(instruction, memState);
  }

  @Nullable
  private static DfaInstructionState[] handleRelationBinop(BinopInstruction instruction,
                                                           AbstractDataFlowRunner runner,
                                                           DfaMemoryState memState,
                                                           DfaValue dfaRight, DfaValue dfaLeft) {
    DfaValueFactory factory = runner.getFactory();
    final Instruction next = runner.getInstruction(instruction.getIndex() + 1);
    DfaRelationValue dfaRelation = factory.getRelationFactory().createRelation(dfaLeft, dfaRight, instruction.getOperationSign(), false);
    if (dfaRelation == null) {
      return null;
    }

    ArrayList<DfaInstructionState> states = new ArrayList<DfaInstructionState>();

    final DfaMemoryState trueCopy = memState.createCopy();
    if (trueCopy.applyCondition(dfaRelation)) {
      trueCopy.push(factory.getConstFactory().getTrue());
      instruction.setTrueReachable();
      states.add(new DfaInstructionState(next, trueCopy));
    }

    //noinspection UnnecessaryLocalVariable
    DfaMemoryState falseCopy = memState;
    if (falseCopy.applyCondition(dfaRelation.createNegated())) {
      falseCopy.push(factory.getConstFactory().getFalse());
      instruction.setFalseReachable();
      states.add(new DfaInstructionState(next, falseCopy));
    }

    //noinspection unchecked
    DfaInstructionState[] result = new DfaInstructionState[states.size()];
    return states.toArray(result);
  }

  @Override
  public DfaInstructionState[] visitTypeCast(GrTypeCastInstruction instruction, DfaMemoryState state) {
    final DfaValue value = state.pop();
    final PsiType type = instruction.getCastTo();
    if (type == PsiType.BOOLEAN) {
      state.push(getFactory().createTypeValue(type, Nullness.NOT_NULL));
    }
    else if (type instanceof PsiPrimitiveType) {
      if (!checkNotNullable(state, value, unboxingNullable, instruction.getCastExpression())) {
        forceNotNull(myRunner, state, value);
      }
      state.push(getFactory().createTypeValue(type, Nullness.NOT_NULL));
    }
    else {
      if (state.isNotNull(value)) {
        state.push(getFactory().createTypeValue(type, Nullness.NOT_NULL));
      }
      else if (state.isNull(value)) {
        state.push(getFactory().createTypeValue(type, Nullness.NULLABLE));
      }
      else {
        state.push(getFactory().createTypeValue(type, Nullness.UNKNOWN));
      }
    }
    return nextInstruction(instruction, state);
  }

  @Override
  public DfaInstructionState[] visitRange(GrRangeInstruction instruction, DfaMemoryState state) {
    final DfaValue to = state.pop();
    final DfaValue from = state.pop();
    final GrRangeExpression expression = instruction.getExpression();

    final boolean fromOk = state.checkNotNullable(from);
    final boolean toOk = state.checkNotNullable(to);
    final boolean inclusive = expression.isInclusive();

    if (!fromOk && (toOk || inclusive)) {
      report(false, state.isEphemeral(), passingNullableAsRangeBound, expression.getLeftOperand());
      if (inclusive) forceNotNull(myRunner, state, from);
    }
    if (!toOk && (fromOk || inclusive)) {
      report(false, state.isEphemeral(), passingNullableAsRangeBound, expression.getRightOperand());
      if (inclusive) forceNotNull(myRunner, state, to);
    }

    state.push(myRunner.getFactory().createTypeValue(TypesUtil.createType(GROOVY_LANG_RANGE, expression), Nullness.NOT_NULL));
    return nextInstruction(instruction, state);
  }

  @Override
  public DfaInstructionState[] visitCoerceToBoolean(GrCoerceToBooleanInstruction instruction, DfaMemoryState memoryState) {
    final GrDfaMemoryState state = (GrDfaMemoryState)memoryState;
    final DfaValue value = state.pop();
    final GrDfaConstValueFactory constFactory = myFactory.getConstFactory();
    if (value == DfaUnknownValue.getInstance()) {
      state.push(value);
      return nextInstruction(instruction, state);
    }
    final GrDfaMemoryState trueState = state.createCopy();
    final GrDfaMemoryState falseState = state.createCopy();

    final List<DfaMemoryState> states = ContainerUtil.newArrayList();
    if (trueState.coerceTo(true, value)) {
      final DfaValue boxed = myFactory.getBoxedFactory().createBoxed(constFactory.getTrue());
      assert boxed != null;
      trueState.push(boxed);
      states.add(trueState);
    }
    if (falseState.coerceTo(false, value)) {
      final DfaValue boxed = myFactory.getBoxedFactory().createBoxed(constFactory.getFalse());
      assert boxed != null;
      falseState.push(boxed);
      states.add(falseState);
    }
    return nextInstructions(instruction, states);
  }
}
