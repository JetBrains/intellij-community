// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.ui.UIThemeMetadata;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethod;
import com.intellij.uast.UastVisitorAdapter;
import com.intellij.ui.JBColor;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.themes.metadata.UIThemeMetadataService;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.List;

public class UnregisteredNamedColorInspection extends DevKitUastInspectionBase {

  private static final String JB_COLOR_FQN = JBColor.class.getCanonicalName();
  private static final String NAMED_COLOR_METHOD_NAME = "namedColor";

  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isPluginProject(holder.getProject())) return PsiElementVisitor.EMPTY_VISITOR;

    return new UastVisitorAdapter(new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitExpression(@NotNull UExpression node) {
        if (node instanceof UCallExpression) {
          handleCallExpression(holder, (UCallExpression)node);
        }
        else if (node instanceof UQualifiedReferenceExpression) {
          UElement parent = node.getUastParent();
          if (parent instanceof UCallExpression) {
            handleCallExpression(holder, (UCallExpression)parent);
          }
        }

        return true;
      }
    }, true);
  }

  private static void handleCallExpression(@NotNull ProblemsHolder holder, @NotNull UCallExpression expression) {
    if (!isNamedColorCall(expression)) return;

    String key = getKey(expression);
    if (key == null) return;

    if (!isRegisteredNamedColor(key)) {
      registerProblem(key, holder, expression, null);
    }
  }

  private static boolean isNamedColorCall(@NotNull UCallExpression expression) {
    if (!NAMED_COLOR_METHOD_NAME.equals(expression.getMethodName())) return false;
    PsiMethod resolved = expression.resolve();
    if (resolved == null) return false;
    PsiClass containingClass = resolved.getContainingClass();
    return containingClass != null && JB_COLOR_FQN.equals(containingClass.getQualifiedName());
  }

  @Nullable
  private static String getKey(@NotNull UCallExpression expression) {
    List<UExpression> arguments = expression.getValueArguments();
    if (arguments.isEmpty()) return null;
    UExpression firstArgument = arguments.get(0);
    Object evaluated = firstArgument.evaluate();
    if (!(evaluated instanceof String)) return null;
    return (String)evaluated;
  }

  private static boolean isRegisteredNamedColor(@NotNull String key) {
    PairProcessor<UIThemeMetadata, UIThemeMetadata.UIKeyMetadata> processor = (themeMetadata, uiKeyMetadata) -> {
      if (key.equals(uiKeyMetadata.getKey())) {
        return false;
      }
      return true;
    };
    return !UIThemeMetadataService.getInstance().processAllKeys(processor);
  }

  private static void registerProblem(@NotNull String key,
                                      @NotNull ProblemsHolder holder,
                                      @NotNull UCallExpression expression,
                                      @Nullable LocalQuickFix fix) {
    UIdentifier identifier = expression.getMethodIdentifier();
    if (identifier == null) return;
    PsiElement identifierPsi = identifier.getPsi();
    if (identifierPsi == null) return;

    holder.registerProblem(identifierPsi,
                           DevKitBundle.message("inspections.unregistered.named.color", key),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           fix);
  }
}
