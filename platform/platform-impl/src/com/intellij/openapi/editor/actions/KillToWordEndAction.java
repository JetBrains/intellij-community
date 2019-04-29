// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.ide.KillRingTransferable;

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
    public static final CaretStopPolicy WORD_END_IGNORE_LINE_BREAK = new CaretStopPolicy(CaretStop.END, CaretStop.NONE);

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      boolean camelMode = editor.getSettings().isCamelWords();
      int startOffset = editor.getCaretModel().getOffset();
      int endOffset = EditorActionUtil.getNextCaretStopOffset(editor, WORD_END_IGNORE_LINE_BREAK, camelMode, true);
      if (startOffset != endOffset) {
        KillRingUtil.cut(editor, startOffset, endOffset);
      }
    }
  }
}
