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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class InstanceofInterfacesInspection extends BaseInspection {
  private static final CallMatcher OBJECT_GET_CLASS =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);

  @SuppressWarnings("PublicField")
  public boolean ignoreAbstractClasses = false;

  @Pattern(VALID_ID_PATTERN)
  @NotNull
  @Override
  public String getID() {
    return "InstanceofConcreteClass";
  }

  @Nullable
  @Override
  public String getAlternativeID() {
    return "InstanceofInterfaces"; // keep old suppression working
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "instanceof.concrete.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(infos[0] instanceof PsiInstanceOfExpression ?
                                           "instanceof.concrete.class.problem.descriptor" :
                                           "instanceof.concrete.class.equality.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("instanceof.interfaces.option"), this, "ignoreAbstractClasses");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofInterfacesVisitor();
  }

  private class InstanceofInterfacesVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      if (OBJECT_GET_CLASS.test(call)) {
        PsiExpression other = ExpressionUtils.getExpressionComparedTo(call);
        if (other instanceof PsiClassObjectAccessExpression) {
          checkTypeElement(((PsiClassObjectAccessExpression)other).getOperand());
        }
      }
    }

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      checkTypeElement(expression.getCheckType());
    }

    public void checkTypeElement(PsiTypeElement typeElement) {
      if (!ConcreteClassUtil.typeIsConcreteClass(typeElement, ignoreAbstractClasses)) {
        return;
      }
      final PsiMethod method = PsiTreeUtil.getParentOfType(typeElement, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
      if (MethodUtils.isEquals(method)) {
        return;
      }
      registerError(typeElement, typeElement.getParent());
    }
  }
}