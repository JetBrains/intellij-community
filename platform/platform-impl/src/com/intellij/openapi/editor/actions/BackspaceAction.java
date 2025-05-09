// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actionSystem.LatencyAwareEditorAction;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

public final class BackspaceAction extends TextComponentEditorAction implements LatencyAwareEditorAction, HintManagerImpl.ActionToIgnore {
  public BackspaceAction() {
    super(new Handler());
  }

  private static final class Handler extends EditorWriteActionHandler.ForEachCaret {
    @Override
    public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      EditorUIUtil.hideCursorInEditor(editor);
      CommandProcessor.getInstance().setCurrentCommandGroupId(EditorActionUtil.DELETE_COMMAND_GROUP);
      // manipulate actual document/editor instead of injected
      // since the latter have trouble finding the right location of caret movement in the case of multi-shred injected fragments
      editor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
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
        editor.getSelectionModel().removeSelection();

        Runnable runnable = () -> {
          FoldRegion region = editor.getFoldingModel().getCollapsedRegionAtOffset(offset - 1);
          if (region != null && region.shouldNeverExpand()) {
            document.deleteString(region.getStartOffset(), region.getEndOffset());
            editor.getCaretModel().moveToOffset(region.getStartOffset());
          }
          else {
            int prevOffset = DocumentUtil.getPreviousCodePointOffset(document, offset);
            if (prevOffset >= 0) {
              document.deleteString(prevOffset, offset);
            }
          }
          EditorModificationUtilEx.scrollToCaret(editor);
        };
        EditorUtil.runWithAnimationDisabled(editor, runnable);
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
