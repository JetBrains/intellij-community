/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.internationalization;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ImplicitDefaultCharsetUsageInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("implicit.default.charset.usage.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    if (infos[0] instanceof PsiNewExpression) {
      return InspectionGadgetsBundle.message("implicit.default.charset.usage.constructor.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("implicit.default.charset.usage.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ImplicitDefaultCharsetUsageVisitor();
  }

  private static class ImplicitDefaultCharsetUsageVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!"getBytes".equals(name)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() == 1) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass ==  null) {
        return;
      }
      final String qName = aClass.getQualifiedName();
      if (!CommonClassNames.JAVA_LANG_STRING.equals(qName)) {
        return;
      }
      registerMethodCallError(expression, expression);
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiMethod constructor = expression.resolveConstructor();
      if (constructor == null) {
        return;
      }
      final PsiClass aClass = constructor.getContainingClass();
      if (aClass == null) {
        return;
      }
      final PsiParameterList parameterList = constructor.getParameterList();
      final int count = parameterList.getParametersCount();
      if (count == 0) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final String qName = aClass.getQualifiedName();
      if (CommonClassNames.JAVA_LANG_STRING.equals(qName)) {
        if (!parameters[0].getType().equalsToText("byte[]") || hasCharsetType(parameters[count - 1])) {
          return;
        }
      }
      else if ("java.io.InputStreamReader".equals(qName) ||
               "java.io.OutputStreamWriter".equals(qName) ||
               "java.io.PrintStream".equals(qName)) {
        if (hasCharsetType(parameters[count - 1])) {
          return;
        }
      }
      else if ("java.io.PrintWriter".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[count - 1]) || parameters[0].getType().equalsToText("java.io.Writer")) {
          return;
        }
      }
      else if ("java.util.Formatter".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[1])) {
          return;
        }
        final PsiType firstType = parameters[0].getType();
        if (!firstType.equalsToText(CommonClassNames.JAVA_LANG_STRING) && !firstType.equalsToText("java.io.File") &&
          !firstType.equalsToText("java.io.OutputStream")) {
          return;
        }
      }
      else if ("java.util.Scanner".equals(qName)) {
        if (count > 1 && hasCharsetType(parameters[1])) {
          return;
        }
        final PsiType firstType = parameters[0].getType();
        if (!firstType.equalsToText("java.io.InputStream") && !firstType.equalsToText("java.io.File") &&
          !firstType.equalsToText("java.nio.file.Path") && !firstType.equalsToText("java.nio.channels.ReadableByteChannel")) {
          return;
        }
      }
      else if (!"java.io.FileReader".equals(qName) && !"java.io.FileWriter".equals(qName)) {
        return;
      }
      registerNewExpressionError(expression, expression);

    }

    private static boolean hasCharsetType(PsiVariable variable) {
      return TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_LANG_STRING,
                                                "java.nio.charset.Charset",
                                                "java.nio.charset.CharsetEncoder",
                                                "java.nio.charset.CharsetDecoder");
    }
  }
}
