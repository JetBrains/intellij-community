/*
 * Copyright 2007-2016 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiNewExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ThrowableInstanceNeverThrownInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "throwable.instance.never.thrown.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final String type =
      TypeUtils.expressionHasTypeOrSubtype(expression,
                                           CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION,
                                           CommonClassNames.JAVA_LANG_EXCEPTION,
                                           CommonClassNames.JAVA_LANG_ERROR);
    if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(type)) {
      return InspectionGadgetsBundle.message(
        "throwable.instance.never.thrown.runtime.exception.problem.descriptor");
    }
    else if (CommonClassNames.JAVA_LANG_EXCEPTION.equals(type)) {
      return InspectionGadgetsBundle.message(
        "throwable.instance.never.thrown.checked.exception.problem.descriptor");
    }
    else if (CommonClassNames.JAVA_LANG_ERROR.equals(type)) {
      return InspectionGadgetsBundle.message(
        "throwable.instance.never.thrown.error.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "throwable.instance.never.thrown.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExceptionInstanceNeverThrownVisitor();
  }

  private static class ExceptionInstanceNeverThrownVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!ThrowableResultOfMethodCallIgnoredInspection.isIgnoredThrowable(expression)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}