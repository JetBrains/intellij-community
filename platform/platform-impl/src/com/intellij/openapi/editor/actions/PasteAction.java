// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCopyPasteHelper;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorTextInsertHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Producer;

import java.awt.datatransfer.Transferable;

/**
 * @author max
 * @since May 13, 2002
 */
public class PasteAction extends TextComponentEditorAction {
  private static final Logger LOG = Logger.getInstance(PasteAction.class);

  public static final DataKey<Producer<Transferable>> TRANSFERABLE_PROVIDER = DataKey.create("PasteTransferableProvider");

  public PasteAction() {
    super(new Handler());
  }

  @Override
  public void update(Editor editor, Presentation presentation, DataContext dataContext) {
    EditorActionHandler handler = getHandler();
    if (!(handler instanceof EditorTextInsertHandler)) {
      LOG.error("Handler for " + IdeActions.ACTION_EDITOR_PASTE + " action (" + (handler == null ? null : handler.getClass()) + 
                ") should implement com.intellij.openapi.editor.actionSystem.EditorTextInsertHandler");
    }
    super.update(editor, presentation, dataContext);
  }

  private static class Handler extends BasePasteHandler {
    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
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
