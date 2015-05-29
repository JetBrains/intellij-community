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

import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelation;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrDereferenceInstruction;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrMethodCallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mOPTIONAL_DOT;

public class GrCFCallHelper<V extends GrInstructionVisitor<V>> {

  public abstract class Arguments {

    @NotNull
    protected GrNamedArgument[] getNamedArguments() {
      return GrNamedArgument.EMPTY_ARRAY;
    }

    @NotNull
    protected GrClosableBlock[] getClosureArguments() {
      return GrClosableBlock.EMPTY_ARRAY;
    }

    @NotNull
    protected GrExpression[] getExpressionArguments() {
      return GrExpression.EMPTY_ARRAY;
    }

    public int runArguments() {
      return visitArguments(getNamedArguments(), getExpressionArguments(), getClosureArguments());
    }
  }

  public class CallBasedArguments extends Arguments {

    private final GrCall myCall;

    public CallBasedArguments(GrCall call) {
      myCall = call;
    }

    @NotNull
    @Override
    public GrNamedArgument[] getNamedArguments() {
      return myCall.getNamedArguments();
    }

    @NotNull
    @Override
    public GrClosableBlock[] getClosureArguments() {
      return myCall.getClosureArguments();
    }

    @NotNull
    @Override
    public GrExpression[] getExpressionArguments() {
      return myCall.getExpressionArguments();
    }
  }

  public class MergedArguments extends Arguments {

    private final @NotNull GrExpression[] myExpressions;
    private final @NotNull Arguments myArguments;

    public MergedArguments(@NotNull GrExpression[] expressionArguments,
                           @NotNull Arguments arguments) {
      myExpressions = expressionArguments;
      myArguments = arguments;
    }

    @NotNull
    @Override
    protected GrNamedArgument[] getNamedArguments() {
      return myArguments.getNamedArguments();
    }

    @NotNull
    @Override
    public GrExpression[] getExpressionArguments() {
      return ArrayUtil.mergeArrays(myExpressions, myArguments.getExpressionArguments());
    }

    @NotNull
    @Override
    protected GrClosableBlock[] getClosureArguments() {
      return myArguments.getClosureArguments();
    }
  }

  public final Arguments EMPTY = new Arguments() {
    @Override
    public int runArguments() {
      return 0;
    }
  };

  private final GrControlFlowAnalyzerImpl<V> myAnalyzer;

  public GrCFCallHelper(GrControlFlowAnalyzerImpl<V> analyzer) {
    myAnalyzer = analyzer;
  }

  /**
   * Assuming that qualifier is not processed yet.
   * Entry point for GrMethodCall
   */
  void processMethodCall(@NotNull GrMethodCall methodCall) {
    processMethodCall(methodCall.getInvokedExpression(), methodCall);
  }

  /**
   * Assuming that qualifier is not processed yet
   * Here we choose how to process each particular call
   */
  private void processMethodCall(@NotNull GrExpression invokedExpression, @NotNull GrMethodCall call) {
    if (invokedExpression instanceof GrReferenceExpression) {
      final GrReferenceExpression reference = (GrReferenceExpression)invokedExpression;
      final GroovyResolveResult callResolveResult = call.advancedResolve();
      if (callResolveResult.getElement() instanceof PsiMethod && callResolveResult.isInvokedOnProperty()) {
        processCallableCall(call);
      }
      else {
        processMethodCall(call, reference, new CallBasedArguments(call));
      }
    }
    else {
      // closure call 
      // ({-> "s"})()
      processCallableCall(call);
    }
  }

  /**
   * Assuming that qualifier is not processed yet
   * Processes calls with implicit call() call.
   */
  private void processCallableCall(@NotNull GrMethodCall call) {
    if (!processCallableCallInner(call)) {
      fallback(call.getInvokedExpression(), new CallBasedArguments(call));
    }
  }

  /**
   * Assuming that qualifier is not processed yet
   */
  private boolean processCallableCallInner(@NotNull GrMethodCall call) {
    final GrExpression invoked = call.getInvokedExpression();
    final PsiType type = invoked.getType();
    final GrArgumentList argumentList = call.getArgumentList();
    if (type == null || argumentList == null) return false;

    final GroovyResolveResult[] resolveResults = ResolveUtil.getMethodCandidates(type, "call", call, argumentList.getExpressionTypes());
    if (resolveResults.length != 1 || !resolveResults[0].isValidResult()) return false;

    processRegularCall(call, invoked, resolveResults[0], new CallBasedArguments(call), null);
    return true;
  }

  /**
   * Assuming that qualifier is not processed yet.
   * Chooses how to process regular calls based on dot token.
   * Processes safe calls then redirects to regular processing
   * or
   * redirects as is.
   */
  void processMethodCall(@NotNull GrExpression highlight,
                         @NotNull GrReferenceExpression invokedReference,
                         @NotNull Arguments arguments) {
    final GroovyResolveResult result = invokedReference.advancedResolve();
    final GrExpression qualifier = invokedReference.getQualifierExpression();
    if (invokedReference.getDotTokenType() == mOPTIONAL_DOT) {
      if (qualifier != null) {
        qualifier.accept(myAnalyzer);
      }
      else {
        myAnalyzer.pushUnknown();
      }
      myAnalyzer.pushNull();
      myAnalyzer.addInstruction(new BinopInstruction(DfaRelation.NE, invokedReference));
      final ConditionalGotoInstruction gotoToNotNull = myAnalyzer.addInstruction(new ConditionalGotoInstruction(null, true, qualifier));

      // not null branch
      processRegularCall(highlight, qualifier, result, arguments, myAnalyzer.factory.createValue(invokedReference));

      final GotoInstruction<V> gotoEnd = myAnalyzer.addInstruction(new GotoInstruction<V>(null));
      gotoToNotNull.setOffset(myAnalyzer.flow.getNextOffset());

      // null branch
      // even if qualifier is null, groovy evaluates arguments
      final int argumentsToPop = arguments.runArguments();
      for (int i = 0; i < argumentsToPop; i++) {
        myAnalyzer.pop();
      }
      myAnalyzer.pushNull();
      gotoEnd.setOffset(myAnalyzer.flow.getNextOffset());
    }
    else {
      processRegularCall(highlight, qualifier, result, arguments, myAnalyzer.factory.createValue(invokedReference));
    }
  }

  /**
   * Assuming that qualifier is not processed yet.
   * Processes regular calls.
   */
  void processRegularCall(@NotNull PsiElement highlight,
                          @Nullable GrExpression qualifier,
                          @NotNull GroovyResolveResult result,
                          @NotNull final GrExpression... expressionArguments) {
    processRegularCall(
      highlight,
      qualifier,
      result,
      new Arguments() {
        @NotNull
        @Override
        public GrExpression[] getExpressionArguments() {
          return expressionArguments;
        }
      },
      null
    );
  }

  /**
   * Assuming that qualifier is not processed yet.
   * Processes regular calls.
   */
  void processRegularCall(@NotNull PsiElement highlight,
                          @Nullable GrExpression qualifier,
                          @NotNull GroovyResolveResult result,
                          @NotNull Arguments arguments,
                          @Nullable DfaValue returnValue) {
    final PsiElement element = result.getElement();
    if (qualifier != null && element instanceof GrGdkMethod) {
      // simulate static method call
      // i.e instead of a++ call next(a)
      myAnalyzer.pushUnknown(); // qualifier
      processMethodCallStraight(
        highlight,
        new GroovyResolveResultImpl(((GrGdkMethod)element).getStaticMethod(), result.isAccessible()),
        new MergedArguments(new GrExpression[]{qualifier}, arguments),
        null
      );
    }
    else {
      if (qualifier == null) {
        myAnalyzer.pushUnknown();
      }
      else {
        qualifier.accept(myAnalyzer);
      }
      processMethodCallStraight(highlight, result, arguments, returnValue);
    }
  }

  /**
   * Assuming that qualifier is already processed.
   * This is where actual instructions are being added.
   */
  void processMethodCallStraight(@NotNull PsiElement highlight,
                                 @NotNull GroovyResolveResult result,
                                 @NotNull Arguments arguments,
                                 @Nullable DfaValue returnValue) {
    // evaluate arguments
    //visitArguments(namedArguments, expressionArguments, closureArguments);
    arguments.runArguments();
    myAnalyzer.exceptionHelper.addConditionalRuntimeThrow();
    myAnalyzer.addInstruction(new GrMethodCallInstruction<V>(
      highlight,
      arguments.getNamedArguments(),
      arguments.getExpressionArguments(),
      arguments.getClosureArguments(),
      result,
      returnValue
    ));
    myAnalyzer.expressionHelper.processDelayed();
    final PsiElement resultElement = result.getElement();
    final List<MethodContract> contracts = resultElement instanceof PsiMethod
                                           ? ControlFlowAnalyzer.getMethodCallContracts((PsiMethod)resultElement, null)
                                           : Collections.<MethodContract>emptyList();
    if (!contracts.isEmpty()) {
      // if a contract resulted in 'fail', handle it
      myAnalyzer.addInstruction(new DupInstruction());
      myAnalyzer.addInstruction(new PushInstruction(myAnalyzer.factory.getConstFactory().getContractFail(), null));
      myAnalyzer.addInstruction(new BinopInstruction(DfaRelation.EQ, null, highlight.getProject()));
      ConditionalGotoInstruction ifNotFail = myAnalyzer.addInstruction(new ConditionalGotoInstruction(null, true, null));
      myAnalyzer.exceptionHelper.returnCheckingFinally(true, highlight);
      ifNotFail.setOffset(myAnalyzer.flow.getInstructionCount());
    }

    if (!myAnalyzer.exceptionHelper.catchStack.isEmpty()) {
      myAnalyzer.exceptionHelper.addMethodThrows((PsiMethod)result.getElement(), highlight);
    }
  }

  int visitArguments(@NotNull GrCall methodCall) {
    return visitArguments(methodCall.getNamedArguments(), methodCall.getExpressionArguments(), methodCall.getClosureArguments());
  }

  private int visitArguments(@NotNull GrNamedArgument[] namedArguments,
                             @NotNull GrExpression[] expressionArguments,
                             @NotNull GrClosableBlock[] closureArguments) {
    int counter = 0;
    for (GrNamedArgument argument : namedArguments) {
      argument.accept(myAnalyzer);
      myAnalyzer.pop();
    }
    if (namedArguments.length > 0) {
      myAnalyzer.push(myAnalyzer.factory.createTypeValue(TypesUtil.createType("java.util.Map", namedArguments[0]), Nullness.NOT_NULL));
      counter++;
    }
    for (GrExpression expression : expressionArguments) {
      expression.accept(myAnalyzer);
      counter++;
    }
    for (GrClosableBlock block : closureArguments) {
      block.accept(myAnalyzer);
      counter++;
    }
    return counter;
  }

  void processIndexProperty(final @NotNull GrIndexProperty indexProperty, final @NotNull Arguments arguments) {
    final GrExpression invokedExpression = indexProperty.getInvokedExpression();
    final GroovyResolveResult[] results = arguments == EMPTY
                                          ? indexProperty.multiResolveGetter(false)
                                          : indexProperty.multiResolveSetter(false);
    final Arguments mergedArguments = new MergedArguments(indexProperty.getExpressionArguments(), arguments);
    if (results.length == 1 && results[0].isValidResult()) {
      invokedExpression.accept(myAnalyzer); // qualifier
      processMethodCallStraight(indexProperty, results[0], mergedArguments, null);
    }
    else {
      fallback(invokedExpression, mergedArguments);
    }
  }

  /**
   * Assuming that qualifier is not processed yet.
   */
  private void fallback(@Nullable GrExpression invoked, @NotNull Arguments arguments) {
    if (invoked != null) {
      invoked.accept(myAnalyzer); // qualifier
      myAnalyzer.addInstruction(new GrDereferenceInstruction<V>(invoked)); // dereference qualifier
    }

    final int argumentsCount = arguments.runArguments();
    for (int i = 0; i < argumentsCount; i++) {
      myAnalyzer.pop();
    }

    myAnalyzer.pushUnknown(); // method call result
  }
}
