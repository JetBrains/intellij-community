// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonLiteral;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class UnresolvedThemeJsonNamedColorInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!ThemeJsonUtil.isThemeFilename(holder.getFile().getName())) return PsiElementVisitor.EMPTY_VISITOR;

    PsiSymbolReferenceService symbolReferenceService = PsiSymbolReferenceService.getService();

    return new JsonElementVisitor() {
      @Override
      public void visitLiteral(@NotNull JsonLiteral literal) {
        if (literal.getTextLength() < 2) return;

        for (PsiSymbolReference reference : symbolReferenceService.getReferences(literal)) {
          if (reference instanceof ThemeColorKeyReference colorRef
              && !colorRef.isSoft() && colorRef.resolveSingleTarget() == null) {
            TextRange range = reference.getRangeInElement();
            var rangeForHighlights = range.isEmpty() && range.getStartOffset() == 1 && "\"\"".equals(literal.getText()) ?
                                     TextRange.create(0, 2) : ElementManipulators.getValueTextRange(literal);

            holder.registerProblem(literal, AnalysisBundle.message("error.cannot.resolve.default.message",
                                                                   ElementManipulators.getValueText(literal)),
                                   ProblemHighlightType.LIKE_UNKNOWN_SYMBOL, rangeForHighlights);
          }
        }
      }
    };
  }
}
