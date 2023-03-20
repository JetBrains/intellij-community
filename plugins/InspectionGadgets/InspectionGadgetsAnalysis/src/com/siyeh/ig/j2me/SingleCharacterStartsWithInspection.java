/*
 * Copyright 2003-2020 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SingleCharacterStartsWithInspection extends BaseInspection {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "single.character.startswith.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SingleCharacterStartsWithFix();
  }

  private static class SingleCharacterStartsWithFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "single.character.startswith.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression = (PsiReferenceExpression)element.getParent();
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)methodExpression.getParent();
      final PsiElement qualifier = methodExpression.getQualifier();
      if (qualifier == null) {
        return;
      }
      final PsiExpressionList argumentList = methodCall.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      final PsiExpression expression = expressions[0];
      final String expressionText = expression.getText();
      String character = expressionText.substring(1, expressionText.length() - 1);
      if (character.equals("'")) {
        character = "\\'";
      }
      final String qualifierText = qualifier.getText();
      @NonNls final StringBuilder newExpression = new StringBuilder();
      final PsiClass stringClass = ClassUtils.findClass(CommonClassNames.JAVA_LANG_STRING, qualifier);
      // String.isEmpty() was introduced in Java 6
      final PsiMethod[] isEmptyMethod = (stringClass == null) ? PsiMethod.EMPTY_ARRAY : stringClass.findMethodsByName("isEmpty", false);
      if (isEmptyMethod.length > 0) {
        newExpression.append("!").append(qualifierText).append(".isEmpty() && ");
      }
      else {
        newExpression.append(qualifierText).append(".length() > 0 && ");
      }
      final String referenceName = methodExpression.getReferenceName();
      newExpression.append(qualifierText).append(".charAt(");
      if (HardcodedMethodConstants.STARTS_WITH.equals(referenceName)) {
        newExpression.append("0) == '");
      }
      else {
        newExpression.append(qualifierText).append(".length() - 1) == '");
      }
      newExpression.append(character).append('\'');
      final CommentTracker commentTracker = new CommentTracker();
      commentTracker.markUnchanged(qualifier);
      PsiReplacementUtil.replaceExpression(methodCall, newExpression.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SingleCharacterStartsWithVisitor();
  }

  private static class SingleCharacterStartsWithVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      super.visitMethodCallExpression(call);
      final PsiReferenceExpression methodExpression = call.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.STARTS_WITH.equals(methodName) &&
          !HardcodedMethodConstants.ENDS_WITH.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = call.getArgumentList();
      final PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 1 && args.length != 2) {
        return;
      }
      if (!isSingleCharacterStringLiteral(args[0])) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      registerMethodCallError(call);
    }

    private static boolean isSingleCharacterStringLiteral(PsiExpression arg) {
      final PsiType type = arg.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return false;
      }
      if (!(arg instanceof PsiLiteralExpression literal)) {
        return false;
      }
      final String value = (String)literal.getValue();
      return value != null && value.length() == 1;
    }
  }
}