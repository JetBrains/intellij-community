/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NotNull;

public class LongLiteralsEndingWithLowercaseLInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "LongLiteralEndingWithLowercaseL";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "long.literals.ending.with.lowercase.l.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "long.literals.ending.with.lowercase.l.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new LongLiteralWithLowercaseLVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new LongLiteralFix();
  }

  private static class LongLiteralFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "long.literals.ending.with.lowercase.l.replace.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiExpression literal =
        (PsiExpression)descriptor.getPsiElement();
      final String text = literal.getText();
      final String newText = text.replace('l', 'L');
      PsiReplacementUtil.replaceExpression(literal, newText);
    }
  }

  private static class LongLiteralWithLowercaseLVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(
      @NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!type.equals(PsiType.LONG)) {
        return;
      }
      final String text = expression.getText();
      if (text == null) {
        return;
      }
      final int length = text.length();
      if (length == 0) {
        return;
      }
      if (text.charAt(length - 1) != 'l') {
        return;
      }
      registerError(expression);
    }
  }
}