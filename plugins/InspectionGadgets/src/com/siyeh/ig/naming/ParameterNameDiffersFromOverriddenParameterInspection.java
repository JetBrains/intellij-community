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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ParameterNameDiffersFromOverriddenParameterInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreSingleCharacterNames = false;
  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreOverridesOfLibraryMethods = false;

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel = new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("parameter.name.differs.from.overridden.parameter.ignore.character.option"),
                             "m_ignoreSingleCharacterNames");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message("parameter.name.differs.from.overridden.parameter.ignore.library.option"),
                             "m_ignoreOverridesOfLibraryMethods");
    return optionsPanel;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix((String)infos[0], false, false);
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("parameter.name.differs.from.overridden.parameter.problem.descriptor", infos[0], infos[1]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ParameterNameDiffersFromOverriddenParameterVisitor();
  }

  private class ParameterNameDiffersFromOverriddenParameterVisitor extends BaseInspectionVisitor {

    private static final int SUPER_METHOD = 1, OVERLOADED_METHOD = 2, SUPER_CONSTRUCTOR = 3, OVERLOADED_CONSTRUCTOR = 4;

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.isEmpty()) {
        return;
      }
      final PsiMethod superMethod = MethodUtils.getSuper(method);
      if (superMethod == null) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      checkParameters(superMethod, parameters);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList.isEmpty()) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final boolean constructorCall;
      if (!JavaPsiConstructorUtil.isConstructorCall(expression)) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true);
        if (method == null) {
          return;
        }
        final String name = methodExpression.getReferenceName();
        if (!method.getName().equals(name)) {
          return;
        }
        constructorCall = false;
      }
      else {
        constructorCall = true;
      }
      final PsiMethod targetMethod = expression.resolveMethod();
      if (targetMethod == null) {
        return;
      }
      if (m_ignoreOverridesOfLibraryMethods && constructorCall) {
        if (targetMethod instanceof PsiCompiledElement) {
          return;
        }
      }
      final PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
      final PsiExpression[] arguments = argumentList.getExpressions();
      for (int i = 0, length = Math.min(arguments.length, parameters.length); i < length; i++) {
        PsiExpression argument = arguments[i];
        argument = PsiUtil.skipParenthesizedExprDown(argument);
        if (!(argument instanceof PsiReferenceExpression)) {
          continue;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)argument;
        if (referenceExpression.getQualifierExpression() != null) {
          continue;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiParameter)) {
          continue;
        }
        final String parameterName = parameters[i].getName();
        if (m_ignoreSingleCharacterNames && parameterName.length() == 1) {
          continue;
        }
        final PsiParameter targetParameter = (PsiParameter)target;
        if (!targetParameter.getName().equals(parameterName)) {
          int type = constructorCall
                     ? PsiUtil.isJavaToken(methodExpression.getReferenceNameElement(), JavaTokenType.SUPER_KEYWORD)
                       ? SUPER_CONSTRUCTOR
                       : OVERLOADED_CONSTRUCTOR
                     : OVERLOADED_METHOD;
          registerVariableError(targetParameter, parameterName, type);
        }
      }
    }

    private void checkParameters(@NotNull PsiMethod superMethod, PsiParameter[] parameters) {
      if (m_ignoreOverridesOfLibraryMethods) {
        final PsiClass containingClass = superMethod.getContainingClass();
        if (containingClass != null && LibraryUtil.classIsInLibrary(containingClass)) {
          return;
        }
      }
      final PsiParameterList superParameterList = superMethod.getParameterList();
      final PsiParameter[] superParameters = superParameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        final PsiParameter parameter = parameters[i];
        final String parameterName = parameter.getName();
        final String superParameterName = superParameters[i].getName();
        if (superParameterName.equals(parameterName)) {
          continue;
        }
        if (m_ignoreSingleCharacterNames && superParameterName.length() == 1) {
          continue;
        }
        registerVariableError(parameter, superParameterName, SUPER_METHOD);
      }
    }
  }
}