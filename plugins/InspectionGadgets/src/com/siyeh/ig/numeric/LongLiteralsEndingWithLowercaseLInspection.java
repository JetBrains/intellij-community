/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class LongLiteralsEndingWithLowercaseLInspection extends ExpressionInspection {

  private final LongLiteralFix fix = new LongLiteralFix();

  public String getID() {
    return "LongLiteralEndingWithLowercaseL";
  }

  public String getGroupDisplayName() {
    return GroupNames.NUMERIC_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new LongLiteralWithLowercaseLVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class LongLiteralFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("long.literals.ending.with.lowercase.l.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiExpression literal = (PsiExpression)descriptor.getPsiElement();
      final String text = literal.getText();
      final String newText = text.replace('l', 'L');
      replaceExpression(literal, newText);
    }
  }

  private static class LongLiteralWithLowercaseLVisitor extends BaseInspectionVisitor {

    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
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
