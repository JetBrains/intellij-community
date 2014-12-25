/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ReturnOfDateFieldInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignorePrivateMethods = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("return.date.calendar.field.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String type = (String)infos[0];
    return InspectionGadgetsBundle.message("return.date.calendar.field.problem.descriptor", type);
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("return.of.null.ignore.private.option"),
                                          this, "ignorePrivateMethods");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ReturnOfDateFieldFix((String)infos[0]);
  }

  private static class ReturnOfDateFieldFix extends InspectionGadgetsFix {

    private final String myType;

    public ReturnOfDateFieldFix(String type) {
      myType = type;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("return.date.calendar.field.quickfix", myType);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Return clone";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
      final String type =
        TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_DATE, CommonClassNames.JAVA_UTIL_CALENDAR);
      if (type == null) {
        return;
      }
      PsiReplacementUtil.replaceExpression(referenceExpression, '(' + type + ')' + referenceExpression.getText() + ".clone()");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnOfDateFieldVisitor();
  }

  private class ReturnOfDateFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression returnValue = statement.getReturnValue();
      if (!(returnValue instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement containingElement = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class, PsiClass.class);
      if (containingElement == null || containingElement instanceof PsiClass || 
          (containingElement instanceof PsiMethod && ignorePrivateMethods && ((PsiMethod)containingElement).hasModifierProperty(PsiModifier.PRIVATE))) {
        return;
      }
      final PsiReferenceExpression fieldReference = (PsiReferenceExpression)returnValue;
      final PsiElement element = fieldReference.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final String type = TypeUtils.expressionHasTypeOrSubtype(
        returnValue, CommonClassNames.JAVA_UTIL_DATE, CommonClassNames.JAVA_UTIL_CALENDAR);
      if (type == null) {
        return;
      }
      registerError(returnValue, type);
    }
  }
}