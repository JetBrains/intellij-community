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
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.KillRingTransferable;
import com.intellij.openapi.util.TextRange;

/**
 * Stands for emacs <a href="http://www.gnu.org/software/emacs/manual/html_node/emacs/Words.html#Words">kill-word</a> command.
 * <p/>
 * Generally, it removes text from the current cursor position up to the end of the current word and puts
 * it to the {@link KillRingTransferable kill ring}.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 */
public class KillToWordEndAction extends TextComponentEditorAction {

  public KillToWordEndAction() {
    super(new Handler());
  }
  
  private static class Handler extends EditorWriteActionHandler {
    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      boolean camelMode = editor.getSettings().isCamelWords();
      final TextRange range = EditorActionUtil.getRangeToWordEnd(editor, camelMode, true);
      if (!range.isEmpty()) {
        KillRingUtil.cut(editor, range.getStartOffset(), range.getEndOffset());
      }
    }
  }
}
