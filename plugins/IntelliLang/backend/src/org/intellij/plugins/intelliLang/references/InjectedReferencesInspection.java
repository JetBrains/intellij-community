// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.references;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public final class InjectedReferencesInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new InjectedReferencesVisitor(holder);
  }

  private static class InjectedReferencesVisitor extends PsiElementVisitor implements HintedPsiElementVisitor {
    private final ProblemsHolder myHolder;

    private InjectedReferencesVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public @NotNull List<Class<?>> getHintPsiElements() {
      return List.of(PsiLanguageInjectionHost.class, ContributedReferenceHost.class);
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      PsiReference[] injected = InjectedReferencesContributor.getInjectedReferences(element);
      if (injected != null) {
        for (PsiReference reference : injected) {
          if (isUnresolved(reference)) {
            TextRange range = reference.getRangeInElement();
            if (range.isEmpty() && range.getStartOffset() == 1 && "\"\"".equals(element.getText())) {
              String message = ProblemsHolder.unresolvedReferenceMessage(reference);
              myHolder.registerProblem(element, message, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, TextRange.create(0, 2));
            }
            else {
              myHolder.registerProblem(reference);
            }
          }
        }
      }

      super.visitElement(element);
    }

    private static boolean isUnresolved(PsiReference reference) {
      if (reference instanceof PsiPolyVariantReference polyVariantReference) {
        return polyVariantReference.multiResolve(false).length == 0;
      }
      return reference.resolve() == null;
    }
  }
}
