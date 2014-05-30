/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NewExceptionWithoutArgumentsInspection extends BaseInspection {

  @Deprecated
  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreWithoutParameters = false;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("new.exception.without.arguments.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("new.exception.without.arguments.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NewExceptionWithoutArgumentsVisitor();
  }

  private class NewExceptionWithoutArgumentsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 0) {
        return;
      }
      final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      if (classReference == null) {
        return;
      }
      final PsiElement target = classReference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      if (hasAccessibleConstructorWithParameters(aClass, expression)) {
        registerNewExpressionError(expression);
      }
    }

    private boolean hasAccessibleConstructorWithParameters(PsiClass aClass, PsiElement context) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        final PsiParameterList parameterList = constructor.getParameterList();
        final int count = parameterList.getParametersCount();
        if (count <= 0) {
          continue;
        }
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
        if (resolveHelper.isAccessible(constructor, context, aClass)) {
          return true;
        }
      }
      return false;
    }
  }
}
