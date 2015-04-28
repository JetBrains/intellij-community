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

import com.intellij.codeInspection.dataFlow.IControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.instructions.BinopInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ConditionalGotoInstruction;
import com.intellij.codeInspection.dataFlow.instructions.DupInstruction;
import com.intellij.codeInspection.dataFlow.instructions.GotoInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaRelation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrInstructionVisitor;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrMethodCallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mOPTIONAL_DOT;

public class GrControlFlowCallHelper<V extends GrInstructionVisitor<V>> {

  private final GrControlFlowAnalyzerImpl<V> myAnalyzer;
  private final PsiType myClosureType;

  public GrControlFlowCallHelper(GrControlFlowAnalyzerImpl<V> analyzer, @NotNull PsiElement block) {
    myAnalyzer = analyzer;
    myClosureType = TypesUtil.createType("groovy.lang.Closure", block);
  }

  void processMethodCall(@NotNull GrMethodCall methodCall) {
    // qualifier
    final GrExpression invokedExpression = methodCall.getInvokedExpression();
    final PsiType invokedExpressionType = invokedExpression.getType();
    final PsiClass psiClass = PsiTypesUtil.getPsiClass(invokedExpressionType);
    if (myClosureType.equals(invokedExpressionType) ||
        psiClass != null && PsiClassImplUtil.findMethodsByName(psiClass, "call", true).length > 0) {
      // TODO
      throw new IControlFlowAnalyzer.CannotAnalyzeException();
    }
    else {
      processMethodCall(
        methodCall,
        (GrReferenceExpression)invokedExpression,
        methodCall.getNamedArguments(),
        methodCall.getExpressionArguments(),
        methodCall.getClosureArguments()
      );
    }
  }

  void processMethodCall(@NotNull GrExpression call,
                         @NotNull GrReferenceExpression invokedReference,
                         @NotNull GrExpression... expressionArguments) {
    processMethodCall(call, invokedReference, GrNamedArgument.EMPTY_ARRAY, expressionArguments, GrClosableBlock.EMPTY_ARRAY);
  }

  private void processMethodCall(@NotNull GrExpression call,
                                 @NotNull GrReferenceExpression invokedReference,
                                 @NotNull GrNamedArgument[] namedArguments,
                                 @NotNull GrExpression[] expressionArguments,
                                 @NotNull GrClosableBlock[] closureArguments) {

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
      myAnalyzer.addInstruction(new BinopInstruction(DfaRelation.EQ, invokedReference));
      final ConditionalGotoInstruction gotoToNotNull = myAnalyzer.addInstruction(new ConditionalGotoInstruction(null, true, qualifier));

      // null branch
      // even if qualifier is null, groovy evaluates arguments
      final int argumentsToPop = visitArguments(namedArguments, expressionArguments, closureArguments);
      for (int i = 0; i < argumentsToPop; i++) {
        myAnalyzer.pop();
      }
      myAnalyzer.pop();        // pop duplicated qualifier
      myAnalyzer.pushNull();
      final GotoInstruction<V> gotoEnd = myAnalyzer.addInstruction(new GotoInstruction<V>(null));

      // not null branch
      gotoToNotNull.setOffset(myAnalyzer.myFlow.getNextOffset());
      // qualifier is on top of stack
      processMethodCallStraight(call, result, namedArguments, expressionArguments, closureArguments);
      gotoEnd.setOffset(myAnalyzer.myFlow.getNextOffset());
    }
    else {
      processMethodCallStraight(call, result, namedArguments, expressionArguments, closureArguments);
    }
  }

  void processMethodCallStraight(@NotNull GrExpression call,
                                 @NotNull GroovyResolveResult result,
                                 @Nullable GrExpression... expressionArguments) {
    processMethodCallStraight(
      call,
      result,
      GrNamedArgument.EMPTY_ARRAY,
      expressionArguments == null ? GrExpression.EMPTY_ARRAY : expressionArguments,
      GrClosableBlock.EMPTY_ARRAY
    );
  }

  private void processMethodCallStraight(@NotNull GrExpression call,
                                         @NotNull GroovyResolveResult result,
                                         @NotNull GrNamedArgument[] namedArguments,
                                         @NotNull GrExpression[] expressionArguments,
                                         @NotNull GrClosableBlock[] closureArguments) {
    // evaluate arguments
    visitArguments(namedArguments, expressionArguments, closureArguments);
    myAnalyzer.addInstruction(new GrMethodCallInstruction<V>(call, namedArguments, expressionArguments, closureArguments, result));
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
      myAnalyzer.push(myAnalyzer.myFactory.createTypeValue(TypesUtil.createType("java.util.Map", namedArguments[0]), Nullness.NOT_NULL));
      counter++;
    }
    for (GrExpression expression : expressionArguments) {
      expression.accept(myAnalyzer);
      counter++;
    }
    for (GrClosableBlock block : closureArguments) {
      myAnalyzer.push(myAnalyzer.myFactory.createValue(block), block);
      counter++;
    }
    return counter;
  }
}
