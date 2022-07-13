// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class EscapedSpaceInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.TEXT_BLOCK_ESCAPES.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitLiteralExpression(@NotNull PsiLiteralExpression literal) {
        PsiType type = literal.getType();
        if (!TypeUtils.isJavaLangString(type)) return;
        boolean block = literal.isTextBlock();
        String text = literal.getText();
        int pos = 0;
        while (true) {
          pos = text.indexOf("\\s", pos + 1);
          if (pos == -1) return;
          if (pos > 2 && text.startsWith("\\s", pos - 2)) continue;
          if (text.startsWith("\\s", pos + 2)) continue;
          if (block && (pos + 2 == text.length() || text.charAt(pos + 2) == '\n')) continue;
          holder.registerProblem(literal, TextRange.create(pos, pos+2),
                                 InspectionGadgetsBundle.message("inspection.use.of.slash.s.message"),
                                 new ReplaceWithSpaceFix(pos));
        }
      }
    };
  }

  private static class ReplaceWithSpaceFix implements LocalQuickFix {
    private final int myPos;

    ReplaceWithSpaceFix(int pos) { myPos = pos; }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.use.of.slash.s.fix.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLiteralExpression literal = ObjectUtils.tryCast(descriptor.getStartElement(), PsiLiteralExpression.class);
      if (literal == null) return;
      String text = literal.getText();
      if (text.length() < myPos+2) return;
      String newText = text.substring(0, myPos) + ' ' + text.substring(myPos + 2);
      literal.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(newText, null));
    }
  }
}
