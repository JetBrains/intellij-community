// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

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
      if (!StringUtil.isEmpty(selectedText) && !selectedText.contains("$")) {
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

        final String newText = String.valueOf(c) + selectedText + c;
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
        if (!multipleCarets) {
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
    }
    return super.beforeSelectionRemoved(c, project, editor, file);
  }
}
