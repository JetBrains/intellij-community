// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.uast.UastHintedVisitorAdapter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.devkit.references.PluginConfigReference;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UPolyadicExpression;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

/**
 * Highlights all unresolved {@link PluginConfigReference}s in code.
 */
@VisibleForTesting
@ApiStatus.Internal
public final class UnresolvedPluginConfigReferenceInspection extends DevKitUastInspectionBase {

  @SuppressWarnings("unchecked")
  private final Class<? extends UElement>[] HINTS = new Class[]{UInjectionHost.class};

  @Override
  protected PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitPolyadicExpression(@NotNull UPolyadicExpression node) {
        if (!(node instanceof UInjectionHost uInjectionHost)) return true;
        processInjectionHost(uInjectionHost);
        return super.visitPolyadicExpression(node);
      }

      @Override
      public boolean visitLiteralExpression(@NotNull ULiteralExpression uLiteralExpression) {
        if (!(uLiteralExpression instanceof UInjectionHost uInjectionHost)) return true;
        processInjectionHost(uInjectionHost);
        return super.visitExpression(uLiteralExpression);
      }

      private void processInjectionHost(@NotNull UInjectionHost node) {
        PsiElement element = node.getSourcePsi();
        if (element == null) return;
        for (PsiReference reference : element.getReferences()) {
          if (reference instanceof PluginConfigReference && !reference.isSoft()) {
            if (reference instanceof PsiPolyVariantReference polyVariantReference) {
              if (polyVariantReference.multiResolve(false).length == 0) {
                holder.registerProblem(reference);
              }
            }
            else if (reference.resolve() == null) {
              holder.registerProblem(reference);
            }
          }
        }
      }
    }, HINTS);
  }
}
