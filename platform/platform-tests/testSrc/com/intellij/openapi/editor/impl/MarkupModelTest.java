// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.event.MarkupModelListener;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MarkupModelTest extends AbstractEditorTest {
  public void testMarkupModelListenersDoWork() {
    initText(" ".repeat(100));
    MarkupModelEx markupModel = (MarkupModelEx)DocumentMarkupModel.forDocument(getDocument(getFile()), getProject(), true);
    List<String> events = new ArrayList<>();
    markupModel.addMarkupModelListener(getTestRootDisposable(), new MarkupModelListener() {
      @Override
      public void afterAdded(@NotNull RangeHighlighterEx highlighter) {
        events.add(highlighter.getTextRange() + ": AA");
      }

      @Override
      public void beforeRemoved(@NotNull RangeHighlighterEx highlighter) {
        events.add(highlighter.getTextRange() + ": BR");
      }

      @Override
      public void afterRemoved(@NotNull RangeHighlighterEx highlighter) {
        events.add(highlighter.getTextRange() + ": AR");
      }

      @Override
      public void attributesChanged(@NotNull RangeHighlighterEx highlighter, boolean renderersChanged, boolean fontStyleOrColorChanged) {
        events.add(highlighter.getTextRange() + ": AC");
      }
    });
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(1, 2, 0, null, HighlighterTargetArea.EXACT_RANGE);
    List<String> expected = new ArrayList<>();
    expected.add("(1,2): AA"); assertEquals(expected, events);
    highlighter.setCustomRenderer((editor, highlighter1, g) -> {}); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setEditorFilter(__ -> true); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setErrorStripeMarkColor(new Color(1)); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setErrorStripeTooltip(this); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setGutterIconRenderer(MarkupModelStressTest.DUMMY_GUTTER_ICON_RENDERER); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setLineMarkerRenderer((editor, g, r) -> {}); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setLineSeparatorColor(new Color(1)); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setLineSeparatorPlacement(SeparatorPlacement.BOTTOM); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setLineSeparatorRenderer((g, x1, x2, y) -> {}); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setTextAttributesKey(CodeInsightColors.LINE_NONE_COVERAGE); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.setThinErrorStripeMark(true); expected.add("(1,2): AC"); assertEquals(expected, events);
    highlighter.dispose();  expected.add("(1,2): BR");  expected.add("(1,2): AR"); assertEquals(expected, events);
    assertEquals(expected, events);
  }
}
