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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import com.intellij.openapi.ide.KillRingTransferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stands for emacs <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Other-Kill-Commands.html">kill-ring-save</a> command.
 * <p/>
 * Generally, it puts currently selected text to the {@link KillRingTransferable kill ring}.
 * <p/>
 * Thread-safe.
 */
public class KillRingSaveAction extends TextComponentEditorAction {

  public KillRingSaveAction() {
    super(new Handler(false));
  }

  static class Handler extends EditorActionHandler {
    
    private final boolean myRemove;

    Handler(boolean remove) {
      myRemove = remove;
    }

    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      SelectionModel selectionModel = editor.getSelectionModel();
      if (!selectionModel.hasSelection()) {
        return;
      }

      final int start = selectionModel.getSelectionStart();
      final int end = selectionModel.getSelectionEnd();
      if (start >= end) {
        return;
      }
      KillRingUtil.copyToKillRing(editor, start, end, false);
      if (myRemove) {
        DocumentRunnable runnable = new DocumentRunnable(editor.getDocument(), editor.getProject()) {
          @Override
          public void run() {
            editor.getDocument().deleteString(start, end);
          }
        };
        if (editor instanceof TextComponentEditor) {
          runnable.run();
        } else {
          ApplicationManager.getApplication().runWriteAction(runnable);
        }
      }
    }

    @Override
    protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      return !myRemove || editor.getDocument().isWritable();
    }
  }
}
