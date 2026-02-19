// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonLiteral;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class UnresolvedThemeJsonNamedColorInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!ThemeJsonUtil.isThemeFilename(holder.getFile().getName())) return PsiElementVisitor.EMPTY_VISITOR;

    return new JsonElementVisitor() {
      @Override
      public void visitLiteral(@NotNull JsonLiteral literal) {
        if (literal.getTextLength() < 2) return;
        for (PsiReference reference : literal.getReferences()) {
          if (reference instanceof ThemeJsonNamedColorPsiReference) {
            if (reference.resolve() == null) {
              TextRange range = reference.getRangeInElement();
              if (range.isEmpty() && range.getStartOffset() == 1 && "\"\"".equals(literal.getText())) {
                String message = ProblemsHolder.unresolvedReferenceMessage(reference);
                holder.registerProblem(literal, message, ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, TextRange.create(0, 2));
              }
              else {
                holder.registerProblem(reference);
              }
            }
          }
        }
      }
    };
  }
}
