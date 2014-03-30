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
package com.siyeh.ig.security;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SystemPropertiesInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "AccessOfSystemProperties";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "system.properties.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean isGetSystemProperty =
      ((Boolean)infos[0]).booleanValue();
    final boolean isIntegerGetInteger =
      ((Boolean)infos[1]).booleanValue();
    if (isGetSystemProperty) {
      return InspectionGadgetsBundle.message(
        "system.set.problem.descriptor");
    }
    else if (isIntegerGetInteger) {
      return InspectionGadgetsBundle.message(
        "system.properties.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "system.properties.problem.descriptor1");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SystemPropertiesVisitor();
  }

  private static class SystemPropertiesVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final boolean isGetSystemProperty = isGetSystemProperty(expression);
      final boolean isIntegerGetInteger = isIntegerGetInteger(expression);
      final boolean isBooleanGetBoolean = isBooleanGetBoolean(expression);
      if (!(isGetSystemProperty || isIntegerGetInteger ||
            isBooleanGetBoolean)) {
        return;
      }
      registerMethodCallError(expression,
                              Boolean.valueOf(isGetSystemProperty),
                              Boolean.valueOf(isIntegerGetInteger));
    }

    private static boolean isGetSystemProperty(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"getProperty".equals(methodName)
          && !"getProperties".equals(methodName)
          && !"setProperty".equals(methodName)
          && !"setProperties".equals(methodName)
          && !"clearProperties".equals(methodName)
        ) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String className = aClass.getQualifiedName();
      if (className == null) {
        return false;
      }
      return "java.lang.System".equals(className);
    }

    private static boolean isIntegerGetInteger(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"getInteger".equals(methodName)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String className = aClass.getQualifiedName();
      if (className == null) {
        return false;
      }
      return CommonClassNames.JAVA_LANG_INTEGER.equals(className);
    }

    private static boolean isBooleanGetBoolean(
      PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      @NonNls final String methodName =
        methodExpression.getReferenceName();
      if (!"getBoolean".equals(methodName)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final String className = aClass.getQualifiedName();
      if (className == null) {
        return false;
      }
      return CommonClassNames.JAVA_LANG_BOOLEAN.equals(className);
    }
  }
}