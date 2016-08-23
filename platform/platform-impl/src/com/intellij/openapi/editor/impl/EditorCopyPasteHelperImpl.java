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
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.editorActions.TextBlockTransferable;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class EditorCopyPasteHelperImpl extends EditorCopyPasteHelper {
  private static final Logger LOG = Logger.getInstance(EditorCopyPasteHelperImpl.class);

  @Override
  public void copySelectionToClipboard(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<TextBlockTransferableData> extraData = new ArrayList<>();
    String s = editor.getCaretModel().supportsMultipleCarets() ? getSelectedTextForClipboard(editor, extraData)
                                                               : editor.getSelectionModel().getSelectedText();
    if (s == null) return;

    s = TextBlockTransferable.convertLineSeparators(s, "\n", extraData);
    Transferable contents = editor.getCaretModel().supportsMultipleCarets() ? new TextBlockTransferable(s, extraData, null) : new StringSelection(s);
    CopyPasteManager.getInstance().setContents(contents);
  }

  public static String getSelectedTextForClipboard(@NotNull Editor editor, @NotNull Collection<TextBlockTransferableData> extraDataCollector) {
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
    return buf.toString();
  }

  @Nullable
  @Override
  public TextRange[] pasteFromClipboard(@NotNull Editor editor) {
    Transferable transferable = EditorModificationUtil.getContentsToPasteToEditor(null);
    return transferable == null ? null : pasteTransferable(editor, transferable);
  }

  @Nullable
  @Override
  public TextRange[] pasteTransferable(final @NotNull Editor editor, @NotNull Transferable content) {
    String text = EditorModificationUtil.getStringContent(content);
    if (text == null) return null;

    if (editor.getCaretModel().supportsMultipleCarets()) {
      CaretStateTransferableData caretData = null;
      int caretCount = editor.getCaretModel().getCaretCount();
      if (caretCount == 1 && editor.isColumnMode()) {
        int pastedLineCount = LineTokenizer.calcLineCount(text, true);
        EditorModificationUtil.deleteSelectedText(editor);
        Caret caret = editor.getCaretModel().getPrimaryCaret();
        for (int i = 0; i < pastedLineCount - 1; i++) {
          caret = caret.clone(false);
          if (caret == null) {
            break;
          }
        }
        caretCount = editor.getCaretModel().getCaretCount();
      }
      else {
        try {
          caretData = content.isDataFlavorSupported(CaretStateTransferableData.FLAVOR)
                      ? (CaretStateTransferableData)content.getTransferData(CaretStateTransferableData.FLAVOR) : null;
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      final TextRange[] ranges = new TextRange[caretCount];
      final Iterator<String> segments = new ClipboardTextPerCaretSplitter().split(text, caretData, caretCount).iterator();
      final int[] index = {0};
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          String normalizedText = TextBlockTransferable.convertLineSeparators(editor, segments.next());
          int caretOffset = caret.getOffset();
          ranges[index[0]++] = new TextRange(caretOffset, caretOffset + normalizedText.length());
          EditorModificationUtil.insertStringAtCaret(editor, normalizedText, false, true);
        }
      });
      return ranges;
    }
    else {
      int caretOffset = editor.getCaretModel().getOffset();
      String normalizedText = TextBlockTransferable.convertLineSeparators(editor, text);
      EditorModificationUtil.insertStringAtCaret(editor, normalizedText, false, true);
      return new TextRange[]{new TextRange(caretOffset, caretOffset + text.length())};
    }
  }
}
