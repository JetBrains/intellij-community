/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.j2me;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class SingleCharacterStartsWithInspection extends ExpressionInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("single.character.startswith.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.J2ME_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("single.character.startswith.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SingleCharacterStartsWithVisitor();
    }

    private static class SingleCharacterStartsWithVisitor extends BaseInspectionVisitor {
      @NonNls private static final String STARTS_WITH_METHOD = "startsWith";
      @NonNls private static final String ENDS_WITH_METHOD = "endsWith";

      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
          super.visitMethodCallExpression(call);
          final PsiReferenceExpression methodExpression = call.getMethodExpression();
          final String methodName = methodExpression.getReferenceName();
          if (!STARTS_WITH_METHOD.equals(methodName) && !ENDS_WITH_METHOD.equals(methodName)) {
              return;
          }
          final PsiExpressionList argumentList = call.getArgumentList();
          if (argumentList == null) {
              return;
          }
          final PsiExpression[] args = argumentList.getExpressions();
          if (args.length != 1 && args.length != 2) {
              return;
          }
          if (!isSingleCharacterStringLiteral(args[0])) {
              return;
          }
          final PsiExpression qualifier = methodExpression.getQualifierExpression();
          if (qualifier == null) {
              return;
          }
          final PsiType type = qualifier.getType();
          if (!TypeUtils.isJavaLangString(type)) {
              return;
          }
          registerMethodCallError(call);
      }

        private static boolean isSingleCharacterStringLiteral(PsiExpression arg) {
            final PsiType type = arg.getType();
            if (!TypeUtils.isJavaLangString(type)) {
                return false;
            }
            if (!(arg instanceof PsiLiteralExpression)) {
                return false;
            }
            final PsiLiteralExpression literal = (PsiLiteralExpression) arg;
            final String value = (String) literal.getValue();
            if (value == null) {
                return false;
            }
            return value.length() == 1;
        }
    }
}