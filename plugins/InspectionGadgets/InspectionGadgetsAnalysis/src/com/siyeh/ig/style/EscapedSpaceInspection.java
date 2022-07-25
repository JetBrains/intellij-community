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
        int pos = 1;
        while (true) {
          pos = text.indexOf('\\', pos);
          if (pos == -1 || pos == text.length() - 1) return;
          char next = text.charAt(pos + 1);
          if (next == 'u') {
            // unicode escape
            pos += 6;
            continue;
          }
          pos += 2;
          if (next >= '0' && next <= '9') {
            // octal escape
            if (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos++;
            if (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') pos++;
            continue;
          }
          if (next != 's') {
            // other escapes
            continue;
          }
          if (pos > 4 && text.startsWith("\\s", pos - 4)) continue;
          if (text.startsWith("\\s", pos)) continue;
          if (block && (pos == text.length() || text.charAt(pos) == '\n')) continue;
          holder.registerProblem(literal, TextRange.create(pos - 2, pos),
                                 InspectionGadgetsBundle.message("inspection.use.of.slash.s.message"),
                                 new ReplaceWithSpaceFix(pos - 2));
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
