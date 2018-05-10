// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.actions;

import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actionSystem.LatencyAwareEditorAction;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

public class BackspaceAction extends TextComponentEditorAction implements LatencyAwareEditorAction {
  public BackspaceAction() {
    super(new Handler());
  }

  private static class Handler extends EditorWriteActionHandler {
    private Handler() {
      super(true);
    }

    @Override
    public void executeWriteAction(Editor editor, Caret caret, DataContext dataContext) {
      EditorUIUtil.hideCursorInEditor(editor);
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      if (editor instanceof EditorWindow) {
        // manipulate actual document/editor instead of injected
        // since the latter have trouble finding the right location of caret movement in the case of multi-shred injected fragments
        editor = ((EditorWindow)editor).getDelegate();
      }
      doBackSpaceAtCaret(editor);
    }
  }

  private static void doBackSpaceAtCaret(@NotNull Editor editor) {
    VisualPosition caretPosition = editor.getCaretModel().getVisualPosition();
    if (caretPosition.column > 0 &&
        editor.getInlayModel().hasInlineElementAt(new VisualPosition(caretPosition.line, caretPosition.column - 1))) {
      editor.getCaretModel().moveCaretRelatively(-1, 0, false, false, EditorUtil.isCurrentCaretPrimary(editor));
      return;
    }

    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    int colNumber = editor.getCaretModel().getLogicalPosition().column;
    Document document = editor.getDocument();
    int offset = editor.getCaretModel().getOffset();
    if(colNumber > 0) {
      if(EditorModificationUtil.calcAfterLineEnd(editor) > 0) {
        int columnShift = -1;
        editor.getCaretModel().moveCaretRelatively(columnShift, 0, false, false, true);
      }
      else {
        EditorModificationUtil.scrollToCaret(editor);
        editor.getSelectionModel().removeSelection();

        FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1);
        if (region != null && region.shouldNeverExpand()) {
          document.deleteString(region.getStartOffset(), region.getEndOffset());
          editor.getCaretModel().moveToOffset(region.getStartOffset());
        }
        else {
          document.deleteString(DocumentUtil.getPreviousCodePointOffset(document, offset), offset);
        }
      }
    }
    else if(lineNumber > 0) {
      int separatorLength = document.getLineSeparatorLength(lineNumber - 1);
      int lineEnd = document.getLineEndOffset(lineNumber - 1) + separatorLength;
      document.deleteString(lineEnd - separatorLength, lineEnd);
      editor.getCaretModel().moveToOffset(lineEnd - separatorLength);
      EditorModificationUtil.scrollToCaret(editor);
      editor.getSelectionModel().removeSelection();
      // Do not group delete newline and other deletions.
      CommandProcessor commandProcessor = CommandProcessor.getInstance();
      commandProcessor.setCurrentCommandGroupId(null);
    }
  }
}
