/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

public class BasePasteHandler extends EditorWriteActionHandler {
  protected Transferable myTransferable;

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return !editor.isViewer();
  }

  @Override
  public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    // We capture the contents to paste here, so it that it won't be affected by possible clipboard operations later (e.g. during unlocking
    // of current file for writing)
    myTransferable = getContentsToPaste(editor, dataContext);
    try {
      super.doExecute(editor, caret, dataContext);
    }
    catch (EditorCopyPasteHelper.TooLargeContentException e) {
      contentLengthLimitExceededMessage(e.getContentLength());
    }
    finally {
      myTransferable = null;
    }
  }

  @Override
  public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (myTransferable != null) {
      EditorCopyPasteHelper.getInstance().pasteTransferable(editor, myTransferable);
    }
  }

  protected Transferable getContentsToPaste(Editor editor, DataContext dataContext) {
    Producer<Transferable> producer = PasteAction.TRANSFERABLE_PROVIDER.getData(dataContext); 
    return EditorModificationUtil.getContentsToPasteToEditor(producer);
  }

  public static boolean isContentTooLarge(int contentLength) {
    return contentLength > FileSizeLimit.getContentLoadLimit();
  }

  public static void contentLengthLimitExceededMessage(int contentLength) {
    Messages.showErrorDialog(EditorBundle.message("content.to.paste.too.large.message",
                                                  StringUtil.formatFileSize(contentLength),
                                                  StringUtil.formatFileSize(FileSizeLimit.getContentLoadLimit())),
                             EditorBundle.message("content.to.paste.too.large.title"));
  }
}
