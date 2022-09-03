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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

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
      String caretSelectedText = carets.get(i).getSelectedText();
      startOffsets[i] = buf.length();
      if (caretSelectedText != null) {
        buf.append(caretSelectedText);
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
      boolean isInsertingEntireLineAboveCaret = copyPasteOptions.isEntireLineFromEmptySelection() &&
                                                !editor.getSelectionModel().hasSelection(true);

      final TextRange[] ranges = new TextRange[caretCount];
      final Iterator<String> segments = new ClipboardTextPerCaretSplitter().split(text, caretData, caretCount).iterator();
      final int[] index = {0};
      caretModel.runForEachCaret(caret -> {
        String normalizedText = normalizeText(editor, segments.next());
        ranges[index[0]++] = isInsertingEntireLineAboveCaret ? insertEntireLineAboveCaret(editor, normalizedText)
                                                             : insertStringAtCaret(editor, normalizedText);
      });
      return ranges;
    }
    else {
      String normalizedText = normalizeText(editor, text);
      TextRange textRange = insertStringAtCaret(editor, normalizedText);
      return new TextRange[]{textRange};
    }
  }

  public static @NotNull TextRange insertEntireLineAboveCaret(@NotNull Editor editor, @NotNull String text) {
    int caretOffset = editor.getCaretModel().getOffset();
    int lineStartOffset = EditorUtil.getNotFoldedLineStartOffset(editor, caretOffset);
    editor.getDocument().insertString(lineStartOffset, text);
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
