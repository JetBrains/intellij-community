/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ImplicitCallToSuperInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreForObjectSubclasses = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "implicit.call.to.super.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "implicit.call.to.super.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new AddExplicitSuperCall();
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "implicit.call.to.super.ignore.option"),
                                          this, "m_ignoreForObjectSubclasses");
  }

  private static class AddExplicitSuperCall extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "implicit.call.to.super.make.explicit.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement methodName = descriptor.getPsiElement();
      final PsiElement parent = methodName.getParent();
      if (!(parent instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)parent;
      final PsiCodeBlock body = method.getBody();
      final PsiElementFactory factory =
        JavaPsiFacade.getElementFactory(project);
      final PsiStatement newStatement =
        factory.createStatementFromText("super();", null);
      final CodeStyleManager styleManager =
        CodeStyleManager.getInstance(project);
      if (body == null) {
        return;
      }
      final PsiJavaToken brace = body.getLBrace();
      body.addAfter(newStatement, brace);
      styleManager.reformat(body);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ImplicitCallToSuperVisitor();
  }

  private class ImplicitCallToSuperVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!method.isConstructor() || method.getNameIdentifier() == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isEnum()) {
        return;
      }
      if (m_ignoreForObjectSubclasses) {
        final PsiClass superClass = containingClass.getSuperClass();
        if (superClass != null) {
          final String superClassName = superClass.getQualifiedName();
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(
            superClassName)) {
            return;
          }
        }
      }
      final PsiStatement firstStatement = ControlFlowUtils.getFirstStatementInBlock(method.getBody());
      if (firstStatement == null) {
        registerMethodError(method);
        return;
      }
      if (isConstructorCall(firstStatement) || PsiUtilCore.hasErrorElementChild(firstStatement)) {
        return;
      }
      registerMethodError(method);
    }

    private boolean isConstructorCall(PsiStatement statement) {
      if (!(statement instanceof PsiExpressionStatement)) {
        return false;
      }
      final PsiExpressionStatement expressionStatement =
        (PsiExpressionStatement)statement;
      final PsiExpression expression =
        expressionStatement.getExpression();
      return ExpressionUtils.isConstructorInvocation(expression);
    }
  }
}
