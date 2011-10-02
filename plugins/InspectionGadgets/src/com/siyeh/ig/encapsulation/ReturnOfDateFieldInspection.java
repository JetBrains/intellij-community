/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ReturnOfDateFieldInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "return.date.calendar.field.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String type = (String)infos[0];
    return InspectionGadgetsBundle.message(
      "return.date.calendar.field.problem.descriptor", type);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnOfDateFieldVisitor();
  }

  private static class ReturnOfDateFieldVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(
      @NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression returnValue = statement.getReturnValue();
      if (!(returnValue instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression fieldReference =
        (PsiReferenceExpression)returnValue;
      final PsiElement element = fieldReference.resolve();
      if (!(element instanceof PsiField)) {
        return;
      }
      final String type = TypeUtils.expressionHasTypeOrSubtype(
        returnValue, CommonClassNames.JAVA_UTIL_DATE,
        CommonClassNames.JAVA_UTIL_CALENDAR);
      if (type == null) {
        return;
      }
      registerError(returnValue, type);
    }
  }
}