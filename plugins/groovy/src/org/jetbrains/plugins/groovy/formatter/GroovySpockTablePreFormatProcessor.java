// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlockGenerator;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public final class GroovySpockTablePreFormatProcessor implements PreFormatProcessor {
  @Override
  public @NotNull TextRange process(@NotNull ASTNode element, @NotNull TextRange range) {
    return range;
  }

  @Override
  public @NotNull TextRange adjustRange(@NotNull ASTNode element, @NotNull TextRange range) {
    PsiElement psi = element.getPsi();
    if (psi == null) return range;
    PsiFile file = psi.getContainingFile();
    if (!(file instanceof GroovyFileBase)) return range;
    GrStatement row = findTableRow(file.findElementAt(range.getEndOffset() - 1));
    return row == null ? range : range.union(tableSpan(row));
  }

  @Override
  public boolean changesWhitespacesOnly() { return true; }

  private static @Nullable GrStatement findTableRow(@Nullable PsiElement leaf) {
    PsiElement e = leaf;
    GrBinaryExpression tableRow = null;
    while (e != null && !(e instanceof GrMethod)) {
      if (e instanceof GrBinaryExpression bin && GroovyBlockGenerator.isTablePart(bin)) {
        tableRow = bin;
      }
      e = e.getParent();
    }
    return tableRow;
  }

  private static @NotNull TextRange tableSpan(@NotNull GrStatement row) {
    PsiElement parent = row.getParent();
    GrLabeledStatement label = null;
    if (parent instanceof GrLabeledStatement ls) {
      label = ls;
    }
    else {
      for (PsiElement s = row.getPrevSibling(); s != null; s = s.getPrevSibling()) {
        if (s instanceof GrLabeledStatement ls) {
          label = ls;
          break;
        }
      }
    }
    if (label == null) return row.getTextRange();
    GrStatement header = label.getStatement();
    int start = header != null ? header.getTextRange().getStartOffset() : row.getTextRange().getStartOffset();
    int end = header != null ? header.getTextRange().getEndOffset() : start;
    for (PsiElement s = label.getNextSibling(); s != null; s = s.getNextSibling()) {
      if (s instanceof GrStatement dataRow && GroovyBlockGenerator.isTablePart(dataRow)) {
        end = Math.max(end, dataRow.getTextRange().getEndOffset());
      }
    }
    return new TextRange(start, end);
  }
}
