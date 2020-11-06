// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.EditorFragmentComponent;
import com.intellij.openapi.editor.IndentGuideDescriptor;
import com.intellij.openapi.editor.IndentsModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.LightweightHint;
import com.intellij.util.IntPair;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class IndentsModelImpl implements IndentsModel {
  private final Map<IntPair, IndentGuideDescriptor> myIndentsByLines = CollectionFactory.createSmallMemoryFootprintMap();
  private List<IndentGuideDescriptor> myIndents = new ArrayList<>();
  @NotNull private final EditorImpl myEditor;

  public IndentsModelImpl(@NotNull EditorImpl editor) {
    myEditor = editor;
    myEditor.getCaretModel().addCaretListener(new CaretListener() {
      @Nullable
      private LightweightHint myCurrentHint;
      @Nullable private IndentGuideDescriptor myCurrentCaretGuide;

      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        final IndentGuideDescriptor newGuide = myEditor.getIndentsModel().getCaretIndentGuide();
        if (!Comparing.equal(myCurrentCaretGuide, newGuide)) {
          repaintGuide(newGuide);
          repaintGuide(myCurrentCaretGuide);
          myCurrentCaretGuide = newGuide;

          if (myCurrentHint != null)
          {
            myCurrentHint.hide();
            myCurrentHint = null;
          }

          if (newGuide != null && shouldShowHint(newGuide)) {
            myCurrentHint = showHint(newGuide);
          }
        }
      }
    });
  }

  @Nullable
  public LightweightHint showHint(@NotNull IndentGuideDescriptor descriptor) {
    int startLine = Math.max(descriptor.codeConstructStartLine, descriptor.startLine - EditorFragmentComponent.getAvailableVisualLinesAboveEditor(myEditor) + 1);
    TextRange textRange = new TextRange(myEditor.getDocument().getLineStartOffset(startLine), myEditor.getDocument().getLineEndOffset(descriptor.startLine));
    return EditorFragmentComponent.showEditorFragmentHint(myEditor, textRange, false, false);
  }

  public boolean shouldShowHint(@NotNull IndentGuideDescriptor descriptor) {
    final Rectangle visibleArea = myEditor.getScrollingModel().getVisibleArea();
    return myEditor.logicalLineToY(descriptor.startLine) < visibleArea.y;
  }

  private void repaintGuide(@Nullable IndentGuideDescriptor guide) {
    if (guide != null) {
      myEditor.repaintLines(guide.startLine, guide.endLine);
    }
  }

  @NotNull
  public List<IndentGuideDescriptor> getIndents() {
    return myIndents;
  }

  @Override
  public IndentGuideDescriptor getCaretIndentGuide() {
    final LogicalPosition pos = myEditor.getCaretModel().getLogicalPosition();
    final int column = pos.column;
    final int line = pos.line;

    if (column > 0) {
      for (IndentGuideDescriptor indent : myIndents) {
        if (column == indent.indentLevel && line >= indent.startLine && line < indent.endLine) {
          return indent;
        }
      }
    }
    return null;
  }

  @Override
  public IndentGuideDescriptor getDescriptor(int startLine, int endLine) {
    return myIndentsByLines.get(new IntPair(startLine, endLine));
  }

  @Override
  public void assumeIndents(@NotNull List<IndentGuideDescriptor> descriptors) {
    myIndents = descriptors;
    myIndentsByLines.clear();
    for (IndentGuideDescriptor descriptor : myIndents) {
      myIndentsByLines.put(new IntPair(descriptor.startLine, descriptor.endLine), descriptor);
    }
  }
}
