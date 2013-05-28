/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import org.jetbrains.annotations.NotNull;

public class StringTokenizerInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "UseOfStringTokenizer";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "use.stringtokenizer.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "use.stringtokenizer.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringTokenizerVisitor();
  }

  private static class StringTokenizerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      super.visitVariable(variable);
      final PsiType type = variable.getType();
      final PsiType deepComponentType = type.getDeepComponentType();
      if (!TypeUtils.typeEquals("java.util.StringTokenizer",
                                deepComponentType)) {
        return;
      }
      final PsiTypeElement typeElement = variable.getTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiExpression initializer = variable.getInitializer();
      if (isTokenizingNonNlsAnnotatedElement(initializer)) {
        return;
      }
      registerError(typeElement);
    }

    private static boolean isTokenizingNonNlsAnnotatedElement(
      PsiExpression initializer) {
      if (!(initializer instanceof PsiNewExpression)) {
        return false;
      }
      final PsiNewExpression newExpression =
        (PsiNewExpression)initializer;
      final PsiExpressionList argumentList =
        newExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] expressions =
        argumentList.getExpressions();
      if (expressions.length <= 0) {
        return false;
      }
      return NonNlsUtils.isNonNlsAnnotated(expressions[0]);
    }
  }
}