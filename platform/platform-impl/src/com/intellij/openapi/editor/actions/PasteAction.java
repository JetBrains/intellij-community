// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCopyPasteHelper;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.Transferable;

public class PasteAction extends TextComponentEditorAction {
  public static final DataKey<Producer<Transferable>> TRANSFERABLE_PROVIDER = DataKey.create("PasteTransferableProvider");

  public PasteAction() {
    super(new Handler());
  }

  private static class Handler extends BasePasteHandler {
    @Override
    public void executeWriteAction(@NotNull Editor editor, Caret caret, DataContext dataContext) {
      TextRange range = null;
      if (myTransferable != null) {
        TextRange[] ranges = EditorCopyPasteHelper.getInstance().pasteTransferable(editor, myTransferable);
        if (ranges != null && ranges.length == 1) {
          range = ranges[0];
        }
      }
      editor.putUserData(EditorEx.LAST_PASTED_REGION, range);
    }
  }
}
