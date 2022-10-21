// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.LineNumberConverter;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.EditorTextFieldCellRenderer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.usages.impl.UsagePreviewPanel.calculateHighlightingRange;

public class UsageCodeSnippetComponent extends EditorTextFieldCellRenderer.SimpleWithGutterRendererComponent {
  public static final int CONTEXT_LINE_NUMBER = 3;
  private final ProperTextRange myInfoRange;

  public UsageCodeSnippetComponent(@NotNull PsiElement element, @Nullable ProperTextRange infoRange) {
    super(element.getProject(), element.getLanguage(), false);
    myInfoRange = infoRange;
    setupEditor();
    addUsagePreview(element);
  }

  private void setupEditor() {
    EditorEx editor = getEditor();
    JScrollPane scrollPane = editor.getScrollPane();
    editor.setBorder(JBUI.Borders.empty());
    editor.getMarkupModel().removeAllHighlighters();
    editor.getGutterComponentEx().setPaintBackground(false);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    EditorSettings settings = editor.getSettings();
    settings.setLineNumbersShown(true);
    settings.setAdditionalLinesCount(0);
    settings.setLineCursorWidth(1);
    settings.setDndEnabled(false);
    settings.setUseSoftWraps(false);
  }

  private void highlightRange(@NotNull TextRange rangeToHighlight) {
    final MarkupModelEx markupModel = getEditor().getMarkupModel();
    markupModel.addRangeHighlighter(EditorColors.SEARCH_RESULT_ATTRIBUTES, rangeToHighlight.getStartOffset(),
                                    rangeToHighlight.getEndOffset(), HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE);
  }

  public void addUsagePreview(@NotNull PsiElement element) {
    PsiDocumentManager docManager = PsiDocumentManager.getInstance(element.getProject());
    Document doc = docManager.getDocument(element.getContainingFile());
    if (doc == null) return;
    TextRange selectionRange = calculateHighlightingRange(element.getProject(), false, element, myInfoRange);
    int usageStartLineNumber = doc.getLineNumber(selectionRange.getStartOffset());
    int usageEndLineNumber = doc.getLineNumber(selectionRange.getEndOffset());
    final int contextStartLineNumber = Math.max(0, usageStartLineNumber - CONTEXT_LINE_NUMBER);
    int startOffset = doc.getLineStartOffset(contextStartLineNumber);
    int endOffset = doc.getLineEndOffset(Math.min(usageEndLineNumber + CONTEXT_LINE_NUMBER, doc.getLineCount() - 1));
    getEditor().getGutterComponentEx().setLineNumberConverter(
      contextStartLineNumber == 0 ? LineNumberConverter.DEFAULT : new LineNumberConverter.Increasing() {
        @Override
        public Integer convert(@NotNull Editor editor, int lineNumber) {
          return lineNumber + contextStartLineNumber;
        }
      });
    setText(doc.getText(new TextRange(startOffset, endOffset)));
    getEditor().getGutterComponentEx().updateUI();
    highlightRange(selectionRange.shiftLeft(startOffset));
  }
}


