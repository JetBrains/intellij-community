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
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import one.util.streamex.IntStreamEx;
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
        for (int pos : findPositions(literal)) {
          holder.registerProblem(literal, TextRange.create(pos, pos + 2),
                                 InspectionGadgetsBundle.message("inspection.use.of.slash.s.message"),
                                 new ReplaceWithSpaceFix());
        }
      }
    };
  }

  private static int[] findPositions(@NotNull PsiLiteralExpression literal) {
    boolean block = literal.isTextBlock();
    String text = literal.getText();
    int pos = 1;
    IntList list = new IntArrayList();
    while (true) {
      pos = text.indexOf('\\', pos);
      if (pos == -1 || pos == text.length() - 1) break;
      char next = text.charAt(pos + 1);
      if (next == 'u') {
        // unicode escape can contain several 'u', according to the spec
        pos += 2;
        while (pos < text.length() && text.charAt(pos) == 'u') pos++;
        pos += 4;
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
      if (block && (pos == text.length() || text.charAt(pos) == '\n'
                    || pos == text.length() - 3 && text.endsWith("\"\"\""))) {
        continue;
      }
      list.add(pos - 2);
    }
    return list.toIntArray();
  }

  private static class ReplaceWithSpaceFix implements LocalQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("inspection.use.of.slash.s.fix.family");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiLiteralExpression literal = ObjectUtils.tryCast(descriptor.getStartElement(), PsiLiteralExpression.class);
      if (literal == null) return;
      int[] positions = findPositions(literal);
      String text = literal.getText();
      String newText = IntStreamEx.of(positions)
        .takeWhile(pos -> pos < text.length() - 2)
        .boxed()
        .prepend(-2)
        .append(text.length())
        .pairMap((start, end) -> text.substring(start + 2, end))
        .joining(" ");
      literal.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(newText, null));
    }
  }
}
