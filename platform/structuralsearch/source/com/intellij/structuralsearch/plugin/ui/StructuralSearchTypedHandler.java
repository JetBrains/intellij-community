// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchTypedHandler extends TypedHandlerDelegate {

  @NotNull
  @Override
  public Result beforeSelectionRemoved(char c,
                                       @NotNull Project project,
                                       @NotNull Editor editor,
                                       @NotNull PsiFile file) {
    if (editor.getUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY) == null) {
      return Result.CONTINUE;
    }
    if (c == '$') {
      final SelectionModel selectionModel = editor.getSelectionModel();
      final String selectedText = selectionModel.getSelectedText();
      if (!StringUtil.isEmpty(selectedText)) {
        if (selectedText.contains("$") || !CodeInsightSettings.getInstance().SURROUND_SELECTION_ON_QUOTE_TYPED) {
          return Result.CONTINUE;
        }
        final Document document = editor.getDocument();
        final List<RangeMarker> rangeMarkers = new SmartList<>();
        rangeMarkers.add(document.createRangeMarker(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd()));

        final CaretModel caretModel = editor.getCaretModel();
        final boolean multipleCarets = caretModel.getCaretCount() != 1;
        if (!multipleCarets) {
          PsiTreeUtil.processElements(file, element -> {
            if (StructuralSearchUtil.isIdentifier(element) && element.getText().equals(selectedText)) {
              rangeMarkers.add(document.createRangeMarker(element.getTextRange()));
            }
            return true;
          });
        }

        final String newText = c + selectedText + c;
        final boolean ltrSelection = selectionModel.getLeadSelectionOffset() != selectionModel.getSelectionEnd();
        for (RangeMarker marker : rangeMarkers) {
          if (marker.isValid()) {
            document.replaceString(marker.getStartOffset(), marker.getEndOffset(), newText);
            if (multipleCarets) {
              final int startOffset = marker.getStartOffset() + 1;
              final int endOffset = startOffset + selectedText.length();
              selectionModel.setSelection(ltrSelection ? startOffset : endOffset, ltrSelection ? endOffset : startOffset);
              caretModel.moveToOffset(ltrSelection ? endOffset : startOffset);
            }
          }
        }
        if (!multipleCarets && rangeMarkers.size() <= caretModel.getMaxCaretCount()) {
          final List<CaretState> newCaretStates = new SmartList<>();
          for (RangeMarker marker : rangeMarkers) {
            final int startOffset = marker.getStartOffset() + 1;
            final int endOffset = startOffset + selectedText.length();
            final LogicalPosition selectionStart = editor.offsetToLogicalPosition(ltrSelection ? startOffset : endOffset);
            final LogicalPosition selectionEnd = editor.offsetToLogicalPosition(ltrSelection ? endOffset : startOffset);
            final LogicalPosition caretPosition = editor.offsetToLogicalPosition(ltrSelection ? endOffset : startOffset);
            final CaretState state = new CaretState(caretPosition, selectionStart, selectionEnd);
            newCaretStates.add(state);
          }
          caretModel.setCaretsAndSelections(newCaretStates);
        }
        return Result.STOP;
      }
      else if (CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
        final Document document = editor.getDocument();
        final CaretModel caretModel = editor.getCaretModel();
        final Caret caret = caretModel.getCurrentCaret();
        final LogicalPosition position = caret.getLogicalPosition();
        final int lineStart = document.getLineStartOffset(position.line);
        final int lineEnd = document.getLineEndOffset(position.line);
        final CharSequence text = document.getCharsSequence();
        final int offset = lineStart + position.column;
        final boolean nextIsDollar = offset < text.length() && text.charAt(offset) == '$';
        if (hasOddDollar(text, lineStart, offset) && nextIsDollar) {
          caret.setSelection(offset, offset + 1);
        }
        else if (!hasOddDollar(text, lineStart, lineEnd)) {
          document.insertString(offset, "$");
        }
      }
    }
    return Result.CONTINUE;
  }

  static boolean hasOddDollar(CharSequence text, int start, int end) {
    boolean $ = false;
    for (int i = start; i < end; i++) {
      if (text.charAt(i) == '$') {
        $ = !$;
      }
    }
    return $;
  }
}
