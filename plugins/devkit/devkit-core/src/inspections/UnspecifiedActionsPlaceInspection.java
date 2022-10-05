// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public class UnspecifiedActionsPlaceInspection extends DevKitInspectionBase {
  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        PsiMethod method = expression.resolveMethod();
        if (method != null) {
          String methodName = method.getName();
          if (requiresSpecifiedActionPlace(method, methodName)) {
            PsiExpression[] expressions = expression.getArgumentList().getExpressions();
            if (expressions.length > 0 && actionPlaceIsUnspecified(expressions[0])) {
              String messageKey = "createActionToolbar".equals(methodName)
                                  ? "inspections.unspecified.actions.place.toolbar"
                                  : "inspections.unspecified.actions.place.popup.menu";
              holder.registerProblem(expressions[0], DevKitBundle.message(messageKey));
            }
          }
          super.visitMethodCallExpression(expression);
        }
      }
    };
  }

  private static boolean requiresSpecifiedActionPlace(@NotNull PsiMethod method, @NotNull String methodName) {
    if ("createActionToolbar".equals(methodName) || "createActionPopupMenu".equals(methodName)) {
      PsiClass aClass = method.getContainingClass();
      if (aClass != null && ActionManager.class.getName().equals(aClass.getQualifiedName())) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length > 0 && parameters[0].getType() instanceof PsiClassType) {
          PsiType type = parameters[0].getType();
          //first check doesn't require resolve
          if (Objects.equals(((PsiClassType)type).getClassName(), CommonClassNames.JAVA_LANG_STRING_SHORT)
              && CommonClassNames.JAVA_LANG_STRING.equals(type.getCanonicalText(false))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean actionPlaceIsUnspecified(PsiExpression placeArgument) {
    String text = placeArgument.getText();
    if (text.equals("\"\"") || text.equals("\"unknown\"") || text.endsWith(".UNKNOWN")) {
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "UnspecifiedActionsPlace";
  }
}
