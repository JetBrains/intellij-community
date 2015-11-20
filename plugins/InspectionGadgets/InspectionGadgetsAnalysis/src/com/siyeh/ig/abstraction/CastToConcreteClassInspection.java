/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CastToConcreteClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAbstractClasses = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreInEquals = true; // keep for compatibility

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("cast.to.concrete.class.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiType type= (PsiType)infos[0];
    return InspectionGadgetsBundle.message("cast.to.concrete.class.problem.descriptor", type.getPresentableText());
  }

  @Override
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("cast.to.concrete.class.option"), "ignoreAbstractClasses");
    panel.addCheckbox(InspectionGadgetsBundle.message("cast.to.concrete.class.ignore.equals.option"), "ignoreInEquals");
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CastToConcreteClassVisitor();
  }

  private class CastToConcreteClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement == null) {
        return;
      }
      if (!ConcreteClassUtil.typeIsConcreteClass(typeElement, ignoreAbstractClasses)) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (MethodUtils.isEquals(method) || CloneUtils.isClone(method)) {
        return;
      }
      registerError(typeElement, typeElement.getType());
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls
      final String referenceName = methodExpression.getReferenceName();
      if (!"cast".equals(referenceName)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return;
      }
      final String className = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_CLASS.equals(className)) {
        return;
      }
      final PsiType[] parameters = classType.getParameters();
      if (parameters.length != 1) {
        return;
      }
      final PsiType parameter = parameters[0];
      if (!ConcreteClassUtil.typeIsConcreteClass(parameter, ignoreAbstractClasses)) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (MethodUtils.isEquals(method) || CloneUtils.isClone(method)) {
        return;
      }
      registerMethodCallError(expression, parameter);
    }
  }
}
