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
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UnqualifiedStaticUsageInspection extends BaseInspection implements CleanupLocalInspectionTool {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticFieldAccesses = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticMethodCalls = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreStaticAccessFromStaticContext = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unqualified.static.usage.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    if (infos[0] instanceof PsiMethodCallExpression) {
      return InspectionGadgetsBundle.message(
        "unqualified.static.usage.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "unqualified.static.usage.problem.descriptor1");
    }
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "unqualified.static.usage.ignore.field.option"),
                             "m_ignoreStaticFieldAccesses");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "unqualified.static.usage.ignore.method.option"),
                             "m_ignoreStaticMethodCalls");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "unqualified,static.usage.only.report.static.usages.option"),
                             "m_ignoreStaticAccessFromStaticContext");
    return optionsPanel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedStaticCallVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    if (infos[0] instanceof PsiMethodCallExpression) {
      return new UnqualifiedStaticAccessFix(false);
    }
    else {
      return new UnqualifiedStaticAccessFix(true);
    }
  }

  private static class UnqualifiedStaticAccessFix
    extends InspectionGadgetsFix {

    private final boolean m_fixField;

    UnqualifiedStaticAccessFix(boolean fixField) {
      m_fixField = fixField;
    }

    @Override
    @NotNull
    public String getName() {
      if (m_fixField) {
        return InspectionGadgetsBundle.message(
          "unqualified.static.usage.qualify.field.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message(
          "unqualified.static.usage.qualify.method.quickfix");
      }
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Qualify static access";
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiReferenceExpression expression =
        (PsiReferenceExpression)descriptor.getPsiElement();
      final PsiMember member = (PsiMember)expression.resolve();
      assert member != null;
      final PsiClass containingClass = member.getContainingClass();
      assert containingClass != null;
      final String className = containingClass.getName();
      CommentTracker commentTracker = new CommentTracker();
      PsiReplacementUtil.replaceExpression(expression, className + '.' + commentTracker.text(expression), commentTracker);
    }
  }

  private class UnqualifiedStaticCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (m_ignoreStaticMethodCalls) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      if (!isUnqualifiedStaticAccess(methodExpression)) {
        return;
      }
      registerError(methodExpression, expression);
    }

    @Override
    public void visitReferenceExpression(
      @NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (m_ignoreStaticFieldAccesses) {
        return;
      }
      final PsiElement element = expression.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)element;
      if (field.hasModifierProperty(PsiModifier.FINAL) &&
          PsiUtil.isOnAssignmentLeftHand(expression)) {
        return;
      }
      if (!isUnqualifiedStaticAccess(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    private boolean isUnqualifiedStaticAccess(
      PsiReferenceExpression expression) {
      if (m_ignoreStaticAccessFromStaticContext) {
        final PsiMember member =
          PsiTreeUtil.getParentOfType(expression,
                                      PsiMember.class);
        if (member != null &&
            member.hasModifierProperty(PsiModifier.STATIC)) {
          return false;
        }
      }
      final PsiExpression qualifierExpression =
        expression.getQualifierExpression();
      if (qualifierExpression != null) {
        return false;
      }
      final JavaResolveResult resolveResult =
        expression.advancedResolve(false);
      final PsiElement currentFileResolveScope =
        resolveResult.getCurrentFileResolveScope();
      if (currentFileResolveScope instanceof PsiImportStaticStatement) {
        return false;
      }
      final PsiElement element = resolveResult.getElement();
      if (!(element instanceof PsiField) &&
          !(element instanceof PsiMethod)) {
        return false;
      }
      final PsiMember member = (PsiMember)element;
      if (member instanceof PsiEnumConstant &&
          expression.getParent() instanceof PsiSwitchLabelStatement) {
        return false;
      }
      return member.hasModifierProperty(PsiModifier.STATIC);
    }
  }
}
