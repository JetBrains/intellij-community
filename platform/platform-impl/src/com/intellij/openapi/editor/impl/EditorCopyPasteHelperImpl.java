// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.editorActions.TextBlockTransferable;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actions.BasePasteHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.BiPredicate;

public class EditorCopyPasteHelperImpl extends EditorCopyPasteHelper {
  @Override
  public @Nullable Transferable getSelectionTransferable(@NotNull Editor editor, @NotNull CopyPasteOptions options) {
    if (editor.getContentComponent() instanceof JPasswordField) return null;

    ApplicationManager.getApplication().assertIsDispatchThread();
    List<TextBlockTransferableData> extraData = new ArrayList<>();
    String s = editor.getCaretModel().supportsMultipleCarets() ? getSelectedTextForClipboard(editor, options, extraData)
                                                               : editor.getSelectionModel().getSelectedText();
    if (StringUtil.isEmpty(s)) return null;

    s = TextBlockTransferable.convertLineSeparators(s, "\n", extraData);
    return editor.getCaretModel().supportsMultipleCarets() ? new TextBlockTransferable(s, extraData, null)
                                                           : new StringSelection(s);
  }

  @SuppressWarnings("unused")  // external usages
  public static String getSelectedTextForClipboard(@NotNull Editor editor,
                                                   @NotNull Collection<? super TextBlockTransferableData> extraDataCollector) {
    return getSelectedTextForClipboard(editor, CopyPasteOptions.DEFAULT, extraDataCollector);
  }

  public static String getSelectedTextForClipboard(@NotNull Editor editor, @NotNull CopyPasteOptions options,
                                                   @NotNull Collection<? super TextBlockTransferableData> extraDataCollector) {
    final StringBuilder buf = new StringBuilder();
    String separator = "";
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    int[] startOffsets = new int[carets.size()];
    int[] endOffsets = new int[carets.size()];
    for (int i = 0; i < carets.size(); i++) {
      buf.append(separator);
      String caretSelectedText = StringUtil.notNullize(carets.get(i).getSelectedText());
      startOffsets[i] = buf.length();
      buf.append(caretSelectedText);
      if (options.isCopiedFromEmptySelection() && !caretSelectedText.endsWith("\n")) {
        buf.append("\n");  // make sure the last line with no '\n' is still copied as a real line
      }
      endOffsets[i] = buf.length();
      separator = "\n";
    }
    extraDataCollector.add(new CaretStateTransferableData(startOffsets, endOffsets));
    extraDataCollector.add(new CopyPasteOptionsTransferableData(options));
    return buf.toString();
  }

  @Override
  public TextRange @Nullable [] pasteFromClipboard(@NotNull Editor editor) throws TooLargeContentException {
    Transferable transferable = EditorModificationUtil.getContentsToPasteToEditor(null);
    return transferable == null ? null : pasteTransferable(editor, transferable);
  }

  @Override
  public TextRange @Nullable [] pasteTransferable(final @NotNull Editor editor, @NotNull Transferable content) throws TooLargeContentException {
    String text = EditorModificationUtil.getStringContent(content);
    if (text == null) return null;

    int textLength = text.length();
    if (BasePasteHandler.isContentTooLarge(textLength)) throw new TooLargeContentException(textLength);

    CaretModel caretModel = editor.getCaretModel();
    if (caretModel.supportsMultipleCarets()) {
      CaretStateTransferableData caretData = null;
      int caretCount = caretModel.getCaretCount();
      if (caretCount == 1 && editor.isColumnMode()) {
        int pastedLineCount = LineTokenizer.calcLineCount(text, true);
        if (pastedLineCount <= caretModel.getMaxCaretCount()) {
          EditorModificationUtilEx.deleteSelectedText(editor);
          Caret caret = caretModel.getPrimaryCaret();
          for (int i = 0; i < pastedLineCount - 1; i++) {
            caret = caret.clone(false);
            if (caret == null) {
              break;
            }
          }
          caretCount = caretModel.getCaretCount();
        }
      }
      else {
        caretData = CaretStateTransferableData.getFrom(content);
      }
      CopyPasteOptions copyPasteOptions = CopyPasteOptionsTransferableData.valueFromTransferable(content);
      boolean isInsertingEntireLineAboveCaret = copyPasteOptions.isCopiedFromEmptySelection() &&
                                                !editor.getSelectionModel().hasSelection(true);

      final TextRange[] ranges = new TextRange[caretCount];
      final Iterator<String> segments = new ClipboardTextPerCaretSplitter().split(text, caretData, caretCount).iterator();
      final int[] index = {0};
      if (isInsertingEntireLineAboveCaret) {
        caretModel.runBatchCaretOperation(() -> {
          List<CaretLineState> caretLineStateList = ContainerUtil.map(caretModel.getAllCarets(), CaretLineState::create);
          List<List<CaretLineState>> caretsGroupedByLine = groupSublistRuns(caretLineStateList, CaretLineState::isOnSameLine);
          int shift = 0;
          for (List<CaretLineState> caretsOnSameLine : caretsGroupedByLine) {
            int lineStartOffset = caretsOnSameLine.get(0).lineStartOffset + shift;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < caretsOnSameLine.size(); i++) {
              String normalizedText = normalizeText(editor, segments.next());
              ranges[index[0]++] = appendEntireLine(sb, normalizedText).shiftRight(lineStartOffset);
            }
            editor.getDocument().insertString(lineStartOffset, sb);
            for (CaretLineState caretLineState : caretsOnSameLine) {
              caretLineState.moveCaretAfterInsertionIfNeeded(lineStartOffset, sb.length());
            }
            shift += sb.length();
          }
        });
        EditorModificationUtilEx.scrollToCaret(editor);
      }
      else {
        caretModel.runForEachCaret(caret -> {
          String normalizedText = normalizeText(editor, segments.next());
          ranges[index[0]++] = insertStringAtCaret(editor, normalizedText);
        });
      }
      return ranges;
    }
    else {
      String normalizedText = normalizeText(editor, text);
      TextRange textRange = insertStringAtCaret(editor, normalizedText);
      return new TextRange[]{textRange};
    }
  }
  private static <T> @NotNull List<List<T>> groupSublistRuns(@NotNull List<T> list,
                                                             @NotNull BiPredicate<? super T, ? super T> equality) {
    if (list.isEmpty()) {
      return Collections.emptyList();
    }

    List<List<T>> result = new ArrayList<>();
    int lastIndex = 0;
    for (int i = 0, size = list.size(); i < size; i++) {
      if (i == size - 1 || !equality.test(list.get(i), list.get(i + 1))) {
        result.add(list.subList(lastIndex, i + 1));
        lastIndex = i + 1;
      }
    }
    return result;
  }

  private record CaretLineState(
    @NotNull Caret caret,
    @NotNull VisualPosition position,
    int lineStartOffset,
    @NotNull VisualPosition lineStartPosition
  ) {
    static @NotNull CaretLineState create(@NotNull Caret caret) {
      Editor editor = caret.getEditor();
      int lineStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, caret.getOffset());
      VisualPosition lineStartPosition = editor.offsetToVisualPosition(lineStartOffset);
      return new CaretLineState(caret, caret.getVisualPosition(), lineStartOffset, lineStartPosition);
    }

    void moveCaretAfterInsertionIfNeeded(int insertionLineStartOffset, int insertionLength) {
      if (caret.getOffset() != insertionLineStartOffset) {
        return;
      }
      int newLineStartOffset = insertionLineStartOffset + insertionLength;
      VisualPosition newLineStartPosition = caret.getEditor().offsetToVisualPosition(newLineStartOffset);
      int lineShift = newLineStartPosition.line - lineStartPosition.line;
      VisualPosition newPosition = new VisualPosition(position.line + lineShift,
                                                      position.column,
                                                      position.leansRight);
      caret.moveToVisualPosition(newPosition);
      // the following shouldn't happen, but just to be sure
      if (caret.getOffset() != newLineStartOffset) {
        caret.moveToOffset(newLineStartOffset);
      }
    }

    boolean isOnSameLine(@NotNull CaretLineState other) {
      return lineStartOffset == other.lineStartOffset;
    }
  }

  private static @NotNull TextRange appendEntireLine(@NotNull StringBuilder sb, @NotNull String line) {
    int startOffset = sb.length();
    sb.append(line);
    if (!line.endsWith("\n")) {
      sb.append("\n");
    }
    int endOffset = sb.length();
    return TextRange.create(startOffset, endOffset);
  }

  public static @NotNull TextRange insertEntireLineAboveCaret(@NotNull Editor editor, @NotNull String text) {
    Caret caret = editor.getCaretModel().getCurrentCaret();
    CaretLineState caretLineState = CaretLineState.create(caret);
    int lineStartOffset = caretLineState.lineStartOffset;
    if (!text.endsWith("\n")) text += "\n";
    editor.getDocument().insertString(lineStartOffset, text);
    caretLineState.moveCaretAfterInsertionIfNeeded(lineStartOffset, text.length());
    EditorModificationUtilEx.scrollToCaret(editor);
    return TextRange.from(lineStartOffset, text.length());
  }

  public static @NotNull TextRange insertStringAtCaret(@NotNull Editor editor, @NotNull String text) {
    int caretOffset = editor.getSelectionModel().getSelectionStart();
    int newOffset = EditorModificationUtilEx.insertStringAtCaret(editor, text, false, true);
    return new TextRange(caretOffset, newOffset);
  }

  private static @NotNull String normalizeText(@NotNull Editor editor, @NotNull String text) {
    text = TextBlockTransferable.convertLineSeparators(editor, text);
    text = trimTextIfNeeded(editor, text);
    return text;
  }

  private static String trimTextIfNeeded(Editor editor, String text) {
    JComponent contentComponent = editor.getContentComponent();
    if (contentComponent instanceof JTextComponent) {
      Document document = ((JTextComponent)contentComponent).getDocument();
      if (document != null && document.getProperty(TRIM_TEXT_ON_PASTE_KEY) == Boolean.TRUE) {
        return text.trim();
      }
    }
    return text;
  }

  public static class CopyPasteOptionsTransferableData implements TextBlockTransferableData, Serializable {
    private static final DataFlavor FLAVOR = new DataFlavor(CopyPasteOptionsTransferableData.class,
                                                            "Copy/paste options");

    public final @NotNull CopyPasteOptions options;

    public CopyPasteOptionsTransferableData(@NotNull CopyPasteOptions options) {
      this.options = options;
    }

    @Override
    public @Nullable DataFlavor getFlavor() {
      return FLAVOR;
    }


    public static @NotNull CopyPasteOptions valueFromTransferable(@NotNull Transferable transferable) {
      CopyPasteOptionsTransferableData transferableData = fromTransferable(transferable);
      return transferableData == null ? CopyPasteOptions.DEFAULT : transferableData.options;
    }

    public static @Nullable CopyPasteOptionsTransferableData fromTransferable(@NotNull Transferable transferable) {
      try {
        return transferable.isDataFlavorSupported(FLAVOR) ? (CopyPasteOptionsTransferableData)transferable.getTransferData(FLAVOR) : null;
      }
      catch (IOException | UnsupportedFlavorException e) {
        return null;
      }
    }
  }
}
