// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ui.UIUtil;

import java.awt.Color;
import java.awt.Font;

/**
 * IJPL-244859: highlighting every match of a search over a big file adds all the highlighters in a single EDT event, and every one of
 * them asks the error stripe to repaint. Guards the throttling in {@code EditorImpl.queueErrorStipeRepaintRequest} - re-arming the
 * repaint alarm once per highlighter used to dominate such a burst.
 */
@PerformanceUnitTest
public class ErrorStripeRepaintPerformanceTest extends AbstractEditorTest {
  private static final String NEEDLE = "needle";
  private static final int MATCHES_PER_LINE = 4;
  private static final int MATCH_COUNT = 50_000;

  public void testAddingManyErrorStripeHighlightersAtOnce() {
    String line = StringUtil.repeat(NEEDLE + ' ', MATCHES_PER_LINE).trim() + '\n';
    String text = StringUtil.repeat(line, MATCH_COUNT / MATCHES_PER_LINE);
    initText(text);

    int[] offsets = new int[MATCH_COUNT];
    int found = 0;
    for (int i = text.indexOf(NEEDLE); i >= 0; i = text.indexOf(NEEDLE, i + NEEDLE.length())) {
      offsets[found++] = i;
    }
    assertEquals(MATCH_COUNT, found);

    // an error stripe color is what makes a highlighter reach queueErrorStipeRepaintRequest at all, the way a search result does
    TextAttributes attributes = new TextAttributes(null, Color.yellow, null, null, Font.PLAIN);
    attributes.setErrorStripeColor(Color.yellow);

    EditorImpl editor = (EditorImpl)getEditor();
    MarkupModel markupModel = editor.getMarkupModel();
    Benchmark.newBenchmark("Adding a lot of error stripe highlighters at once", () -> {
      for (int offset : offsets) {
        markupModel.addRangeHighlighter(offset, offset + NEEDLE.length(), 0, attributes, HighlighterTargetArea.EXACT_RANGE);
      }
      // the coalesced editor repaint is part of the burst, the stripe repaint is not: the alarm runs it once, after the burst
      UIUtil.dispatchAllInvocationEvents();
    }).setup(() -> {
      markupModel.removeAllHighlighters();
      editor.invokeDelayedErrorStripeRepaint(); // so that no stripe repaint left over from the previous attempt lands inside the next
      UIUtil.dispatchAllInvocationEvents();
    }).warmupIterations(2)
      .attempts(10)
      .runAsStressTest()
      .start();
  }
}
