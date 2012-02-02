/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.intentions.control;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.base.ErrorUtil;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.IntentionUtils;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * @author ilyas
 */
public class ConditionalToElvisIntention extends Intention {
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    final GrConditionalExpression expr = (GrConditionalExpression)element;

    final GrExpression condition = expr.getCondition();
    final GrExpression thenExpression = expr.getThenBranch();
    final GrExpression elseExpression = expr.getElseBranch();
    assert elseExpression != null;
    assert thenExpression != null;

    final String newExpression;
    if (checkForStringIsEmpty(condition, elseExpression) || checkForListIsEmpty(condition, elseExpression)) {
      newExpression = elseExpression.getText() + " ?: " + thenExpression.getText();
    }
    else {
      newExpression = thenExpression.getText() + " ?: " + elseExpression.getText();
    }
    IntentionUtils.replaceExpression(newExpression, expr);
  }

  @NotNull
  protected PsiElementPredicate getElementPredicate() {
    return new ConditionalToElvisPredicate();
  }

  /**
   * @author ilyas
   */
  private static class ConditionalToElvisPredicate implements PsiElementPredicate {
    public boolean satisfiedBy(PsiElement element) {
      if (!(element instanceof GrConditionalExpression) || ErrorUtil.containsError(element)) return false;

      GrConditionalExpression expr = (GrConditionalExpression)element;
      GrExpression condition = expr.getCondition();

      final GrExpression then = expr.getThenBranch();
      final GrExpression elseBranch = expr.getElseBranch();
      if (then == null || elseBranch == null) return false;

      if (PsiEquivalenceUtil.areElementsEquivalent(condition, then)) return true;

      return checkForNull(condition, then) ||
             checkForStringIsEmpty(condition, elseBranch) ||
             checkForStringIsNotEmpty(condition, then) ||
             checkForListIsEmpty(condition, elseBranch) ||
             checkForListIsNotEmpty(condition, then);
    }
  }

  private static boolean checkForListIsNotEmpty(GrExpression condition, GrExpression then) {
    if (!(condition instanceof GrUnaryExpression)) return false;

    if (((GrUnaryExpression)condition).getOperationTokenType() != GroovyTokenTypes.mLNOT) return false;

    return checkForListIsEmpty(((GrUnaryExpression)condition).getOperand(), then);
  }

  private static boolean checkForListIsEmpty(GrExpression condition, GrExpression elseBranch) {
    if (condition instanceof GrMethodCall) condition = ((GrMethodCall)condition).getInvokedExpression();
    if (!(condition instanceof GrReferenceExpression)) return false;

    final GrExpression qualifier = ((GrReferenceExpression)condition).getQualifier();
    if (qualifier == null) return false;

    if (!PsiEquivalenceUtil.areElementsEquivalent(qualifier, elseBranch)) return false;

    final PsiType type = qualifier.getType();
    if (type == null) return false;
    if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_LIST)) return false;

    final PsiElement resolved = ((GrReferenceExpression)condition).resolve();

    return resolved instanceof PsiMethod &&
           "isEmpty".equals(((PsiMethod)resolved).getName()) &&
           ((PsiMethod)resolved).getParameterList().getParametersCount() == 0;
  }

  /**
   * checks for the case !string.isEmpty ? string : something_else
   */
  private static boolean checkForStringIsNotEmpty(GrExpression condition, GrExpression then) {
    if (!(condition instanceof GrUnaryExpression)) return false;

    if (((GrUnaryExpression)condition).getOperationTokenType() != GroovyTokenTypes.mLNOT) return false;

    return checkForStringIsEmpty(((GrUnaryExpression)condition).getOperand(), then);
  }

  /**
   * checks for the case string.isEmpty() ? something_else : string
   */
  private static boolean checkForStringIsEmpty(GrExpression condition, GrExpression elseBranch) {
    if (condition instanceof GrMethodCall) condition = ((GrMethodCall)condition).getInvokedExpression();
    if (!(condition instanceof GrReferenceExpression)) return false;

    final GrExpression qualifier = ((GrReferenceExpression)condition).getQualifier();
    if (qualifier == null) return false;

    if (!PsiEquivalenceUtil.areElementsEquivalent(qualifier, elseBranch)) return false;

    final PsiType type = qualifier.getType();
    if (type == null) return false;
    if (!type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return false;

    final PsiElement resolved = ((GrReferenceExpression)condition).resolve();

    return resolved instanceof PsiMethod &&
           "isEmpty".equals(((PsiMethod)resolved).getName()) &&
           ((PsiMethod)resolved).getParameterList().getParametersCount() == 0;
  }

  private static boolean checkForNull(GrExpression condition, GrExpression then) {
    if (!(condition instanceof GrBinaryExpression)) return false;

    GrBinaryExpression binaryExpression = (GrBinaryExpression)condition;
    if (GroovyTokenTypes.mNOT_EQUAL != binaryExpression.getOperationTokenType()) return false;

    GrExpression left = binaryExpression.getLeftOperand();
    GrExpression right = binaryExpression.getRightOperand();
    if (left instanceof GrLiteral && "null".equals(left.getText()) && right != null) {
      return PsiEquivalenceUtil.areElementsEquivalent(right, then);
    }
    if (right instanceof GrLiteral && "null".equals(right.getText())) {
      return PsiEquivalenceUtil.areElementsEquivalent(left, then);
    }

    return false;
  }
}

