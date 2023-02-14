// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomFoldRegionTest extends AbstractEditorTest {
  public void testAllowedOverlapping() {
    initText("line1\nline2\n\n");
    checkOverlapWithNormalRegion(0, 5, 1, 1, true);
    checkOverlapWithNormalRegion(12, 13, 1, 1, true);
    checkOverlapWithNormalRegion(6, 11, 1, 1, true);
    checkOverlapWithNormalRegion(6, 7, 1, 1, true);
    checkOverlapWithNormalRegion(10, 11, 1, 1, true);
    checkOverlapWithNormalRegion(7, 10, 1, 1, true);
    checkOverlapWithNormalRegion(5, 12, 1, 1, true);

    checkOverlapWithNormalRegion(5, 6, 1, 1, false);
    checkOverlapWithNormalRegion(5, 7, 1, 1, false);
    checkOverlapWithNormalRegion(5, 11, 1, 1, false);
    checkOverlapWithNormalRegion(6, 12, 1, 1, false);
    checkOverlapWithNormalRegion(10, 12, 1, 1, false);
    checkOverlapWithNormalRegion(11, 12, 1, 1, false);

    checkOverlapWithCustomRegion(1, 1, 1, 1, false);
    checkOverlapWithCustomRegion(1, 1, 0, 0, true);
    checkOverlapWithCustomRegion(1, 1, 0, 1, true);
    checkOverlapWithCustomRegion(1, 1, 1, 2, true);
    checkOverlapWithCustomRegion(1, 1, 0, 2, true);

    checkOverlapWithCustomRegion(1, 2, 0, 1, false);
    checkOverlapWithCustomRegion(1, 2, 2, 3, false);
    checkOverlapWithCustomRegion(1, 2, 1, 2, false);
    checkOverlapWithCustomRegion(1, 2, 1, 1, true);
    checkOverlapWithCustomRegion(1, 2, 0, 2, true);
    checkOverlapWithCustomRegion(1, 2, 1, 3, true);
    checkOverlapWithCustomRegion(1, 2, 0, 3, true);
  }

  public void testCoordinateTransformations() {
    initText("line1\nline2\nline3\n");
    addCustomFoldRegion(1, 2, 30);

    assertEquals(new VisualPosition(0, 2), getEditor().logicalToVisualPosition(new LogicalPosition(0, 2)));
    assertEquals(new VisualPosition(1, 0), getEditor().logicalToVisualPosition(new LogicalPosition(1, 2)));
    assertEquals(new VisualPosition(1, 0), getEditor().logicalToVisualPosition(new LogicalPosition(1, 10)));
    assertEquals(new VisualPosition(1, 0), getEditor().logicalToVisualPosition(new LogicalPosition(2, 2)));
    assertEquals(new VisualPosition(1, 0), getEditor().logicalToVisualPosition(new LogicalPosition(2, 10)));
    assertEquals(new VisualPosition(2, 2), getEditor().logicalToVisualPosition(new LogicalPosition(3, 2)));

    assertEquals(new LogicalPosition(0, 2), getEditor().visualToLogicalPosition(new VisualPosition(0, 2)));
    assertEquals(new LogicalPosition(1, 0), getEditor().visualToLogicalPosition(new VisualPosition(1, 0)));
    assertEquals(new LogicalPosition(1, 0), getEditor().visualToLogicalPosition(new VisualPosition(1, 1)));
    assertEquals(new LogicalPosition(1, 0), getEditor().visualToLogicalPosition(new VisualPosition(1, 2)));
    assertEquals(new LogicalPosition(3, 2), getEditor().visualToLogicalPosition(new VisualPosition(2, 2)));

    assertEquals(0, getEditor().offsetToVisualLine(5, false));
    assertEquals(1, getEditor().offsetToVisualLine(6, false));
    assertEquals(1, getEditor().offsetToVisualLine(11, false));
    assertEquals(1, getEditor().offsetToVisualLine(12, false));
    assertEquals(1, getEditor().offsetToVisualLine(17, false));
    assertEquals(2, getEditor().offsetToVisualLine(18, false));

    assertEquals(0, ((EditorImpl)getEditor()).visualLineStartOffset(0));
    assertEquals(6, ((EditorImpl)getEditor()).visualLineStartOffset(1));
    assertEquals(18, ((EditorImpl)getEditor()).visualLineStartOffset(2));

    assertEquals(0, getEditor().visualLineToY(0));
    assertEquals(12, getEditor().visualLineToY(1));
    assertEquals(42, getEditor().visualLineToY(2));
    assertEquals(54, getEditor().visualLineToY(3));

    assertEquals(0, getEditor().yToVisualLine(0));
    assertEquals(0, getEditor().yToVisualLine(11));
    assertEquals(1, getEditor().yToVisualLine(12));
    assertEquals(1, getEditor().yToVisualLine(13));
    assertEquals(2, getEditor().yToVisualLine(42));
    assertEquals(2, getEditor().yToVisualLine(53));
    assertEquals(3, getEditor().yToVisualLine(54));

    assertEquals(new Point(20, 0), getEditor().visualPositionToXY(new VisualPosition(0, 2)));
    assertEquals(new Point(0, 12), getEditor().visualPositionToXY(new VisualPosition(1, 0)));
    assertEquals(new Point(0, 12), getEditor().visualPositionToXY(new VisualPosition(1, 1)));
    assertEquals(new Point(0, 12), getEditor().visualPositionToXY(new VisualPosition(1, 2)));
    assertEquals(new Point(0, 42), getEditor().visualPositionToXY(new VisualPosition(2, 0)));
    assertEquals(new Point(20, 42), getEditor().visualPositionToXY(new VisualPosition(2, 2)));

    assertEquals(new VisualPosition(0, 0), getEditor().xyToVisualPosition(new Point(0, 0)));
    assertEquals(new VisualPosition(0, 2), getEditor().xyToVisualPosition(new Point(20, 11)));
    assertEquals(new VisualPosition(1, 0), getEditor().xyToVisualPosition(new Point(0, 12)));
    assertEquals(new VisualPosition(1, 0), getEditor().xyToVisualPosition(new Point(10, 13)));
    assertEquals(new VisualPosition(2, 0), getEditor().xyToVisualPosition(new Point(0, 42)));
    assertEquals(new VisualPosition(2, 2), getEditor().xyToVisualPosition(new Point(20, 53)));

    assertEquals(new Point(50, 0), getEditor().offsetToXY(5));
    assertEquals(new Point(0, 12), getEditor().offsetToXY(6));
    assertEquals(new Point(0, 12), getEditor().offsetToXY(11));
    assertEquals(new Point(0, 12), getEditor().offsetToXY(17));
    assertEquals(new Point(0, 42), getEditor().offsetToXY(18));
  }

  public void testEditorSize() {
    initText("line1\nline2\nline3\n");
    getEditor().getSettings().setAdditionalLinesCount(0);
    getEditor().getSettings().setAdditionalColumnsCount(0);
    assertEquals(new Dimension(50, 48), getEditor().getContentComponent().getPreferredSize());
    getEditor().getFoldingModel().runBatchFoldingOperation(() -> {
      assertNotNull(getEditor().getFoldingModel().addCustomLinesFolding(1, 2, new CustomFoldRegionRenderer() {
        @Override
        public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
          return 75;
        }

        @Override
        public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
          return 30;
        }

        @Override
        public void paint(@NotNull CustomFoldRegion region,
                          @NotNull Graphics2D g,
                          @NotNull Rectangle2D targetRegion,
                          @NotNull TextAttributes textAttributes) {}
      }));
    });
    assertEquals(new Dimension(75, 54), getEditor().getContentComponent().getPreferredSize());
  }

  public void testCaretMovement() {
    initText("<caret>line1\nline2\nline3\n ");
    addCustomFoldRegion(1, 2, 2);

    assertEquals(new VisualPosition(0, 0), getEditor().getCaretModel().getVisualPosition());
    right();
    right();
    right();
    right();
    right();
    assertEquals(new VisualPosition(0, 5), getEditor().getCaretModel().getVisualPosition());
    right();
    assertEquals(new VisualPosition(1, 0), getEditor().getCaretModel().getVisualPosition());
    right();
    assertEquals(new VisualPosition(2, 0), getEditor().getCaretModel().getVisualPosition());
    right();
    assertEquals(new VisualPosition(2, 1), getEditor().getCaretModel().getVisualPosition());
    left();
    left();
    assertEquals(new VisualPosition(1, 0), getEditor().getCaretModel().getVisualPosition());
    left();
    assertEquals(new VisualPosition(0, 5), getEditor().getCaretModel().getVisualPosition());
    left();
    assertEquals(new VisualPosition(0, 4), getEditor().getCaretModel().getVisualPosition());
    down();
    assertEquals(new VisualPosition(1, 0), getEditor().getCaretModel().getVisualPosition());
    down();
    assertEquals(new VisualPosition(2, 1), getEditor().getCaretModel().getVisualPosition());
    up();
    assertEquals(new VisualPosition(1, 0), getEditor().getCaretModel().getVisualPosition());
    up();
    assertEquals(new VisualPosition(0, 4), getEditor().getCaretModel().getVisualPosition());
  }

  public void testSizeUpdate() {
    initText("text");
    getEditor().getSettings().setAdditionalLinesCount(0);
    getEditor().getSettings().setAdditionalColumnsCount(0);
    FoldingModel foldingModel = getEditor().getFoldingModel();
    int[] size = new int[] {35, 25};
    CustomFoldRegion[] region = new CustomFoldRegion[1];
    foldingModel.runBatchFoldingOperation(() -> {
      region[0] = foldingModel.addCustomLinesFolding(0, 0, new CustomFoldRegionRenderer() {
        @Override
        public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
          return size[0];
        }

        @Override
        public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
          return size[1];
        }

        @Override
        public void paint(@NotNull CustomFoldRegion region,
                          @NotNull Graphics2D g,
                          @NotNull Rectangle2D targetRegion,
                          @NotNull TextAttributes textAttributes) {}
      });
      assertNotNull(region[0]);
    });
    assertEquals(25, getEditor().visualLineToY(1));
    assertEquals(new Dimension(35, 25), getEditor().getContentComponent().getPreferredSize());
    size[0] = 36;
    size[1] = 26;
    region[0].update();
    assertEquals(26, getEditor().visualLineToY(1));
    assertEquals(new Dimension(36, 26), getEditor().getContentComponent().getPreferredSize());
  }

  public void testInvalidationDueToRelationToAnotherFoldRegion() {
    initText("line1\nline2\nline3");
    FoldingModelEx model = (FoldingModelEx)getEditor().getFoldingModel();
    AtomicInteger invocationCount = new AtomicInteger();
    model.addListener(new FoldingListener() {
      @Override
      public void beforeFoldRegionDisposed(@NotNull FoldRegion region) {
        invocationCount.incrementAndGet();
      }
    }, getTestRootDisposable());
    FoldRegion region = addFoldRegion(0, 17, ".");
    assertNotNull(region);
    assertTrue(region.isValid());
    CustomFoldRegion customRegion = addCustomFoldRegion(1, 1);
    assertNotNull(customRegion);
    assertTrue(customRegion.isValid());
    assertEquals(0, invocationCount.get());
    runWriteCommand(() -> getEditor().getDocument().deleteString(11, 17));
    assertTrue(region.isValid());
    assertFalse(customRegion.isValid());
    assertEquals(1, invocationCount.get());
  }

  public void testInlineInlayAtLineEndDoesNotHaveImpact() {
    initText("text");
    getEditor().getSettings().setAdditionalLinesCount(0);
    getEditor().getSettings().setAdditionalColumnsCount(0);
    addInlay(4, 15);
    addCustomFoldRegion(0, 0, 40, 30);
    assertEquals(new Dimension(40, 30), getEditor().getContentComponent().getPreferredSize());
  }

  public void testDelete() {
    initText("line1\n<caret>line2\nline3");
    assertNotNull(addCustomFoldRegion(1, 1));
    delete();
    checkResultByText("line1\n<caret>\nline3");
  }

  public void testBackspace() {
    initText("line1\nline2\n<caret>\nline4");
    assertNotNull(addCustomFoldRegion(1, 1));
    backspace();
    checkResultByText("line1\n<caret>\nline4");
  }

  public void testOverText() {
    initText("text");
    assertNotNull(addCustomFoldRegion(0, 0, 321, 123));
    List<Boolean> results = new ArrayList<>();
    getEditor().addEditorMouseMotionListener(new EditorMouseMotionListener() {
      @Override
      public void mouseMoved(@NotNull EditorMouseEvent e) {
        results.add(e.isOverText());
      }
    });
    mouse().moveToXY(400, 100);
    assertEquals(List.of(Boolean.FALSE), results);
  }

  public void testOverlappingAfterDocumentChange() {
    initText("line1\nline2");
    assertNotNull(addCustomFoldRegion(0, 0));
    assertNotNull(addCollapsedFoldRegion(6, 11, "..."));
    runWriteCommand(() -> getEditor().getDocument().deleteString(5, 6));
    verifyFoldingState("[FoldRegion +(5:10), placeholder='...']");
  }

  public void testOverlappingAfterDocumentChangeComplexLineBreaks() {
    initText("");
    ((DocumentImpl)getEditor().getDocument()).setAcceptSlashR(true);
    runWriteCommand(() -> getEditor().getDocument().insertString(0, "a\rb\nc"));
    assertNotNull(addCustomFoldRegion(0, 0));
    addCollapsedFoldRegion(3, 5, "...");
    runWriteCommand(() -> getEditor().getDocument().deleteString(2, 3));
    verifyFoldingState("[FoldRegion +(1:4), placeholder='...']");
  }

  private void checkOverlapWithNormalRegion(int regionStartOffset,
                                            int regionEndOffset,
                                            int customRegionStartLine,
                                            int customRegionEndLine,
                                            boolean allowed) {
    try {
      assertNotNull(addFoldRegion(regionStartOffset, regionEndOffset, "."));
      CustomFoldRegion customRegion = addCustomFoldRegion(customRegionStartLine, customRegionEndLine);
      assertEquals(allowed, customRegion != null);
    }
    finally { // cleanup
      FoldingModelEx model = (FoldingModelEx)getEditor().getFoldingModel();
      model.runBatchFoldingOperation(() -> model.clearFoldRegions());
    }
  }

  private void checkOverlapWithCustomRegion(int customRegionStartLine1,
                                            int customRegionEndLine1,
                                            int customRegionStartLine2,
                                            int customRegionEndLine2,
                                            boolean allowed) {
    try {
      assertNotNull(addCustomFoldRegion(customRegionStartLine1, customRegionEndLine1));
      CustomFoldRegion customRegion2 = addCustomFoldRegion(customRegionStartLine2, customRegionEndLine2);
      assertEquals(allowed, customRegion2 != null);
    }
    finally { // cleanup
      FoldingModelEx model = (FoldingModelEx)getEditor().getFoldingModel();
      model.runBatchFoldingOperation(() -> model.clearFoldRegions());
    }
  }
}
