// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.devkit.references.PluginConfigReference;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

/**
 * Highlights all unresolved {@link PluginConfigReference}s in code.
 */
@VisibleForTesting
public final class UnresolvedPluginConfigReferenceInspection extends LocalInspectionTool {

  @SuppressWarnings("unchecked")
  private final Class<? extends UElement>[] HINTS = new Class[]{ULiteralExpression.class};

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!DevKitInspectionUtil.isAllowed(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitLiteralExpression(@NotNull ULiteralExpression uLiteralExpression) {
        visit(uLiteralExpression);

        return super.visitExpression(uLiteralExpression);
      }

      private void visit(ULiteralExpression uLiteralExpression) {
        PsiElement element = uLiteralExpression.getSourcePsi();
        PsiLanguageInjectionHost expression = UastLiteralUtils.getSourceInjectionHost(uLiteralExpression);
        if (element == null || expression == null) return;

        for (PsiReference reference : expression.getReferences()) {
          if (reference instanceof PluginConfigReference &&
              !reference.isSoft() &&
              reference.resolve() == null) {
            holder.registerProblem(reference);
          }
        }
      }
    }, HINTS);
  }
}
