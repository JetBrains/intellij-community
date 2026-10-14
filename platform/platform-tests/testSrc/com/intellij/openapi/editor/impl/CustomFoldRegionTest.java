// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.CustomFoldRegionRenderer;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CustomFoldRegionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testAllowedOverlapping() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\nline2\n\n");
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
    });
  }

  @Test
  public void testCoordinateTransformations() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\nline2\nline3\n");
      myFixture.addCustomFoldRegion(1, 2, 30);

      assertEquals(new VisualPosition(0, 2), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(0, 2)));
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(1, 2)));
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(1, 10)));
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(2, 2)));
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(2, 10)));
      assertEquals(new VisualPosition(2, 2), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(3, 2)));

      assertEquals(new LogicalPosition(0, 2), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(0, 2)));
      assertEquals(new LogicalPosition(1, 0), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(1, 0)));
      assertEquals(new LogicalPosition(1, 0), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(1, 1)));
      assertEquals(new LogicalPosition(1, 0), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(1, 2)));
      assertEquals(new LogicalPosition(3, 2), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(2, 2)));

      assertEquals(0, myFixture.getEditor().offsetToVisualLine(5, false));
      assertEquals(1, myFixture.getEditor().offsetToVisualLine(6, false));
      assertEquals(1, myFixture.getEditor().offsetToVisualLine(11, false));
      assertEquals(1, myFixture.getEditor().offsetToVisualLine(12, false));
      assertEquals(1, myFixture.getEditor().offsetToVisualLine(17, false));
      assertEquals(2, myFixture.getEditor().offsetToVisualLine(18, false));

      assertEquals(0, ((EditorImpl)myFixture.getEditor()).visualLineStartOffset(0));
      assertEquals(6, ((EditorImpl)myFixture.getEditor()).visualLineStartOffset(1));
      assertEquals(18, ((EditorImpl)myFixture.getEditor()).visualLineStartOffset(2));

      assertEquals(0, myFixture.getEditor().visualLineToY(0));
      assertEquals(12, myFixture.getEditor().visualLineToY(1));
      assertEquals(42, myFixture.getEditor().visualLineToY(2));
      assertEquals(54, myFixture.getEditor().visualLineToY(3));

      assertEquals(0, myFixture.getEditor().yToVisualLine(0));
      assertEquals(0, myFixture.getEditor().yToVisualLine(11));
      assertEquals(1, myFixture.getEditor().yToVisualLine(12));
      assertEquals(1, myFixture.getEditor().yToVisualLine(13));
      assertEquals(2, myFixture.getEditor().yToVisualLine(42));
      assertEquals(2, myFixture.getEditor().yToVisualLine(53));
      assertEquals(3, myFixture.getEditor().yToVisualLine(54));

      assertEquals(new Point(20, 0), myFixture.getEditor().visualPositionToXY(new VisualPosition(0, 2)));
      assertEquals(new Point(0, 12), myFixture.getEditor().visualPositionToXY(new VisualPosition(1, 0)));
      assertEquals(new Point(0, 12), myFixture.getEditor().visualPositionToXY(new VisualPosition(1, 1)));
      assertEquals(new Point(0, 12), myFixture.getEditor().visualPositionToXY(new VisualPosition(1, 2)));
      assertEquals(new Point(0, 42), myFixture.getEditor().visualPositionToXY(new VisualPosition(2, 0)));
      assertEquals(new Point(20, 42), myFixture.getEditor().visualPositionToXY(new VisualPosition(2, 2)));

      assertEquals(new VisualPosition(0, 0), myFixture.getEditor().xyToVisualPosition(new Point(0, 0)));
      assertEquals(new VisualPosition(0, 2), myFixture.getEditor().xyToVisualPosition(new Point(20, 11)));
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().xyToVisualPosition(new Point(0, 12)));
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().xyToVisualPosition(new Point(10, 13)));
      assertEquals(new VisualPosition(2, 0), myFixture.getEditor().xyToVisualPosition(new Point(0, 42)));
      assertEquals(new VisualPosition(2, 2), myFixture.getEditor().xyToVisualPosition(new Point(20, 53)));

      assertEquals(new Point(50, 0), myFixture.getEditor().offsetToXY(5));
      assertEquals(new Point(0, 12), myFixture.getEditor().offsetToXY(6));
      assertEquals(new Point(0, 12), myFixture.getEditor().offsetToXY(11));
      assertEquals(new Point(0, 12), myFixture.getEditor().offsetToXY(17));
      assertEquals(new Point(0, 42), myFixture.getEditor().offsetToXY(18));
    });
  }

  @Test
  public void testEditorSize() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\nline2\nline3\n");
      myFixture.getEditor().getSettings().setAdditionalLinesCount(0);
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      assertEquals(new Dimension(50, 48), myFixture.getEditor().getContentComponent().getPreferredSize());
      myFixture.getEditor().getFoldingModel().runBatchFoldingOperation(() -> {
        assertNotNull(myFixture.getEditor().getFoldingModel().addCustomLinesFolding(1, 2, new CustomFoldRegionRenderer() {
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
      assertEquals(new Dimension(75, 54), myFixture.getEditor().getContentComponent().getPreferredSize());
    });
  }

  @Test
  public void testCaretMovement() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>line1\nline2\nline3\n ");
      myFixture.addCustomFoldRegion(1, 2, 2);

      assertEquals(new VisualPosition(0, 0), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.right();
      myFixture.right();
      myFixture.right();
      myFixture.right();
      myFixture.right();
      assertEquals(new VisualPosition(0, 5), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.right();
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.right();
      assertEquals(new VisualPosition(2, 0), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.right();
      assertEquals(new VisualPosition(2, 1), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.left();
      myFixture.left();
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.left();
      assertEquals(new VisualPosition(0, 5), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.left();
      assertEquals(new VisualPosition(0, 4), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.down();
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.down();
      assertEquals(new VisualPosition(2, 1), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.up();
      assertEquals(new VisualPosition(1, 0), myFixture.getEditor().getCaretModel().getVisualPosition());
      myFixture.up();
      assertEquals(new VisualPosition(0, 4), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  @Test
  public void testSizeUpdate() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.getEditor().getSettings().setAdditionalLinesCount(0);
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      FoldingModel foldingModel = myFixture.getEditor().getFoldingModel();
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
      assertEquals(25, myFixture.getEditor().visualLineToY(1));
      assertEquals(new Dimension(35, 25), myFixture.getEditor().getContentComponent().getPreferredSize());
      size[0] = 36;
      size[1] = 26;
      region[0].update();
      assertEquals(26, myFixture.getEditor().visualLineToY(1));
      assertEquals(new Dimension(36, 26), myFixture.getEditor().getContentComponent().getPreferredSize());
    });
  }

  @Test
  public void testInvalidationDueToRelationToAnotherFoldRegion() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\nline2\nline3");
      FoldingModelEx model = (FoldingModelEx)myFixture.getEditor().getFoldingModel();
      AtomicInteger invocationCount = new AtomicInteger();
      model.addListener(new FoldingListener() {
        @Override
        public void beforeFoldRegionDisposed(@NotNull FoldRegion region) {
          invocationCount.incrementAndGet();
        }
      }, myFixture.getTestRootDisposable());
      FoldRegion region = myFixture.addFoldRegion(0, 17, ".");
      assertNotNull(region);
      assertTrue(region.isValid());
      CustomFoldRegion customRegion = myFixture.addCustomFoldRegion(1, 1);
      assertNotNull(customRegion);
      assertTrue(customRegion.isValid());
      assertEquals(0, invocationCount.get());
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(11, 17));
      assertTrue(region.isValid());
      assertFalse(customRegion.isValid());
      assertEquals(1, invocationCount.get());
    });
  }

  @Test
  public void testInlineInlayAtLineEndDoesNotHaveImpact() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.getEditor().getSettings().setAdditionalLinesCount(0);
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      myFixture.addInlay(4, 15);
      myFixture.addCustomFoldRegion(0, 0, 40, 30);
      assertEquals(new Dimension(40, 30), myFixture.getEditor().getContentComponent().getPreferredSize());
    });
  }

  @Test
  public void testDelete() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\n<caret>line2\nline3");
      assertNotNull(myFixture.addCustomFoldRegion(1, 1));
      myFixture.delete();
      myFixture.checkResultByText("line1\n<caret>\nline3");
    });
  }

  @Test
  public void testBackspace() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\nline2\n<caret>\nline4");
      assertNotNull(myFixture.addCustomFoldRegion(1, 1));
      myFixture.backspace();
      myFixture.checkResultByText("line1\n<caret>\nline4");
    });
  }

  @Test
  public void testOverText() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      assertNotNull(myFixture.addCustomFoldRegion(0, 0, 321, 123));
      List<Boolean> results = new ArrayList<>();
      myFixture.getEditor().addEditorMouseMotionListener(new EditorMouseMotionListener() {
        @Override
        public void mouseMoved(@NotNull EditorMouseEvent e) {
          results.add(e.isOverText());
        }
      });
      myFixture.mouse().moveToXY(400, 100);
      assertEquals(List.of(Boolean.FALSE), results);
    });
  }

  @Test
  public void testOverlappingAfterDocumentChange() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\nline2");
      assertNotNull(myFixture.addCustomFoldRegion(0, 0));
      assertNotNull(myFixture.addCollapsedFoldRegion(6, 11, "..."));
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(5, 6));
      myFixture.verifyFoldingState("[FoldRegion +(5:10), placeholder='...']");
    });
  }

  @Test
  public void testOverlappingAfterDocumentChangeComplexLineBreaks() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      ((DocumentImpl)myFixture.getEditor().getDocument()).setAcceptSlashR(true);
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().insertString(0, "a\rb\nc"));
      assertNotNull(myFixture.addCustomFoldRegion(0, 0));
      myFixture.addCollapsedFoldRegion(3, 5, "...");
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(2, 3));
      myFixture.verifyFoldingState("[FoldRegion +(1:4), placeholder='...']");
    });
  }

  private void checkOverlapWithNormalRegion(int regionStartOffset,
                                            int regionEndOffset,
                                            int customRegionStartLine,
                                            int customRegionEndLine,
                                            boolean allowed) {
    try {
      assertNotNull(myFixture.addFoldRegion(regionStartOffset, regionEndOffset, "."));
      CustomFoldRegion customRegion = myFixture.addCustomFoldRegion(customRegionStartLine, customRegionEndLine);
      assertEquals(allowed, customRegion != null);
    }
    finally { // cleanup
      FoldingModelEx model = (FoldingModelEx)myFixture.getEditor().getFoldingModel();
      model.runBatchFoldingOperation(() -> model.clearFoldRegions());
    }
  }

  private void checkOverlapWithCustomRegion(int customRegionStartLine1,
                                            int customRegionEndLine1,
                                            int customRegionStartLine2,
                                            int customRegionEndLine2,
                                            boolean allowed) {
    try {
      assertNotNull(myFixture.addCustomFoldRegion(customRegionStartLine1, customRegionEndLine1));
      CustomFoldRegion customRegion2 = myFixture.addCustomFoldRegion(customRegionStartLine2, customRegionEndLine2);
      assertEquals(allowed, customRegion2 != null);
    }
    finally { // cleanup
      FoldingModelEx model = (FoldingModelEx)myFixture.getEditor().getFoldingModel();
      model.runBatchFoldingOperation(() -> model.clearFoldRegions());
    }
  }
}
