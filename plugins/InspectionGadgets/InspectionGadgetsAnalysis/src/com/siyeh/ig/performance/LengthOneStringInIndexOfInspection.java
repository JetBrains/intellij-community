/*
 * Copyright 2006-2018 Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LengthOneStringInIndexOfInspection
  extends BaseInspection {

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "SingleCharacterStringConcatenation";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiExpression literal = (PsiExpression)infos[0];
    final String replacement = getReplacement(literal);
    return InspectionGadgetsBundle.message("expression.can.be.replaced.no.quotes.problem.descriptor", literal.getText(), replacement);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LengthOneStringsInIndexOfVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ReplaceStringsWithCharsFix();
  }

  private static class ReplaceStringsWithCharsFix
    extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "length.one.strings.in.concatenation.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      final String charLiteral = getReplacement(expression);
      PsiReplacementUtil.replaceExpression(expression, charLiteral);
    }
  }

  @NotNull
  private static String getReplacement(PsiExpression expression) {
    final String text = expression.getText();
    final int length = text.length();
    final String character = text.substring(1, length - 1);
    switch (character) {
      case "\'":
        return "'\\''";
      case "\\\"":
        return "'\"'";
      default:
        return '\'' + character + '\'';
    }
  }

  private static class LengthOneStringsInIndexOfVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(
      @NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      final String value = (String)expression.getValue();
      if (value == null || value.length() != 1) {
        return;
      }
      if (!isArgumentOfIndexOf(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    static boolean isArgumentOfIndexOf(PsiExpression expression) {
      final PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
      if (parent == null) {
        return false;
      }
      if (!(parent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiElement grandparent = parent.getParent();
      if (!(grandparent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression call =
        (PsiMethodCallExpression)grandparent;
      final PsiReferenceExpression methodExpression =
        call.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.INDEX_OF.equals(name) &&
          !HardcodedMethodConstants.LAST_INDEX_OF.equals(name)) {
        return false;
      }
      final PsiMethod method = call.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null) {
        return false;
      }
      final String className = methodClass.getQualifiedName();
      return CommonClassNames.JAVA_LANG_STRING.equals(className);
    }
  }
}