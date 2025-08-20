// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@TestOnly
@ApiStatus.Internal
public final class EditorCaretTestUtil {
  public static final String CARET_TAG = "<caret>";
  public static final String CARET_TAG_PREFIX = CARET_TAG.substring(0, CARET_TAG.length() - 1);

  public static final String SELECTION_START_TAG = "<selection>";
  public static final String SELECTION_END_TAG = "</selection>";
  public static final String BLOCK_SELECTION_START_TAG = "<block>";
  public static final String BLOCK_SELECTION_END_TAG = "</block>";

  public static class CaretInfo {
    public final @Nullable LogicalPosition position; // column number in this position is calculated in terms of characters,
    // not in terms of visual position
    // so Tab character always increases the column number by 1
    public final @Nullable TextRange selection;

    public CaretInfo(@Nullable LogicalPosition position, @Nullable TextRange selection) {
      this.position = position;
      this.selection = selection;
    }

    public int getCaretOffset(Document document) {
      return position == null ? -1 : document.getLineStartOffset(position.line) + position.column;
    }
  }

  public record CaretAndSelectionState(List<CaretInfo> carets, @Nullable TextRange blockSelection) {

    /**
     * Returns true if current CaretAndSelectionState contains at least one caret or selection explicitly specified
     */
    public boolean hasExplicitCaret() {
      if(carets.isEmpty()) return false;
      if(blockSelection == null && carets.size() == 1) {
        CaretInfo caret = carets.get(0);
        return caret.position != null || caret.selection != null;
      }
      return true;
    }
  }


  public static @NotNull CaretAndSelectionState extractCaretAndSelectionMarkers(@NotNull Document document) {
    return extractCaretAndSelectionMarkers(document, true);
  }

  public static @NotNull CaretAndSelectionState extractCaretAndSelectionMarkers(@NotNull Document document, final boolean processBlockSelection) {
    return WriteCommandAction.writeCommandAction(null).compute(() -> extractCaretAndSelectionMarkersImpl(document, processBlockSelection));
  }
  public static @NotNull CaretAndSelectionState extractCaretAndSelectionMarkersImpl(@NotNull Document document, boolean processBlockSelection) {
    List<CaretInfo> carets = new ArrayList<>();
    String fileText = document.getText();

    RangeMarker blockSelectionStartMarker = null;
    RangeMarker blockSelectionEndMarker = null;
    if (processBlockSelection) {
      int blockSelectionStart = fileText.indexOf(BLOCK_SELECTION_START_TAG);
      int blockSelectionEnd = fileText.indexOf(BLOCK_SELECTION_END_TAG);
      if ((blockSelectionStart ^ blockSelectionEnd) < 0) {
        throw new IllegalArgumentException("Both block selection opening and closing tag must be present");
      }
      if (blockSelectionStart >= 0) {
        blockSelectionStartMarker = document.createRangeMarker(blockSelectionStart, blockSelectionStart);
        blockSelectionEndMarker = document.createRangeMarker(blockSelectionEnd, blockSelectionEnd);
        document.deleteString(blockSelectionStartMarker.getStartOffset(), blockSelectionStartMarker.getStartOffset() + BLOCK_SELECTION_START_TAG.length());
        document.deleteString(blockSelectionEndMarker.getStartOffset(), blockSelectionEndMarker.getStartOffset() + BLOCK_SELECTION_END_TAG.length());
      }
    }

    boolean multiCaret = StringUtil.getOccurrenceCount(document.getText(), CARET_TAG) > 1
                         || StringUtil.getOccurrenceCount(document.getText(), SELECTION_START_TAG) > 1;
    int pos = 0;
    while (pos < document.getTextLength()) {
      fileText = document.getText();
      int caretIndex = fileText.indexOf(CARET_TAG, pos);
      int selStartIndex = fileText.indexOf(SELECTION_START_TAG, pos);
      int selEndIndex = fileText.indexOf(SELECTION_END_TAG, pos);

      if ((selStartIndex ^ selEndIndex) < 0) {
        selStartIndex = -1;
        selEndIndex = -1;
      }
      if (0 <= selEndIndex && selEndIndex < selStartIndex) {
        throw new IllegalArgumentException("Wrong order of selection opening and closing tags");
      }
      if (caretIndex < 0 && selStartIndex < 0 && selEndIndex < 0) {
        break;
      }
      if (multiCaret && 0 <= caretIndex && caretIndex < selStartIndex) {
        selStartIndex = -1;
        selEndIndex = -1;
      }
      if (multiCaret && caretIndex > selEndIndex && selEndIndex >= 0) {
        caretIndex = -1;
      }

      final RangeMarker caretMarker = caretIndex >= 0 ? document.createRangeMarker(caretIndex, caretIndex) : null;
      final RangeMarker selStartMarker = selStartIndex >= 0
                                         ? document.createRangeMarker(selStartIndex, selStartIndex)
                                         : null;
      final RangeMarker selEndMarker = selEndIndex >= 0
                                       ? document.createRangeMarker(selEndIndex, selEndIndex)
                                       : null;

      if (caretMarker != null) {
        document.deleteString(caretMarker.getStartOffset(), caretMarker.getStartOffset() + CARET_TAG.length());
      }
      if (selStartMarker != null) {
        document.deleteString(selStartMarker.getStartOffset(),
                              selStartMarker.getStartOffset() + SELECTION_START_TAG.length());
      }
      if (selEndMarker != null) {
        document.deleteString(selEndMarker.getStartOffset(),
                              selEndMarker.getStartOffset() + SELECTION_END_TAG.length());
      }
      LogicalPosition caretPosition = null;
      if (caretMarker != null) {
        int line = document.getLineNumber(caretMarker.getStartOffset());
        int column = caretMarker.getStartOffset() - document.getLineStartOffset(line);
        caretPosition = new LogicalPosition(line, column);
      }
      carets.add(new CaretInfo(caretPosition,
                               selStartMarker == null || selEndMarker == null
                               ? null
                               : new TextRange(selStartMarker.getStartOffset(), selEndMarker.getEndOffset())));

      pos = Math.max(caretMarker == null ? -1 : caretMarker.getStartOffset(), selEndMarker == null ? -1 : selEndMarker.getEndOffset());
    }
    if (carets.isEmpty()) {
      carets.add(new CaretInfo(null, null));
    }
    TextRange blockSelection = null;
    if (blockSelectionStartMarker != null) {
      blockSelection = new TextRange(blockSelectionStartMarker.getStartOffset(), blockSelectionEndMarker.getStartOffset());
    }
    return new CaretAndSelectionState(Arrays.asList(carets.toArray(new CaretInfo[0])), blockSelection);
  }

  public static void setCaretsAndSelection(Editor editor, CaretAndSelectionState caretsState) {
    CaretModel caretModel = editor.getCaretModel();
    List<CaretState> states = new ArrayList<>(caretsState.carets().size());
    for (CaretInfo caret : caretsState.carets()) {
      states.add(new CaretState(caret.position == null ? null : editor.offsetToLogicalPosition(caret.getCaretOffset(editor.getDocument())),
                                caret.selection == null ? null : editor.offsetToLogicalPosition(caret.selection.getStartOffset()),
                                caret.selection == null ? null : editor.offsetToLogicalPosition(caret.selection.getEndOffset())));
    }
    caretModel.setCaretsAndSelections(states);
    if (caretsState.blockSelection() != null) {
      editor.getSelectionModel().setBlockSelection(editor.offsetToLogicalPosition(caretsState.blockSelection().getStartOffset()),
                                                   editor.offsetToLogicalPosition(caretsState.blockSelection().getEndOffset()));
    }
  }
}
