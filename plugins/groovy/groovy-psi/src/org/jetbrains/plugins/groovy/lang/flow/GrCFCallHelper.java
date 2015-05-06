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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.JavaStylePropertiesUtil;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mOPTIONAL_DOT;

public class GrCFCallHelper<V extends GrInstructionVisitor<V>> {

  public interface Arguments {
    @NotNull
    GrNamedArgument[] getNamedArguments();

    @NotNull
    GrExpression[] getExpressionArguments();

    @NotNull
    GrClosableBlock[] getClosureArguments();

    int runArguments();
  }

  public abstract class ArgumentsBase implements Arguments {

    @NotNull
    @Override
    public GrNamedArgument[] getNamedArguments() {
      return GrNamedArgument.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public GrClosableBlock[] getClosureArguments() {
      return GrClosableBlock.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public GrExpression[] getExpressionArguments() {
      return GrExpression.EMPTY_ARRAY;
    }

    @Override
    public int runArguments() {
      return visitArguments(getNamedArguments(), getExpressionArguments(), getClosureArguments());
    }
  }

  public class CallBasedArguments extends ArgumentsBase {

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

  public final Arguments EMPTY = new ArgumentsBase() {
    @Override
    public int runArguments() {
      return 0;
    }
  };

  private final GrControlFlowAnalyzerImpl<V> myAnalyzer;

  public GrCFCallHelper(GrControlFlowAnalyzerImpl<V> analyzer) {
    myAnalyzer = analyzer;
  }

  void processMethodCall(@NotNull GrMethodCall methodCall) {
    processMethodCall(methodCall.getInvokedExpression(), methodCall);
  }

  private void processMethodCall(@NotNull GrExpression invokedExpression, @NotNull GrMethodCall call) {
    if (invokedExpression instanceof GrReferenceExpression) {
      final GrReferenceExpression reference = (GrReferenceExpression)invokedExpression;
      if (JavaStylePropertiesUtil.isPropertyAccessor(call)) {
        // getter call
        // a.getStuff()
        processMethodCall(call, reference, new CallBasedArguments(call));
      }
      else {
        final GroovyResolveResult result = reference.advancedResolve();
        final PsiElement element = result.getElement();
        if (element instanceof PsiParameter || element instanceof PsiMethod && PropertyUtil.isSimplePropertyAccessor((PsiMethod)element)) {
          // callable parameter call
          // a()
          // callable property call
          // a.stuff() (same as a.getStuff()() or a.getStuff().call() or a.stuff.call())
          processCallableCall(call);
        }
        else {
          // simple method call
          // a.foo()
          processMethodCall(call, reference, new CallBasedArguments(call));
        }
      }
    }
    else {
      // closure call 
      // ({-> "s"})()
      processCallableCall(call);
    }
  }

  private void processCallableCall(@NotNull GrMethodCall call) {
    final GrExpression invoked = call.getInvokedExpression();
    final PsiType type = invoked.getType();
    if (type != null) {
      final GrArgumentList argumentList = call.getArgumentList();
      final GroovyResolveResult[] resolveResults = ResolveUtil.getMethodCandidates(type, "call", call, argumentList.getExpressionTypes());
      if (resolveResults.length == 1) {
        final GroovyResolveResult result = resolveResults[0];
        if (result.isValidResult()) {
          invoked.accept(myAnalyzer);
          processMethodCallStraight(call, result, new CallBasedArguments(call));
          return;
        }
      }
    }
    fallback(invoked, new CallBasedArguments(call));
  }

  void processMethodCall(@NotNull GrExpression highlight,
                         @NotNull GrReferenceExpression invokedReference,
                         @NotNull Arguments arguments) {
    final GroovyResolveResult result = invokedReference.advancedResolve();
    final GrExpression qualifier = invokedReference.getQualifierExpression();
    if (qualifier != null) {
      qualifier.accept(myAnalyzer);
    }
    else {
      myAnalyzer.pushUnknown();
    }
    if (invokedReference.getDotTokenType() == mOPTIONAL_DOT) {
      myAnalyzer.addInstruction(new DupInstruction<V>()); // save qualifier for later use
      myAnalyzer.pushNull();
      myAnalyzer.addInstruction(new BinopInstruction(DfaRelation.NE, invokedReference));
      final ConditionalGotoInstruction gotoToNotNull = myAnalyzer.addInstruction(new ConditionalGotoInstruction(null, true, qualifier));

      // not null branch
      // qualifier is on top of stack
      processMethodCallStraight(highlight, result, arguments);

      final GotoInstruction<V> gotoEnd = myAnalyzer.addInstruction(new GotoInstruction<V>(null));
      gotoToNotNull.setOffset(myAnalyzer.flow.getNextOffset());

      // null branch
      // even if qualifier is null, groovy evaluates arguments
      final int argumentsToPop = arguments.runArguments();
      for (int i = 0; i < argumentsToPop; i++) {
        myAnalyzer.pop();
      }
      myAnalyzer.pop();        // pop duplicated qualifier
      myAnalyzer.pushNull();
      gotoEnd.setOffset(myAnalyzer.flow.getNextOffset());
    }
    else {
      processMethodCallStraight(highlight, result, arguments);
    }
  }

  /**
   * Assuming that qualifier is already processed
   */
  void processMethodCallStraight(@NotNull GrExpression highlight,
                                 @NotNull GroovyResolveResult result,
                                 @NotNull final GrExpression... expressionArguments) {
    processMethodCallStraight(
      highlight,
      result,
      new ArgumentsBase() {
        @NotNull
        @Override
        public GrExpression[] getExpressionArguments() {
          return expressionArguments;
        }
      }
    );
  }

  /**
   * Assuming that qualifier is already processed
   * This is where actual instructions are being added.
   */
  void processMethodCallStraight(@NotNull GrExpression highlight,
                                 @NotNull GroovyResolveResult result,
                                 @NotNull Arguments arguments) {
    // evaluate arguments
    //visitArguments(namedArguments, expressionArguments, closureArguments);
    arguments.runArguments();
    myAnalyzer.exceptionHelper.addConditionalRuntimeThrow();
    myAnalyzer.addInstruction(new GrMethodCallInstruction<V>(
      highlight,
      arguments.getNamedArguments(),
      arguments.getExpressionArguments(),
      arguments.getClosureArguments(),
      result
    ));
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
    final ArgumentsBase mergedArguments = new ArgumentsBase() {
      @NotNull
      @Override
      public GrExpression[] getExpressionArguments() {
        return ArrayUtil.mergeArrays(indexProperty.getExpressionArguments(), arguments.getExpressionArguments());
      }

      @Override
      public int runArguments() {
        for (GrExpression arg : indexProperty.getExpressionArguments()) {
          arg.accept(myAnalyzer);
        }
        return indexProperty.getExpressionArguments().length + arguments.runArguments();
      }
    };
    if (results.length == 1 && results[0].isValidResult()) {
      invokedExpression.accept(myAnalyzer); // qualifier
      processMethodCallStraight(indexProperty, results[0], mergedArguments);
    }
    else {
      fallback(invokedExpression, mergedArguments);
    }
  }

  private void fallback(@NotNull GrExpression invoked, @NotNull Arguments arguments) {
    invoked.accept(myAnalyzer); // qualifier
    myAnalyzer.addInstruction(new GrDereferenceInstruction<V>(invoked)); // dereference qualifier

    final int argumentsCount = arguments.runArguments();
    for (int i = 0; i < argumentsCount; i++) {
      myAnalyzer.pop();
    }

    myAnalyzer.pushUnknown(); // method call result
  }
}
