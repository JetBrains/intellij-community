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
package com.siyeh.ig.portability;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class UseOfJDBCDriverClassInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "use.of.concrete.jdbc.driver.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "use.of.concrete.jdbc.driver.class.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UseOfJDBCDriverClassVisitor();
  }

  private static class UseOfJDBCDriverClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      final PsiType type = variable.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiType deepComponentType = type.getDeepComponentType();
      if (!(deepComponentType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)deepComponentType;
      final PsiClass resolveClass = classType.resolve();
      if (resolveClass == null) {
        return;
      }
      if (resolveClass.isEnum() || resolveClass.isInterface() ||
          resolveClass.isAnnotationType()) {
        return;
      }
      if (resolveClass instanceof PsiTypeParameter) {
        return;
      }
      if (!InheritanceUtil.isInheritor(resolveClass, "java.sql.Driver")) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return;
      }
      registerError(typeElement);
    }

    @Override
    public void visitNewExpression(
      @NotNull PsiNewExpression newExpression) {
      super.visitNewExpression(newExpression);
      final PsiType type = newExpression.getType();
      if (type == null) {
        return;
      }
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClass resolveClass = ((PsiClassType)type).resolve();
      if (resolveClass == null) {
        return;
      }
      if (resolveClass.isEnum() || resolveClass.isInterface() ||
          resolveClass.isAnnotationType()) {
        return;
      }
      if (resolveClass instanceof PsiTypeParameter) {
        return;
      }
      if (!InheritanceUtil.isInheritor(resolveClass, "java.sql.Driver")) {
        return;
      }
      registerNewExpressionError(newExpression);
    }
  }
}