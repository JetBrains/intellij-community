// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Looks for the invalid fragment range of IJPL-176182. The test churns the editor models and paints after every step.
 */
public class EditorPaintingFuzzTest extends EditorPaintingTestCase {
  private static final int PAINT_WIDTH = 300;
  private static final int PAINT_HEIGHT = 400;
  private static final int STEPS = 400;
  private static final int SEEDS = 40;

  private static final String[] WORDS = {
    "abc", "defg", "hi", "\t", " ", "12345", "אבג", "شصض", "你好",
    "x", "\n", "\n\n", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "\ud83d\ude00", "()", "  ",
  };

  public void testFuzz() {
    initText("");
    List<String> log = new ArrayList<>();
    for (int seed = 0; seed < SEEDS; seed++) {
      log.clear();
      try {
        runSeed(seed, log);
      }
      catch (Throwable e) {
        throw new AssertionError("seed " + seed + " failed after:\n" + String.join("\n", log), e);
      }
    }
  }

  private void runSeed(int seed, List<String> log) {
    Random random = new Random(seed);
    EditorEx editor = (EditorEx)getEditor();
    Document document = editor.getDocument();
    runWriteCommand(() -> document.setText(""));
    resetModels();
    log.add("reset");

    for (int step = 0; step < STEPS; step++) {
      int length = document.getTextLength();
      switch (random.nextInt(10)) {
        case 0, 1, 2 -> {
          String word = WORDS[random.nextInt(WORDS.length)];
          int offset = length == 0 ? 0 : random.nextInt(length + 1);
          log.add("insert " + offset + " " + escape(word));
          runWriteCommand(() -> document.insertString(safeOffset(document, offset), word));
        }
        case 3 -> {
          if (length < 2) break;
          int from = random.nextInt(length);
          int to = from + 1 + random.nextInt(Math.min(30, length - from));
          log.add("delete " + from + " " + to);
          runWriteCommand(() -> document.deleteString(safeOffset(document, from), safeOffset(document, to)));
        }
        case 4 -> {
          if (length < 2) break;
          int from = random.nextInt(length);
          int to = from + 1 + random.nextInt(Math.min(30, length - from));
          String word = WORDS[random.nextInt(WORDS.length)];
          log.add("replace " + from + " " + to + " " + escape(word));
          runWriteCommand(() -> document.replaceString(safeOffset(document, from), safeOffset(document, to), word));
        }
        case 5 -> {
          if (length < 3) break;
          int from = random.nextInt(length - 1);
          int to = from + 1 + random.nextInt(Math.min(40, length - from - 1));
          log.add("fold " + from + " " + to);
          runFoldingOperation(() -> {
            FoldRegion region = editor.getFoldingModel().addFoldRegion(from, to, "...");
            if (region != null) region.setExpanded(false);
          });
        }
        case 6 -> {
          FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
          if (regions.length == 0) break;
          FoldRegion region = regions[random.nextInt(regions.length)];
          log.add("unfold " + region.getStartOffset() + " " + region.getEndOffset());
          runFoldingOperation(() -> editor.getFoldingModel().removeFoldRegion(region));
        }
        case 7 -> {
          if (length == 0) break;
          int offset = random.nextInt(length + 1);
          log.add("inlay " + offset);
          editor.getInlayModel().addInlineElement(offset, new MyInlayRenderer());
        }
        case 8 -> {
          if (length < 2) break;
          int from = random.nextInt(length);
          int to = from + 1 + random.nextInt(Math.min(50, length - from));
          boolean lines = random.nextBoolean();
          log.add("highlighter " + from + " " + to + " lines=" + lines);
          TextAttributes attributes = new TextAttributes(Color.red, null, null, null, Font.PLAIN);
          RangeHighlighter highlighter = editor.getMarkupModel().addRangeHighlighter(
            from, to, HighlighterLayer.WARNING, attributes,
            lines ? HighlighterTargetArea.LINES_IN_RANGE : HighlighterTargetArea.EXACT_RANGE);
          assertNotNull(highlighter);
        }
        case 9 -> {
          if (length == 0) break;
          int caret = random.nextInt(length + 1);
          int from = random.nextInt(length + 1);
          int to = random.nextInt(length + 1);
          log.add("caret " + caret + " selection " + Math.min(from, to) + " " + Math.max(from, to));
          editor.getCaretModel().moveToOffset(caret);
          editor.getSelectionModel().setSelection(Math.min(from, to), Math.max(from, to));
        }
      }
      if (random.nextInt(20) == 0) {
        int wrapAt = 5 + random.nextInt(30);
        log.add("softWraps " + wrapAt);
        configureSoftWraps(wrapAt);
      }
      if (random.nextInt(30) == 0) {
        boolean right = random.nextBoolean();
        log.add("rightAligned " + right);
        ((EditorImpl)editor).setHorizontalTextAlignment(
          right ? EditorImpl.TEXT_ALIGNMENT_RIGHT : EditorImpl.TEXT_ALIGNMENT_LEFT);
      }
      paint();
    }
  }

  private void resetModels() {
    EditorEx editor = (EditorEx)getEditor();
    runFoldingOperation(() -> editor.getFoldingModel().clearFoldRegions());
    editor.getMarkupModel().removeAllHighlighters();
    editor.getCaretModel().moveToOffset(0);
    editor.getSelectionModel().removeSelection();
    ((EditorImpl)editor).setHorizontalTextAlignment(EditorImpl.TEXT_ALIGNMENT_LEFT);
  }

  private static int safeOffset(Document document, int offset) {
    return Math.clamp(offset, 0, document.getTextLength());
  }

  private static String escape(String text) {
    return "'" + text.replace("\n", "\\n").replace("\t", "\\t") + "'";
  }

  private void paint() {
    JComponent component = getEditor().getContentComponent();
    component.setSize(PAINT_WIDTH, PAINT_HEIGHT);
    BufferedImage image = new BufferedImage(PAINT_WIDTH, PAINT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = new CheckingGraphics(image.createGraphics());
    try {
      component.paint(graphics);
    }
    finally {
      graphics.dispose();
    }
  }
}
