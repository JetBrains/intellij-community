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
package org.jetbrains.plugins.groovy.lang.flow;

import com.intellij.codeInspection.dataFlow.instructions.BinopInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaRelation;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.instruction.*;
import org.jetbrains.plugins.groovy.lang.flow.value.GrDfaValueFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.controlFlow.ControlFlowBuilderUtil.isCertainlyReturnStatement;

public class GrCFExpressionHelper<V extends GrInstructionVisitor<V>> {
  private static final Map<IElementType, DfaRelation> MAP = new HashMap<IElementType, DfaRelation>();

  static {
    MAP.put(mEQUAL, DfaRelation.EQ);
    MAP.put(mNOT_EQUAL, DfaRelation.NE);
    MAP.put(kINSTANCEOF, DfaRelation.INSTANCEOF);
    MAP.put(mGT, DfaRelation.GT);
    MAP.put(mGE, DfaRelation.GE);
    MAP.put(mLT, DfaRelation.LT);
    MAP.put(mLE, DfaRelation.LE);
    MAP.put(mPLUS, DfaRelation.PLUS);
  }

  private final GrControlFlowAnalyzerImpl<V> myAnalyzer;
  private final Stack<GrUnaryExpression> myDelayed = ContainerUtil.newStack();

  public GrCFExpressionHelper(GrControlFlowAnalyzerImpl<V> analyzer) {
    myAnalyzer = analyzer;
  }

  void initialize(@NotNull GrVariable variable, @NotNull GrExpression initializer) {
    final DfaVariableValue dfaVariableValue = getFactory().getVarFactory().createVariableValue(variable, false);
    myAnalyzer.push(dfaVariableValue, initializer);
    initializer.accept(myAnalyzer);
    boxUnbox(variable.getDeclaredType(), initializer.getNominalType());
    myAnalyzer.addInstruction(new GrAssignInstruction(dfaVariableValue, initializer, true));
    processDelayed();
  }

  @NotNull
  private GrDfaValueFactory getFactory() {
    return myAnalyzer.factory;
  }

  void assign(@NotNull GrExpression left, @NotNull final GrExpression right) {
    assign(left, right, myAnalyzer.callHelper.new Arguments() {
      @NotNull
      @Override
      public GrExpression[] getExpressionArguments() {
        return new GrExpression[]{right};
      }
    });
  }

  void assign(@NotNull GrExpression left, @NotNull GrExpression anchor, GrCFCallHelper<V>.Arguments argumentsProvider) {
    if (left instanceof GrReferenceExpression) {
      final GroovyResolveResult result = ((GrReferenceExpression)left).advancedResolve();
      final PsiElement element = result.getElement();
      if (element instanceof PsiMethod) {
        myAnalyzer.callHelper.processMethodCall(left, (GrReferenceExpression)left, argumentsProvider);
        return;
      }
    }
    if (left instanceof GrIndexProperty) {
      myAnalyzer.callHelper.processIndexProperty((GrIndexProperty)left, argumentsProvider);
      return;
    }
    left.accept(myAnalyzer);
    argumentsProvider.runArguments();
    myAnalyzer.addInstruction(new GrAssignInstruction(getFactory().createValue(left), anchor, false));
    processDelayed();
  }

  void assign(@NotNull GrExpression left, @NotNull DfaValue right) {
    left.accept(myAnalyzer);
    myAnalyzer.push(right);
    myAnalyzer.addInstruction(new GrAssignInstruction<V>(getFactory().createValue(left), null, false));
    processDelayed();
  }

  void assignTuple(@NotNull GrExpression[] lValues, @Nullable GrExpression right) {
    if (right instanceof GrListOrMap) {
      final GrExpression[] rValues = ((GrListOrMap)right).getInitializers();
      // iterate over tuple variables and assign each 
      for (int i = 0; i < Math.min(lValues.length, rValues.length); i++) {
        assign(lValues[i], rValues[i]);
        myAnalyzer.pop();
      }
      // iterate over rest lValues and assign them to null 
      for (int i = rValues.length; i < lValues.length; i++) {
        assign(lValues[i], getFactory().getConstFactory().getNull());
        myAnalyzer.pop();
      }
      // iterate over rest rValues and evaluate them
      for (int i = lValues.length; i < rValues.length; i++) {
        rValues[i].accept(myAnalyzer);
        myAnalyzer.pop();
      }
    }
    else {
      // here we cannot know what values will be assigned
      for (GrExpression lValue : lValues) {
        assign(lValue, DfaUnknownValue.getInstance());
      }
    }
  }

  /**
   * Assuming that qualifier is on top of the stack.
   */
  void dereference(@NotNull GrExpression qualifier, @NotNull GrReferenceExpression referenceExpression, boolean writing) {
    // qualifier is already on top of stack thank to duplication
    final GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)resolved) && !writing) {
      // groovy property getter
      myAnalyzer.addInstruction(new GrMethodCallInstruction<V>(
        referenceExpression, (PsiMethod)resolved, getFactory().createValue(referenceExpression)
      ));
    }
    else {
      myAnalyzer.addInstruction(new GrDereferenceInstruction<V>(qualifier));
      // push value
      myAnalyzer.push(getFactory().createValue(referenceExpression), referenceExpression, writing);
    }
    processDelayed();
  }

  void binaryOperation(@NotNull GrExpression anchor,
                       @NotNull GrExpression left,
                       @NotNull GrExpression right,
                       @NotNull IElementType operatorToken,
                       @NotNull GroovyResolveResult[] resolveResults) {
    if (resolveResults.length == 1 && resolveResults[0].isValidResult() && !(operatorToken == mEQUAL || operatorToken == mNOT_EQUAL)) {
      final GroovyResolveResult result = resolveResults[0];
      myAnalyzer.callHelper.processRegularCall(anchor, left, result, right);
    }
    else {
      left.accept(myAnalyzer);
      right.accept(myAnalyzer);
      myAnalyzer.addInstruction(new BinopInstruction<V>(MAP.get(operatorToken), anchor));
      processDelayed();
    }
  }

  void boxUnbox(PsiType expectedType, PsiType actualType) {
    if (TypeConversionUtil.isPrimitiveAndNotNull(expectedType) && TypeConversionUtil.isPrimitiveWrapper(actualType)) {
      myAnalyzer.addInstruction(new GrDummyInstruction<V>("UNBOXING"));
    }
    else if (TypeConversionUtil.isAssignableFromPrimitiveWrapper(expectedType) && TypeConversionUtil.isPrimitiveAndNotNull(actualType)) {
      myAnalyzer.addInstruction(new GrDummyInstruction<V>("BOXING"));
    }
  }

  void processIncrementDecrement(GrUnaryExpression expression) {
    final GrExpression operand = expression.getOperand();
    assert operand != null;
    operand.accept(myAnalyzer);
    final GroovyResolveResult[] results = expression.multiResolve(false);
    myAnalyzer.callHelper.processRegularCall(
      expression, operand, results.length == 1 ? results[0] : GroovyResolveResult.EMPTY_RESULT
    );
    myAnalyzer.addInstruction(new GrAssignInstruction<V>(null, expression, false));
  }

  void processDelayed() {
    for (GrUnaryExpression expression = myDelayed.tryPop(); expression != null; expression = myDelayed.tryPop()) {
      processIncrementDecrement(expression);
      myAnalyzer.pop();
    }
  }

  void delay(@NotNull GrUnaryExpression expression) {
    myDelayed.push(expression);
  }

  @Contract("null -> false")
  public static boolean shouldCheckReturn(GroovyPsiElement element) {
    if (!(element instanceof GrExpression)
        || element instanceof GrConditionalExpression
        || element instanceof GrParenthesizedExpression) {
      return false;
    }
    final PsiElement parent = element.getParent();
    return parent instanceof GrReturnStatement && ((GrReturnStatement)parent).getReturnValue() == element
           || isCertainlyReturnStatement((GrStatement)element);
  }
}
