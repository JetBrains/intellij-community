// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor.selection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;

import java.util.List;

/**
 * @author Max Medvedev
 */
public final class GroovyElseSelectioner extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return e instanceof GrIfStatement;
  }

  @Override
  public @Nullable List<TextRange> select(@NotNull PsiElement e, @NotNull CharSequence editorText, int cursorOffset, @NotNull Editor editor) {
    if (!(e instanceof GrIfStatement ifSt)) return null;

    GrStatement branch = ifSt.getElseBranch();
    PsiElement elseKeyword = ifSt.getElseKeyword();
    if (branch == null || elseKeyword == null) return null;

    return expandToWholeLine(editorText, new TextRange(elseKeyword.getTextRange().getStartOffset(), branch.getTextRange().getEndOffset()));
  }
}
