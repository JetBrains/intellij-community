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

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Transferable;

public class CopyAction extends TextComponentEditorAction implements HintManagerImpl.ActionToIgnore {

  public static final String SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY = "editor.skip.copy.and.cut.for.empty.selection";

  public CopyAction() {
    super(new Handler());
  }

  private static class Handler extends EditorActionHandler {
    @Override
    public void doExecute(@NotNull final Editor editor, @Nullable Caret caret, DataContext dataContext) {
      if (!editor.getSelectionModel().hasSelection(true)) {
        if (Registry.is(SKIP_COPY_AND_CUT_FOR_EMPTY_SELECTION_KEY)) {
          return;
        }
        editor.getCaretModel().runForEachCaret(__ -> {
          editor.getSelectionModel().selectLineAtCaret();
          EditorActionUtil.moveCaretToLineStartIgnoringSoftWraps(editor);
        });
      }
      editor.getSelectionModel().copySelectionToClipboard();
    }
  }

  public interface TransferableProvider {
    @Nullable Transferable getSelection(@NotNull Editor editor);
  }

  public static @Nullable Transferable getSelection(@NotNull Editor editor) {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_COPY);
    if (!(action instanceof EditorAction)) return null;
    TransferableProvider provider = ((EditorAction)action).getHandlerOfType(TransferableProvider.class);
    return provider == null ? null : provider.getSelection(editor);
  }
}
