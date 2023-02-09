/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.codeInspection.options.OptPane.*;

public class UnnecessaryThisInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings("PublicField")
  public boolean ignoreAssignments = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.this.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreAssignments", InspectionGadgetsBundle.message("unnecessary.this.ignore.assignments.option")));
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryThisFix();
  }

  private static class UnnecessaryThisFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.this.remove.quickfix");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement thisToken = descriptor.getPsiElement();
      final PsiElement thisParenthesized = findBiggestParenthesizedExpr(thisToken);
      new CommentTracker().deleteAndRestoreComments(thisParenthesized);
    }
  }

  private static PsiElement findBiggestParenthesizedExpr(@NotNull PsiElement element){
    PsiElement current = element;
    PsiElement parent = element.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      current = parent;
      parent = current.getParent();
    }
    return current;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryThisVisitor();
  }

  private class UnnecessaryThisVisitor extends BaseInspectionVisitor {

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression expression) {
      super.visitThisExpression(expression);
      PsiElement parenthesizedThis = findBiggestParenthesizedExpr(expression);
      if (parenthesizedThis.getParent() instanceof PsiNewExpression) {
        final PsiReference qualifier = expression.getQualifier();
        if (qualifier == null || qualifier.resolve() == PsiTreeUtil.getParentOfType(expression, PsiClass.class)) {
          registerError(expression);
        }
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      final PsiReferenceParameterList parameterList = expression.getParameterList();
      if (parameterList == null) {
        return;
      }
      if (parameterList.getTypeArguments().length > 0) {
        return;
      }
      final PsiExpression qualifierExpression = PsiUtil.skipParenthesizedExprDown(expression.getQualifierExpression());
      if (!(qualifierExpression instanceof PsiThisExpression thisExpression)) {
        return;
      }
      final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
      final String referenceName = expression.getReferenceName();
      if (referenceName == null) {
        return;
      }
      if (ignoreAssignments && PsiUtil.isAccessedForWriting(expression)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (qualifier == null) {
        if (referenceName.equals(PsiKeyword.YIELD) && parent instanceof PsiMethodCallExpression) {
          // Qualifier might be required since Java 14, so don't warn
          return;
        }
        if (parent instanceof PsiCallExpression) {
          // method calls are always in error
          registerError(qualifierExpression);
          return;
        }
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiVariable variable)) {
          return;
        }
        if (!DeclarationSearchUtils.variableNameResolvesToTarget(referenceName, variable, expression)) {
          return;
        }
        if (variable instanceof PsiField && HighlightUtil.isIllegalForwardReferenceToField(expression, (PsiField)variable, true) != null) {
          return;
        }
        registerError(thisExpression);
      }
      else {
        final String qualifierName = qualifier.getReferenceName();
        if (qualifierName == null) {
          return;
        }
        if (parent instanceof PsiCallExpression callExpression) {
          final PsiMethod calledMethod = callExpression.resolveMethod();
          if (calledMethod == null) {
            return;
          }
          final String methodName = calledMethod.getName();
          PsiClass parentClass = ClassUtils.getContainingClass(expression);
          final Project project = expression.getProject();
          final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
          final PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();
          while (parentClass != null) {
            if (qualifierName.equals(parentClass.getName())) {
              registerError(thisExpression);
            }
            final PsiMethod[] methods = parentClass.findMethodsByName(methodName, true);
            for (PsiMethod method : methods) {
              final PsiClass containingClass = method.getContainingClass();
              if (resolveHelper.isAccessible(method, expression, null)) {
                if (method.hasModifierProperty(PsiModifier.PRIVATE) && !PsiTreeUtil.isAncestor(containingClass, expression, true)) {
                  continue;
                }
                return;
              }
            }
            parentClass = ClassUtils.getContainingClass(parentClass);
          }
        }
        else {
          final PsiElement target = expression.resolve();
          if (!(target instanceof PsiVariable variable)) {
            return;
          }
          if (!DeclarationSearchUtils.variableNameResolvesToTarget(referenceName, variable, expression)) {
            return;
          }
          PsiClass parentClass = ClassUtils.getContainingClass(expression);
          while (parentClass != null) {
            if (qualifierName.equals(parentClass.getName())) {
              registerError(thisExpression);
            }
            final PsiField field = parentClass.findFieldByName(referenceName, true);
            if (field != null) {
              return;
            }
            parentClass = ClassUtils.getContainingClass(parentClass);
          }
        }
      }
    }
  }
}