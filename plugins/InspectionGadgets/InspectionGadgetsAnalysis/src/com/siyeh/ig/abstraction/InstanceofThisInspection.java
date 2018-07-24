/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

public class InstanceofThisInspection extends BaseInspection {
  private static final CallMatcher OBJECT_GET_CLASS =
    CallMatcher.exactInstanceCall(CommonClassNames.JAVA_LANG_OBJECT, "getClass").parameterCount(0);

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("instanceof.check.for.this.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {

    return InspectionGadgetsBundle.message(infos[0] instanceof PsiInstanceOfExpression
                                           ? "instanceof.check.for.this.problem.descriptor"
                                           : "instanceof.check.for.this.equality.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new InstanceofThisVisitor();
  }

  private static class InstanceofThisVisitor extends BaseInspectionVisitor {
    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      if (OBJECT_GET_CLASS.test(call)) {
        PsiExpression qualifier = ExpressionUtils.getQualifierOrThis(call.getMethodExpression());
        if (StreamEx.of(ExpressionUtils.nonStructuralChildren(qualifier)).select(PsiThisExpression.class)
                .anyMatch(thisExpression -> thisExpression.getQualifier() == null)) {
          PsiExpression compared = ExpressionUtils.getExpressionComparedTo(call);
          if (compared instanceof PsiClassObjectAccessExpression) {
            registerError(qualifier.isPhysical() ? qualifier : call, call);
          }
        }
      }
    }

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression thisValue) {
      super.visitThisExpression(thisValue);
      if (thisValue.getQualifier() != null) {
        return;
      }
      final PsiElement parent =
        PsiTreeUtil.skipParentsOfType(thisValue, PsiParenthesizedExpression.class,
                                      PsiConditionalExpression.class, PsiTypeCastExpression.class);
      if (!(parent instanceof PsiInstanceOfExpression)) {
        return;
      }
      registerError(thisValue, parent);
    }
  }
}