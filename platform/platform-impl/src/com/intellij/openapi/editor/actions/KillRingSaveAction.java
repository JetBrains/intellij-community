/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.KillRingTransferable;

/**
 * Stands for emacs <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Other-Kill-Commands.html">kill-ring-save</a> command.
 * <p/>
 * Generally, it puts currently selected text to the {@link KillRingTransferable kill ring}.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 4/19/11 6:06 PM
 */
public class KillRingSaveAction extends TextComponentEditorAction {

  public KillRingSaveAction() {
    super(new Handler(false));
  }

  static class Handler extends EditorWriteActionHandler {
    
    private final boolean myRemove;

    Handler(boolean remove) {
      myRemove = remove;
    }

    @Override
    public void executeWriteAction(Editor editor, DataContext dataContext) {
      SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) {
        return;
      }

      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();
      if (start >= end) {
        return;
      }
      Document document = editor.getDocument();
      KillRingUtil.copyToKillRing(document, start, end, false);
      if (myRemove) {
        document.deleteString(start, end);
      }
    }
  }
}
