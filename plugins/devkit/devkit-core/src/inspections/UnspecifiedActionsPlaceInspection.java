// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.psi.*;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UExpression;
import org.jetbrains.uast.util.UastExpressionUtils;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
public class UnspecifiedActionsPlaceInspection extends DevKitUastInspectionBase {

  private static final boolean SKIP_CHILDREN = true;

  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS = new Class[]{UCallExpression.class};
  public static final String CREATE_ACTION_TOOLBAR_METHOD_NAME = "createActionToolbar";
  public static final String CREATE_ACTION_POPUP_MENU_METHOD_NAME = "createActionPopupMenu";

  @Override
  @NotNull
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        if (UastExpressionUtils.isMethodCall(node)) {
          PsiMethod method = node.resolve();
          if (method != null) {
            String methodName = node.getMethodName();
            if (methodName != null && requiresSpecifiedActionPlace(method, methodName) && node.getValueArgumentCount() > 0) {
              UExpression parameter = node.getArgumentForParameter(0);
              if (parameter != null && actionPlaceIsUnspecified(parameter)) {
                PsiElement reportedElement = parameter.getSourcePsi();
                if (reportedElement == null) return SKIP_CHILDREN;
                String messageKey = CREATE_ACTION_TOOLBAR_METHOD_NAME.equals(methodName)
                                    ? "inspections.unspecified.actions.place.toolbar"
                                    : "inspections.unspecified.actions.place.popup.menu";
                holder.registerProblem(reportedElement, DevKitBundle.message(messageKey));
              }
            }
          }
        }
        return SKIP_CHILDREN;
      }
    }, HINTS);
  }

  private static boolean requiresSpecifiedActionPlace(@NotNull PsiMethod method, @NotNull String methodName) {
    if (CREATE_ACTION_TOOLBAR_METHOD_NAME.equals(methodName) || CREATE_ACTION_POPUP_MENU_METHOD_NAME.equals(methodName)) {
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

  private static boolean actionPlaceIsUnspecified(UExpression parameter) {
    Object evaluatedExpression = parameter.evaluate();
    if (evaluatedExpression instanceof String stringValue && (stringValue.isEmpty() || "unknown".equals(stringValue))) {
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
