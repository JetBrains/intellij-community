/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class UniqueToolbarIdInspection extends DevKitInspectionBase {
  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        PsiMethod method = expression.resolveMethod();
        if (method != null && "createActionToolbar".equals(method.getName())) {
          PsiClass aClass = method.getContainingClass();
          if (aClass != null && ActionManager.class.getName().equals(aClass.getQualifiedName())) {
            PsiParameter[] parameters = method.getParameterList().getParameters();
            if (parameters.length > 0 &&  parameters[0].getType() instanceof PsiClassType) {
              PsiType type = parameters[0].getType();
              //first check doesn't require resolve
              if (Comparing.equal(((PsiClassType)type).getClassName(), CommonClassNames.JAVA_LANG_STRING_SHORT)
                && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText(false))) {
                PsiExpression[] expressions = expression.getArgumentList().getExpressions();
                if (expressions.length > 0) {
                  String text = expressions[0].getText();
                  if (text.equals("\"\"") || text.endsWith(".UNKNOWN")) {
                    holder.registerProblem(expressions[0], "Specify unique toolbar id");
                  }
                }
              }
            }
          }
        }
        super.visitMethodCallExpression(expression);
      }
    };
  }

  @NotNull
  @Override
  public String getShortName() {
    return "InspectionUniqueToolbarId";
  }
}
