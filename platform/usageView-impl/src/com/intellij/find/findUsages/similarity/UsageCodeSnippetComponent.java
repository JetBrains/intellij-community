// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.lang.injection.InjectedLanguageManager;
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.ui.EditorTextFieldCellRenderer;
import com.intellij.ui.RemoteTransferUIManager;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class UsageCodeSnippetComponent extends EditorTextFieldCellRenderer.SimpleWithGutterRendererComponent {
  private static final int CONTEXT_LINE_NUMBER = 3;

  UsageCodeSnippetComponent(@NotNull SnippetRenderingData renderingData) {
    super(renderingData.getProject(), renderingData.getLanguage(), false);
    setupEditor();
    addUsagePreview(renderingData);
    getEditor().getGutterComponentEx().setLineNumberConverter(renderingData.getConverter());
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

    // This editor doesn't work properly in Remote Development, so we forbid it from conversion to BeControls
    // TODO Remove this after GTW-6748 is done
    RemoteTransferUIManager.forbidBeControlizationInLux(editor, "findUsages");
  }

  private void highlightRange(@NotNull TextRange rangeToHighlight) {
    final MarkupModelEx markupModel = getEditor().getMarkupModel();
    markupModel.addRangeHighlighter(EditorColors.SEARCH_RESULT_ATTRIBUTES, rangeToHighlight.getStartOffset(),
                                    rangeToHighlight.getEndOffset(), HighlighterLayer.ADDITIONAL_SYNTAX, HighlighterTargetArea.EXACT_RANGE);
  }

  private void addUsagePreview(SnippetRenderingData result) {
    if (result == null) return;
    setText(result.getText());
    getEditor().getGutterComponentEx().updateUI();
    getEditor().getMarkupModel().removeAllHighlighters();
    highlightRange(result.getSelectionRange());
  }

  public static @Nullable SnippetRenderingData calculateSnippetRenderingData(@NotNull PsiElement element,
                                                                             @Nullable ProperTextRange infoRange) {
    PsiDocumentManager docManager = PsiDocumentManager.getInstance(element.getProject());
    Document doc = docManager.getDocument(InjectedLanguageManager.getInstance(element.getProject()).getTopLevelFile(element));
    if (doc == null) return null;
    TextRange selectionRange = UsagePreviewPanel.calculateHighlightingRangeForUsage(element, infoRange);
    if (element instanceof PsiNamedElement && !(element instanceof PsiFile)) {
      selectionRange = UsagePreviewPanel.getNameElementTextRange(element);
    }
    selectionRange = InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, selectionRange);
    int usageStartLineNumber = doc.getLineNumber(selectionRange.getStartOffset());
    int usageEndLineNumber = doc.getLineNumber(selectionRange.getEndOffset());
    final int contextStartLineNumber = Math.max(0, usageStartLineNumber - CONTEXT_LINE_NUMBER);
    int startOffset = doc.getLineStartOffset(contextStartLineNumber);
    int endOffset = doc.getLineEndOffset(Math.min(usageEndLineNumber + CONTEXT_LINE_NUMBER, doc.getLineCount() - 1));
    selectionRange = selectionRange.shiftLeft(startOffset);
    String text = doc.getText(new TextRange(startOffset, endOffset));
    LineNumberConverter converter = contextStartLineNumber == 0 ? LineNumberConverter.DEFAULT : new LineNumberConverter.Increasing() {
      @Override
      public Integer convert(@NotNull Editor editor, int lineNumber) {
        return lineNumber + contextStartLineNumber;
      }
    };
    return new SnippetRenderingData(element.getProject(), element.getLanguage(), selectionRange, text, converter);
  }
}


