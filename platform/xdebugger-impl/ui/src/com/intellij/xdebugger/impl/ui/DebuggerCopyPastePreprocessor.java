// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DebuggerCopyPastePreprocessor implements CopyPastePreProcessor {
  public static final Key<Boolean> REMOVE_NEWLINES_ON_PASTE = new Key<>("REMOVE_NEWLINES_ON_PASTE");

  @Override
  public @Nullable String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @Override
  public @NotNull String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    if (editor.getUserData(REMOVE_NEWLINES_ON_PASTE) != null) {
      return StringUtil.convertLineSeparators(text, " ");
    }
    return text;
  }

  @Override
  public boolean requiresAllDocumentsToBeCommitted(@NotNull Editor editor, @NotNull Project project) {
    return false;
  }
}
