package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightweightHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class IndentsModelCaretListener implements CaretListener {
  @NotNull
  private final EditorImpl myEditor;
  @Nullable
  private IndentGuideDescriptor myCurrentCaretGuide;
  @Nullable
  protected LightweightHint myCurrentHint;

  public IndentsModelCaretListener(@NotNull final EditorImpl editor) {
    myEditor = editor;
  }

  @Override
  public void caretPositionChanged(@NotNull final CaretEvent event) {
    final IndentGuideDescriptor newGuide = getCaretIndentGuide(event);
    if (!Comparing.equal(myCurrentCaretGuide, newGuide)) {
      repaintGuide(newGuide);
      repaintGuide(myCurrentCaretGuide);
      myCurrentCaretGuide = newGuide;

      if (myCurrentHint != null) {
        myCurrentHint.hide();
        myCurrentHint = null;
      }

      if (newGuide != null && shouldShowHint(newGuide)) {
        showHint(newGuide);
      }
    }
  }

  private void repaintGuide(@Nullable final IndentGuideDescriptor guide) {
    if (guide != null) {
      myEditor.repaintLines(guide.startLine, guide.endLine);
    }
  }

  private boolean shouldShowHint(@NotNull IndentGuideDescriptor descriptor) {
    final Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    return myEditor.logicalLineToY(descriptor.startLine) < visibleArea.y;
  }

  @Nullable
  protected IndentGuideDescriptor getCaretIndentGuide(@NotNull final CaretEvent event) {
    return myEditor.getIndentsModel().getCaretIndentGuide();
  }

  protected void showHint(@NotNull IndentGuideDescriptor descriptor) {
    int startLine = Math.max(descriptor.codeConstructStartLine, descriptor.startLine - EditorFragmentComponent.getAvailableVisualLinesAboveEditor(myEditor) + 1);
    TextRange textRange = new TextRange(myEditor.getDocument().getLineStartOffset(startLine), myEditor.getDocument().getLineEndOffset(descriptor.startLine));
    myCurrentHint =  EditorFragmentComponent.showEditorFragmentHint(myEditor, textRange, false, false);
  }
}