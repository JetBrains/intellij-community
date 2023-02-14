/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ipp.forloop;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReplaceForEachLoopWithIteratorForLoopIntention extends Intention {

  @Override
  public @NotNull String getFamilyName() {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.iterator.for.loop.intention.family.name");
  }

  @Override
  public @NotNull String getText() {
    return IntentionPowerPackBundle.message("replace.for.each.loop.with.iterator.for.loop.intention.name");
  }

  @Override
  @NotNull
  public PsiElementPredicate getElementPredicate() {
    return new IterableForEachLoopPredicate();
  }

  @Override
  public void processIntention(@NotNull PsiElement element) {
    final PsiForeachStatement statement = (PsiForeachStatement)element.getParent();
    if (statement == null) {
      return;
    }
    final PsiExpression iteratedValue = statement.getIteratedValue();
    if (iteratedValue == null) {
      return;
    }
    final PsiType iteratedValueType = iteratedValue.getType();
    if (!(iteratedValueType instanceof PsiClassType)) {
      return;
    }
    CommentTracker tracker = new CommentTracker();
    final String methodCall = tracker.text(iteratedValue, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + ".iterator()";
    final Project project = statement.getProject();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiExpression iteratorCall = factory.createExpressionFromText(methodCall, iteratedValue);
    final PsiType variableType = GenericsUtil.getVariableTypeByExpressionType(iteratorCall.getType());
    if (variableType == null) {
      return;
    }
    @NonNls final StringBuilder newStatement = new StringBuilder();
    newStatement.append("for(").append(variableType.getCanonicalText()).append(' ');
    final String iterator = new VariableNameGenerator(statement, VariableKind.LOCAL_VARIABLE)
      .byName("iterator", "iter", "it").generate(true);
    newStatement.append(iterator).append("=").append(iteratorCall.getText()).append(';');
    newStatement.append(iterator).append(".hasNext();) {");
    if (JavaCodeStyleSettings.getInstance(statement.getContainingFile()).GENERATE_FINAL_LOCALS) {
      newStatement.append("final ");
    }
    final PsiParameter iterationParameter = statement.getIterationParameter();
    final PsiTypeElement parameterType = iterationParameter.getTypeElement();
    final String typeText = parameterType == null ? iterationParameter.getType().getCanonicalText() : parameterType.getText();
    newStatement.append(typeText).append(' ').append(iterationParameter.getName()).append(" = ").append(iterator).append(".next();");
    final PsiStatement body = statement.getBody();
    if (body == null) {
      return;
    }
    if (body instanceof PsiBlockStatement) {
      final PsiCodeBlock block = ((PsiBlockStatement)body).getCodeBlock();
      final PsiElement[] children = block.getChildren();
      for (int i = 1; i < children.length - 1; i++) {
        //skip the braces
        newStatement.append(tracker.text(children[i]));
      }
    }
    else {
      newStatement.append(tracker.text(body));
    }
    newStatement.append('}');

    PsiReplacementUtil.replaceStatementAndShortenClassNames(statement, newStatement.toString(), tracker);
  }
}