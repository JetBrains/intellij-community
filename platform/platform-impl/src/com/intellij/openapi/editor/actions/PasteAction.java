/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCopyPasteHelper;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Producer;

import java.awt.datatransfer.Transferable;

/**
 * @author max
 * @since May 13, 2002
 */
public class PasteAction extends EditorAction {
  public static final DataKey<Producer<Transferable>> TRANSFERABLE_PROVIDER = DataKey.create("PasteTransferableProvider");

  public PasteAction() {
    super(new Handler());
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
