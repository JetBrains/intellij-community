// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.FontPreferences;
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.mapping.SoftWrapApplianceManager;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LoggedError;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.DocumentUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.font.TextHitInfo;
import java.text.AttributedString;
import java.util.Collections;
import java.util.stream.IntStream;

import static com.intellij.openapi.editor.impl.EditorModelFixture.HIGH_SURROGATE;
import static com.intellij.openapi.editor.impl.EditorModelFixture.LOW_SURROGATE;
import static com.intellij.openapi.editor.impl.EditorModelFixture.SURROGATE_PAIR;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_CHAR_WIDTH;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_DESCENT;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_LINE_HEIGHT;
import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditorImplTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testPositionCalculationForZeroWidthChars() {
    myFixture.runTest(() -> {
      myFixture.initText("some\u2044text");
      VisualPosition pos = new VisualPosition(0, 6);
      VisualPosition recalculatedPos = myFixture.getEditor().xyToVisualPosition(myFixture.getEditor().visualPositionToXY(pos));
      assertEquals(pos, recalculatedPos);
    });
  }

  @Test
  public void testPositionCalculationOnEmptyLine() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 text with

                 empty line""");
      VisualPosition pos = new VisualPosition(1, 0);
      VisualPosition recalculatedPos = myFixture.getEditor().xyToVisualPosition(myFixture.getEditor().visualPositionToXY(pos));
      assertEquals(pos, recalculatedPos);
    });
  }

  @Test
  public void testShiftDragAddsToSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("A quick brown fox");
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 1000, 1000); // enable drag testing
      myFixture.mouse().clickAt(0, 1);
      myFixture.mouse().shift().pressAt(0, 2).dragTo(0, 3).release();
      myFixture.checkResultByText("A<selection> q<caret></selection>uick brown fox");
    });
  }

  @Test
  public void testSoftWrapsRecalculationInASpecificCase() {
    myFixture.runTest(() -> {
      if (ExperimentalUI.isNewUI()) {
        // TODO: rewrite test if needed
        return;
      }
      myFixture.configureFromFileText(myFixture.getTestName() + ".java",
                            """
                              <selection>class Foo {
                              \t@Override
                              \tpublic boolean equals(Object other) {
                              \t\treturn this == other;
                              \t}
                              }</selection>""");
      EditorTestUtil.buildInitialFoldingsInBackground(myFixture.getEditor());
      myFixture.configureSoftWraps(32, false);

      // verify initial state
      assertEquals(4, EditorUtil.getTabSize(myFixture.getEditor()));
      assertEquals("[FoldRegion +(59:64), placeholder=' { ', FoldRegion +(85:88), placeholder=' }']", myFixture.getEditor().getFoldingModel().toString());
      myFixture.verifySoftWrapPositions(52, 85);

      myFixture.runWriteCommand(() -> {
        Document document = myFixture.getEditor().getDocument();
        for (int i = document.getLineCount() - 1; i >= 0; i--) {
          document.insertString(document.getLineStartOffset(i), "//");
        }
      });


      myFixture.verifySoftWrapPositions(58, 93);
    });
  }

  @Test
  public void testCorrectVisibleLineCountCalculation() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 line containing FOLDED_REGION
                 next <caret>line
                 last line""");
      myFixture.foldOccurrences("FOLDED_REGION", "...");
      myFixture.configureSoftWraps(16); // wrap right at folded region start
      myFixture.verifySoftWrapPositions(16);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
      myFixture.checkResultByText("""
                          line containing FOLDED_REGION
                          next line
                          last <caret>line""");
    });
  }

  @Test
  public void testInsertingFirstTab() {
    myFixture.runTest(() -> {
      myFixture.initText(" <caret>space-indented line");
      myFixture.configureSoftWraps(100);
      myFixture.getEditor().getSettings().setUseTabCharacter(true);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_TAB);
      myFixture.checkResultByText(" \t<caret>space-indented line");
    });
  }

  @Test
  public void testNoExceptionDuringBulkModeDocumentUpdate() {
    myFixture.runTest(() -> {
      myFixture.initText("something");
      DocumentEx document = (DocumentEx)myFixture.getEditor().getDocument();
      myFixture.runWriteCommand(() -> DocumentUtil.executeInBulk(document, ()-> document.setText("something\telse")));

      myFixture.checkResultByText("something\telse");
    });
  }

  @Test
  public void testCaretMergeDuringBulkModeDocumentUpdate() {
    myFixture.runTest(() -> {
      myFixture.initText("a<selection>bcdef<caret></selection>g\n" +
               "a<selection>bcdef<caret></selection>g");
      myFixture.runWriteCommand(() -> {
        DocumentEx document = (DocumentEx)myFixture.getEditor().getDocument();
        DocumentUtil.executeInBulk(document, ()-> {
          // delete selected text
          document.deleteString(1, 6);
          document.deleteString(4, 9);
        });
      });

      myFixture.checkResultByText("a<caret>g\n" +
                        "a<caret>g");
    });
  }

  @Test
  public void testPositionCalculationForOneCharacterFolds() {
    myFixture.runTest(() -> {
      myFixture.initText("something");
      myFixture.addCollapsedFoldRegion(1, 2, "...");
      myFixture.addCollapsedFoldRegion(3, 4, "...");

      assertEquals(new VisualPosition(0, 5), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(0, 3)));
    });
  }
  
  @Test
  public void testNavigationIntoFoldedRegionWithSoftWrapsEnabled() {
    myFixture.runTest(() -> {
      myFixture.initText("something");
      myFixture.addCollapsedFoldRegion(4, 8, "...");
      myFixture.configureSoftWraps(1000);
    
      myFixture.getEditor().getCaretModel().moveToOffset(5);
    
      assertEquals(new VisualPosition(0, 5), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }
  
  @Test
  public void testPositionCalculationForMultilineFoldingWithEmptyPlaceholder() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 line1
                 line2
                 line3
                 line4""");
      myFixture.addCollapsedFoldRegion(6, 17, "");
      myFixture.configureSoftWraps(1000);
    
      assertEquals(new LogicalPosition(3, 0), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(2, 0)));
    });
  }
  
  @Test
  public void testNavigationInsideNonNormalizedLineTerminator() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      ((DocumentImpl)myFixture.getEditor().getDocument()).setAcceptSlashR(true);
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().insertString(0, "abc\r\ndef"));

      myFixture.getEditor().getCaretModel().moveToOffset(4);
    
      assertEquals(new LogicalPosition(0, 3), myFixture.getEditor().getCaretModel().getLogicalPosition());
      assertEquals(new VisualPosition(0, 3), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }
  
  @Test
  public void testSoftWrapModeUpdateDuringBulkModeChange() {
    myFixture.runTest(() -> {
      myFixture.initText("long long line<caret>");
      myFixture.configureSoftWraps(12);
      DocumentEx document = (DocumentEx)myFixture.getEditor().getDocument();
      myFixture.runWriteCommand(() -> DocumentUtil.executeInBulk(document, ()-> document.replaceString(4, 5, "-")));

      assertEquals(new VisualPosition(1, 5), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  @Test
  public void testForcedSoftWrapsAreDeferredUntilBulkModeFinishes() {
    myFixture.runTest(() -> {
      int oldForceLimit = AdvancedSettings.getInt("editor.soft.wrap.force.limit");
      AdvancedSettings.setInt("editor.soft.wrap.force.limit", 10);
      try {
        myFixture.initText("short");
        EditorImpl editor = (EditorImpl)myFixture.getEditor();
        DocumentEx document = editor.getDocument();
        myFixture.setEditorVisibleSize(10, 1);

        assertFalse(editor.getSettings().isUseSoftWraps());
        assertNull(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS));
        assertTrue(editor.getSoftWrapModel().getSoftWrapsForRange(0, document.getTextLength()).isEmpty());

        String longLine = StringUtil.repeat("1234567890", 3);
        myFixture.runWriteCommand(() -> DocumentUtil.executeInBulk(document, () -> {
          document.setText(longLine);

          assertFalse(editor.getSettings().isUseSoftWraps());
          assertNull(editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS));
          assertTrue(editor.getSoftWrapModel().getSoftWrapsForRange(0, document.getTextLength()).isEmpty());
        }));

        assertTrue(editor.getSettings().isUseSoftWraps());
        assertEquals(Boolean.TRUE, editor.getUserData(EditorImpl.FORCED_SOFT_WRAPS));
        assertFalse(editor.getSoftWrapModel().getSoftWrapsForRange(0, document.getTextLength()).isEmpty());
      }
      finally {
        AdvancedSettings.setInt("editor.soft.wrap.force.limit", oldForceLimit);
      }
    });
  }
  
  @Test
  public void testSuccessiveBulkModeOperations() {
    myFixture.runTest(() -> {
      myFixture.initText("some text");
      DocumentEx document = (DocumentEx)myFixture.getEditor().getDocument();

      myFixture.runWriteCommand(() -> {
        DocumentUtil.executeInBulk(document, ()-> document.replaceString(4, 5, "-"));

        myFixture.getEditor().getCaretModel().moveToOffset(9);

        DocumentUtil.executeInBulk(document, ()-> document.replaceString(4, 5, "+"));
      });


      myFixture.checkResultByText("some+text<caret>");
    });
  }
  
  @Test
  public void testCorrectCaretPositionRestorationAfterMultipleFoldRegionsChange() {
    myFixture.runTest(() -> {
      myFixture.initText("so<caret>me long text");
      final FoldRegion innerRegion = myFixture.addCollapsedFoldRegion(0, 4, "...");
      final FoldRegion outerRegion = myFixture.addCollapsedFoldRegion(0, 9, "...");

      myFixture.runFoldingOperation(() -> {
        myFixture.getEditor().getFoldingModel().removeFoldRegion(outerRegion);
        myFixture.getEditor().getFoldingModel().removeFoldRegion(innerRegion);
      });

      myFixture.checkResultByText("so<caret>me long text");
    });
  }
  
  @Test
  public void testEditorSizeCalculationOnOpening() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nbbb\nccccc");
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      myFixture.getEditor().getSettings().setAdditionalLinesCount(0);
      assertEquals(new Dimension(50, (int)(30 * FontPreferences.DEFAULT_LINE_SPACING)), myFixture.getEditor().getContentComponent().getPreferredSize());
    });
  }
  
  @Test
  public void testCollapsingRegionContainingSoftWrap() {
    myFixture.runTest(() -> {
      myFixture.initText("abcdef abcdef");
      myFixture.configureSoftWraps(10);
      myFixture.addCollapsedFoldRegion(0, 13, "...");
      assertEquals(new VisualPosition(0, 3), myFixture.getEditor().offsetToVisualPosition(13));
    });
  }
  
  @Test
  public void testSizeRecalculationOnTurningSoftWrapsOff() {
    myFixture.runTest(() -> {
      myFixture.initText("abc def");
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      myFixture.getEditor().getSettings().setAdditionalLinesCount(0);
      myFixture.configureSoftWraps(4);
      myFixture.getEditor().getSettings().setUseSoftWraps(false);
      assertEquals(new Dimension(70, (int)(10 * FontPreferences.DEFAULT_LINE_SPACING)), myFixture.getEditor().getContentComponent().getPreferredSize());
    });
  }
  
  @Test
  public void testUpdatingCaretPositionAfterBulkMode() {
    myFixture.runTest(() -> {
      myFixture.initText("a<caret>bc");
      myFixture.runWriteCommand(() -> {
        DocumentEx document = (DocumentEx)myFixture.getEditor().getDocument();
        DocumentUtil.executeInBulk(document, ()-> {
          document.insertString(0, "\n "); // we're changing number of visual lines, and invalidating text layout for caret line
        });
      });

      myFixture.checkResultByText("\n a<caret>bc");
    });
  }
  
  @Test
  public void testAllowCaretPositioningInsideSoftWrapOnlyIfVirtualSpaceIsEnabled() {
    myFixture.runTest(() -> {
      myFixture.initText("abcdef abcdef");
      myFixture.configureSoftWraps(10);
      myFixture.mouse().clickAt(0, 10);
      assertEquals(new VisualPosition(0, 7), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }
  
  @Test
  public void testXYToVisualPositionWhenPrefixIsSet() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      myFixture.configureSoftWraps(1000); // to make sure soft wrap character width is properly mocked
      ((EditorEx)myFixture.getEditor()).setPrefixTextAndAttributes(">", new TextAttributes());
      assertEquals(new VisualPosition(0, 0), myFixture.getEditor().xyToVisualPosition(new Point(1, 0)));
    });
  }
  
  @Test
  public void testLogicalPositionCacheInvalidationAfterLineDeletion() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>\n\t");
      myFixture.delete();
      assertEquals(new LogicalPosition(0, 4), myFixture.getEditor().offsetToLogicalPosition(1));
    });
  }
  
  @Test
  public void testSoftWrapsUpdateAfterEditorWasHidden() {
    myFixture.runTest(() -> {
      myFixture.initText("long long line");
      myFixture.configureSoftWraps(6);
      myFixture.verifySoftWrapPositions(5, 10);

      JViewport viewport = ((EditorEx)myFixture.getEditor()).getScrollPane().getViewport();
      Dimension normalSize = viewport.getExtentSize();
      viewport.setExtentSize(new Dimension(0, 0));
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().deleteString(5, 14));

      viewport.setExtentSize(normalSize);
    
      myFixture.verifySoftWrapPositions();
    });
  }
  
  @Test
  public void testUpDownNearDocumentTopAndBottom() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\nd<caret>ef\nghi");
      myFixture.up();
      myFixture.checkResultByText("a<caret>bc\ndef\nghi");
      myFixture.up();
      myFixture.checkResultByText("<caret>abc\ndef\nghi");
      myFixture.down();
      myFixture.checkResultByText("abc\nd<caret>ef\nghi");
      myFixture.down();
      myFixture.checkResultByText("abc\ndef\ng<caret>hi");
      myFixture.down();
      myFixture.checkResultByText("abc\ndef\nghi<caret>");
      myFixture.up();
      myFixture.checkResultByText("abc\nd<caret>ef\nghi");
    });
  }
  
  @Test
  public void testScrollingToCaretAtSoftWrap() {
    myFixture.runTest(() -> {
      myFixture.initText("looooooooooooooooong wooooooooooooooooords");
      myFixture.configureSoftWraps(10);
      myFixture.end();
      VisualPosition caretPosition = myFixture.getEditor().getCaretModel().getCurrentCaret().getVisualPosition();
      Point caretPoint = myFixture.getEditor().visualPositionToXY(caretPosition);
      Rectangle visibleArea = myFixture.getEditor().getScrollingModel().getVisibleArea();
      assertTrue(visibleArea.contains(caretPoint));
    });
  }

  @Test
  public void testChangingHighlightersInBulkModeListener() {
    myFixture.runTest(() -> {
      myFixture.initText("abcdef");
      Document document = myFixture.getEditor().getDocument();
      document.addDocumentListener(new DocumentListener() {
        @Override
        public void bulkUpdateFinished(@NotNull Document document) {
          myFixture.getEditor().getMarkupModel().addRangeHighlighter(null, 7, 8, 0, HighlighterTargetArea.EXACT_RANGE);
        }
      }, myFixture.getTestRootDisposable());
      myFixture.runWriteCommand(() -> DocumentUtil.executeInBulk(document, ()-> document.insertString(3, "\n\n")));
      RangeHighlighter[] highlighters =
        ContainerUtil.findAllAsArray(myFixture.getEditor().getMarkupModel().getAllHighlighters(), h -> h.isValid());
      assertEquals(1, highlighters.length);
      assertEquals(7, highlighters[0].getStartOffset());
      assertEquals(8, highlighters[0].getEndOffset());
    });
  }

  @Test
  public void testChangingHighlightersAfterClearingFoldingsDuringFoldingBatchUpdate() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\n\ndef");
      myFixture.addCollapsedFoldRegion(2, 6, "...");
      myFixture.runFoldingOperation(() -> {
        ((FoldingModelEx)myFixture.getEditor().getFoldingModel()).clearFoldRegions();
        myFixture.getEditor().getMarkupModel().addRangeHighlighter(7, 8, 0,
                                                         new TextAttributes(null, null, null, null, Font.BOLD),
                                                         HighlighterTargetArea.EXACT_RANGE);
      });
      RangeHighlighter[] highlighters =
        ContainerUtil.findAllAsArray(myFixture.getEditor().getMarkupModel().getAllHighlighters(), h -> h.isValid());
      assertEquals(1, highlighters.length);
      assertEquals(7, highlighters[0].getStartOffset());
      assertEquals(8, highlighters[0].getEndOffset());
    });
  }

  @Test
  public void testShiftPressedBeforeDragOverLineNumbersIsFinished() {
    myFixture.runTest(() -> {
      if (ExperimentalUI.isNewUI()) {
        // TODO: rewrite test if needed
        return;
      }
      myFixture.initText("abc\ndef\nghi");
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 1000, 1000); // enable drag testing
      myFixture.mouse().pressAtLineNumbers(0).dragToLineNumbers(2).shift().release();
      myFixture.checkResultByText("<selection>abc\ndef\nghi</selection>");
    });
  }

  @Test
  public void testDefaultVerticalScrolling() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>" + StringUtil.repeat("abc\n", 100));

      int lineHeight = myFixture.getEditor().getLineHeight();
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 10, 10);

      // test for bottom scroll offset
      for (int i = 0; i < 49; ++i) {
        myFixture.down();
      }
      assertEquals(41 * lineHeight, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());

      // test for top scroll offset
      for (int i = 0; i < 20; ++i) {
        myFixture.up();
      }
      assertEquals(28 * lineHeight, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());
    });
  }

  @Test
  public void testOverridingDefaultVerticalScrolling() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>" + StringUtil.repeat("abc\n", 100));
      int scrollOffBefore = myFixture.getEditor().getSettings().getVerticalScrollOffset();
      myFixture.getEditor().getSettings().setVerticalScrollOffset(3);

      int lineHeight = myFixture.getEditor().getLineHeight();
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 10, 10);

      // test for bottom scroll offset
      for (int i = 0; i < 49; ++i) {
        myFixture.down();
      }
      assertEquals(43 * lineHeight, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());

      // test for top scroll offset
      for (int i = 0; i < 20; ++i) {
        myFixture.up();
      }
      assertEquals(26 * lineHeight, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());

      myFixture.getEditor().getSettings().setVerticalScrollOffset(scrollOffBefore);
    });
  }

  @Test
  public void testScrollingInEditorOfSmallHeight() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\n<caret>");
      int lineHeight = myFixture.getEditor().getLineHeight();
      int heightInPixels = (int)(lineHeight * 1.5);
      EditorTestUtil.setEditorVisibleSizeInPixels(myFixture.getEditor(),
                                                  1000 * EditorUtil.getSpaceWidth(Font.PLAIN, myFixture.getEditor()),
                                                  heightInPixels);
      myFixture.getEditor().getSettings().setAnimatedScrolling(false);
      myFixture.type('a');
      assertEquals(lineHeight - (heightInPixels - lineHeight) / 2, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());
      myFixture.type('b');
      assertEquals(lineHeight - (heightInPixels - lineHeight) / 2, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());
    });
  }

  @Test
  public void testVerticalScrollJump() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>" + StringUtil.repeat("abc\n", 100));
      int scrollJumpBefore = myFixture.getEditor().getSettings().getVerticalScrollJump();
      myFixture.getEditor().getSettings().setVerticalScrollJump(7);

      int lineHeight = myFixture.getEditor().getLineHeight();
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 10, 10);

      for (int i = 0; i < 9 - myFixture.getEditor().getSettings().getVerticalScrollOffset(); ++i) {
        myFixture.down();
      }
      assertEquals(0, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());

      myFixture.down();
      assertEquals(7 * lineHeight, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());

      myFixture.getEditor().getSettings().setVerticalScrollJump(scrollJumpBefore);
    });
  }

  @Test
  public void testDefaultHorizontalScrolling() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>" + StringUtil.repeat("abc", 100));
      assertTrue(AsyncEditorLoader.Companion.isEditorLoaded(myFixture.getEditor()));
      int spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, myFixture.getEditor());
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 15, 2);

      // test for right scroll offset
      for (int i = 0; i < 49; ++i) {
        myFixture.right();
      }
      assertEquals(37 * spaceWidth, myFixture.getEditor().getScrollingModel().getHorizontalScrollOffset());

      // test for left scroll offset
      for (int i = 0; i < 20; ++i) {
        myFixture.left();
      } // caret offset is 30 now
      assertEquals(26 * spaceWidth, myFixture.getEditor().getScrollingModel().getHorizontalScrollOffset());
    });
  }

  @Test
  public void testOverridingDefaultHorizontalScrolling() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>" + StringUtil.repeat("abc", 100));
      int scrollOffBefore = myFixture.getEditor().getSettings().getHorizontalScrollOffset();
      myFixture.getEditor().getSettings().setHorizontalScrollOffset(5);

      int spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, myFixture.getEditor());
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 15, 2);

      // test for right scroll offset
      for (int i = 0; i < 49; ++i) {
        myFixture.right();
      }
      assertEquals(39 * spaceWidth, myFixture.getEditor().getScrollingModel().getHorizontalScrollOffset());

      // test for left scroll offset
      for (int i = 0; i < 20; ++i) {
        myFixture.left();
      }
      assertEquals(24 * spaceWidth, myFixture.getEditor().getScrollingModel().getHorizontalScrollOffset());

      myFixture.getEditor().getSettings().setHorizontalScrollOffset(scrollOffBefore);
    });
  }

  @Test
  public void testScrollingInEditorOfSmallWidth() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>" + StringUtil.repeat("abc", 100));
      int spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, myFixture.getEditor());
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 3, 2);

      // test for right scroll offset
      for (int i = 0; i < 49; ++i) {
        myFixture.right();
      }
      assertEquals((int) (47.5 * spaceWidth), myFixture.getEditor().getScrollingModel().getHorizontalScrollOffset());

      // test for left scroll offset
      for (int i = 0; i < 20; ++i) {
        myFixture.left();
      }
      assertEquals((int) (27.5 * spaceWidth), myFixture.getEditor().getScrollingModel().getHorizontalScrollOffset());
    });
  }

  @Test
  public void testHorizontalScrollJump() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>" + StringUtil.repeat("abc", 100));
      int scrollJumpBefore = myFixture.getEditor().getSettings().getHorizontalScrollJump();
      myFixture.getEditor().getSettings().setHorizontalScrollJump(10);

      int spaceWidth = EditorUtil.getSpaceWidth(Font.PLAIN, myFixture.getEditor());
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 15, 2);

      for (int i = 0; i < 15 - myFixture.getEditor().getSettings().getHorizontalScrollOffset(); ++i) {
        myFixture.right();
      }
      assertEquals(0, myFixture.getEditor().getScrollingModel().getHorizontalScrollOffset());

      myFixture.right();
      assertEquals(10 * spaceWidth, myFixture.getEditor().getScrollingModel().getHorizontalScrollOffset());

      myFixture.getEditor().getSettings().setHorizontalScrollJump(scrollJumpBefore);
    });
  }

  @Test
  public void testEditorWithSoftWrapsBecomesVisibleAfterDocumentTextRemoval() {
    myFixture.runTest(() -> {
      myFixture.initText("abc def ghi");
      myFixture.configureSoftWraps(3);
      JViewport viewport = ((EditorEx)myFixture.getEditor()).getScrollPane().getViewport();
      viewport.setExtentSize(new Dimension()); // emulate editor becoming invisible
      myFixture.runWriteCommand(() -> {
          Document document = myFixture.getEditor().getDocument();
          document.deleteString(0, document.getTextLength());
      });
      viewport.setExtentSize(new Dimension(1000, 1000)); // editor becomes visible
      myFixture.verifySoftWrapPositions();
    });
  }

  @Test
  public void testMoveCaretLeftWithVirtualSpace() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\nde<caret>f");
      myFixture.getEditor().getSettings().setVirtualSpace(true);
      myFixture.left();
      assertEquals(5, myFixture.getEditor().getCaretModel().getOffset());
      myFixture.getEditor().getSettings().setVirtualSpace(false);
    });
  }

  @Test
  public void testMoveCaretAtLineStartLeftWithVirtualSpace() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\n<caret>def");
      myFixture.getEditor().getSettings().setVirtualSpace(true);
      myFixture.left();
      assertEquals(3, myFixture.getEditor().getCaretModel().getOffset());
      myFixture.getEditor().getSettings().setVirtualSpace(false);
    });
  }

  @Test
  public void testMoveCaretAtFileStartLeftWithVirtualSpace() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>abc\ndef");
      myFixture.getEditor().getSettings().setVirtualSpace(true);
      myFixture.left();
      assertEquals(0, myFixture.getEditor().getCaretModel().getOffset());
      myFixture.getEditor().getSettings().setVirtualSpace(false);
    });
  }

  @Test
  public void testDocumentChangeAfterEditorDisposal() {
    myFixture.runTest(() -> {
      EditorFactory editorFactory = EditorFactory.getInstance();

      Document document = editorFactory.createDocument("ab");
      Editor editor = editorFactory.createEditor(document);
      try {
        editor.getCaretModel().moveToOffset(1);
      }
      finally {
        editorFactory.releaseEditor(editor);
      }

      myFixture.runWriteCommand(() -> document.setText("cd")); // should run without throwing an exception
    });
  }

  @Test
  public void testLineLengthMatchingLogicalPositionCacheFrequency() {
    myFixture.runTest(() -> {
      myFixture.initText("\t" + StringUtil.repeat(" ", 1023));
      assertEquals(new LogicalPosition(0, 1027), myFixture.getEditor().offsetToLogicalPosition(1024));
    });
  }

  @Test
  public void testSpecialCaseOfCaretPositionUpdateOnFolding() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef\ngh<caret>i");
      FoldRegion region = myFixture.addCollapsedFoldRegion(1, 11, "...");
      myFixture.runWriteCommand(()-> myFixture.getEditor().getDocument().deleteString(7, 8));
      myFixture.runFoldingOperation(()-> region.setExpanded(true));
      myFixture.runFoldingOperation(()-> region.setExpanded(false));
      assertFalse(region.isExpanded());
    });
  }

  @Test
  public void testEditingNearInlayInBulkMode() {
    myFixture.runTest(() -> {
      myFixture.initText("a<caret>bc");
      myFixture.addInlay(1);
      myFixture.runWriteCommand(()-> DocumentUtil.executeInBulk(myFixture.getEditor().getDocument(),
                                                      ()-> myFixture.getEditor().getDocument().insertString(1, " ")));
      myFixture.checkResultByText("a<caret> bc");
      assertTrue(myFixture.getEditor().getInlayModel().hasInlineElementAt(1));
    });
  }

  @Test
  public void testCoordinateConversionsAroundSurrogatePair() {
    myFixture.runTest(() -> {
      myFixture.initText("a" + SURROGATE_PAIR + "b");
      assertEquals(new LogicalPosition(0, 2), myFixture.getEditor().offsetToLogicalPosition(3));
      assertEquals(3, myFixture.getEditor().logicalPositionToOffset(new LogicalPosition(0, 2)));
      assertEquals(new VisualPosition(0, 2), myFixture.getEditor().logicalToVisualPosition(new LogicalPosition(0, 2)));
      assertEquals(new LogicalPosition(0, 2), myFixture.getEditor().visualToLogicalPosition(new VisualPosition(0, 2)));
    });
  }

  @Test
  public void testCreationOfSurrogatePairByMergingDividedParts() {
    myFixture.runTest(() -> {
      myFixture.initText(""); // Cannot set up text with singular surrogate characters directly
      myFixture.runWriteCommand(() -> {
        myFixture.getEditor().getDocument().setText(HIGH_SURROGATE + " " + LOW_SURROGATE);
        myFixture.getEditor().getDocument().deleteString(1, 2);
      });
      myFixture.checkResultByText(SURROGATE_PAIR);
      assertEquals(new LogicalPosition(0, 1), myFixture.getEditor().offsetToLogicalPosition(2));
    });
  }

  @Test
  public void testCaretDoesntGetInsideSurrogatePair() {
    myFixture.runTest(() -> {
      myFixture.initText(""); // Cannot set up text with singular surrogate characters directly
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
      myFixture.getEditor().getCaretModel().moveToOffset(2);
      myFixture.runWriteCommand(() -> ((DocumentEx)myFixture.getEditor().getDocument()).moveText(2, 3, 1));
      assertFalse(DocumentUtil.isInsideSurrogatePair(myFixture.getEditor().getDocument(),
                                                     myFixture.getEditor().getCaretModel().getOffset()));
    });
  }

  @Test
  public void testFoldingBoundaryDoesntGetInsideSurrogatePair() {
    myFixture.runTest(() -> {
      myFixture.initText(""); // Cannot set up text with singular surrogate characters directly
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().setText(HIGH_SURROGATE + LOW_SURROGATE + LOW_SURROGATE));
      myFixture.addFoldRegion(2, 3, "...");
      myFixture.runWriteCommand(() -> ((DocumentEx)myFixture.getEditor().getDocument()).moveText(2, 3, 1));
      FoldRegion[] foldRegions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
      assertFalse(foldRegions.length > 0 &&
                  DocumentUtil.isInsideSurrogatePair(myFixture.getEditor().getDocument(), foldRegions[0].getStartOffset()));
    });
  }

  @Test
  public void testRemovalOfNonExpandingFoldingRegion() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb");
      FoldingModelEx model = (FoldingModelEx)myFixture.getEditor().getFoldingModel();
      Ref<FoldRegion> regionRef = new Ref<>();
      myFixture.runFoldingOperation(() -> {
        FoldRegion region = model.createFoldRegion(0, 3, "...", null, true);
        assertNotNull(region);
        assertTrue(region.isValid());
        assertFalse(region.isExpanded());
        regionRef.set(region);
      });
      myFixture.runFoldingOperation(() -> model.removeFoldRegion(regionRef.get()));
      assertEquals(new VisualPosition(1, 1), myFixture.getEditor().offsetToVisualPosition(3));
    });
  }
  
  @Test
  public void testPasteWithStickySelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>abc<caret></selection>\ndef");
      myFixture.copy();
      myFixture.down();
      myFixture.home();
      ((EditorEx)myFixture.getEditor()).setStickySelection(true);
      myFixture.right();
      myFixture.right();
      myFixture.right();
      myFixture.paste();
      myFixture.checkResultByText("abc\nabc<caret>");
    });
  }
  
  @Test
  public void testPasteInVirtualSpaceWhenSelectionExists() {
    myFixture.runTest(() -> {
      myFixture.initText("foo");
      myFixture.getEditor().getSettings().setVirtualSpace(true);
      myFixture.mouse().clickAt(0, 10);
      myFixture.executeAction(IdeActions.ACTION_SELECT_ALL);
      CopyPasteManager.getInstance().setContents(new StringSelection("bar"));
      myFixture.paste();
      myFixture.checkResultByText("bar<caret>");
    });
  }

  @Test
  public void testMoveTextNearInlayAtBrokenSurrogates() {
    myFixture.runTest(() -> {
      myFixture.initText(""); // Cannot set up text with singular surrogate characters directly
      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().setText(LOW_SURROGATE + HIGH_SURROGATE));
      Inlay<?> inlay = myFixture.addInlay(2);
      FoldRegion region = myFixture.addCollapsedFoldRegion(1, 2, "...");
    
      myFixture.runWriteCommand(() -> ((DocumentEx)myFixture.getEditor().getDocument()).moveText(1, 2, 0));
    
      assertFalse(inlay.isValid() && inlay.getOffset() == 1);
      assertFalse(region.isValid() && (region.getStartOffset() == 1 || region.getEndOffset() == 1));
    });
  }

  @Test
  public void testStickySelectionAtDocumentEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("ab<caret>");
      ((EditorEx)myFixture.getEditor()).setStickySelection(true);
      myFixture.left();
      myFixture.checkResultByText("a<selection><caret>b</selection>");
    });
  }

  @Test
  public void testDocumentUpdateAtCaretInVirtualSpace() {
    myFixture.runTest(() -> {
      myFixture.initText("ab\ncd");
      myFixture.getEditor().getSettings().setVirtualSpace(true);
      myFixture.mouse().clickAt(0, 4);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
      myFixture.checkResultByText("ab<caret>cd");
      assertEquals(new LogicalPosition(0, 2), myFixture.getEditor().getCaretModel().getLogicalPosition());
      assertEquals(new VisualPosition(0, 2), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  @Test
  public void testTypingInOverwriteModeWithMultilineSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("a<selection>b\nc<caret></selection>d");
      ((EditorEx)myFixture.getEditor()).setInsertMode(false);
      myFixture.type(' ');
      myFixture.checkResultByText("a <caret>");
    });
  }

  @Test
  public void testSettingPromptDoesNotCreateSoftWraps() {
    myFixture.runTest(() -> {
      myFixture.initText(StringUtil.repeat("a ", 1000));
      ((EditorEx)myFixture.getEditor()).setPrefixTextAndAttributes(">", new TextAttributes());
      myFixture.runWriteCommand(
        () -> myFixture.getEditor().getDocument().deleteString(0, myFixture.getEditor().getDocument().getTextLength()));
      assertEquals(1, ((EditorImpl)myFixture.getEditor()).getVisibleLineCount());
    });
  }

  @Test
  public void testDragInsideSelectionWithDndDisabled() {
    myFixture.runTest(() -> {
      myFixture.initText("abcdef");
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 100);
      myFixture.getEditor().getSettings().setDndEnabled(false);
      EditorMouseFixture mouse = myFixture.mouse();
      mouse.pressAt(0, 0).dragTo(0, 3).release();
      myFixture.checkResultByText("<selection>abc<caret></selection>def");
      mouse.pressAt(0, 1);
      myFixture.checkResultByText("a<caret>bcdef");
      mouse.dragTo(0, 4);
      myFixture.checkResultByText("a<selection>bcd<caret></selection>ef");
      mouse.release();
      myFixture.checkResultByText("a<selection>bcd<caret></selection>ef");
    });
  }

  @Test
  public void testCreateRectangularSelectionWithDndDisabled() {
    myFixture.runTest(() -> {
      myFixture.initText("ab\ncd<caret>");
      myFixture.getEditor().getSettings().setDndEnabled(false);
      myFixture.mouse().alt().shift().middle().clickAt(0, 0);
      myFixture.checkResultByText("<selection><caret>ab</selection>\n<selection><caret>cd</selection>");
    });
  }

  @Test
  public void testActionsFromPopupMenuCanBeInvokedForSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("abcd");
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 100);
      myFixture.getEditor().getSettings().setDndEnabled(false);
      EditorMouseFixture mouse = myFixture.mouse();
    
      mouse.pressAt(0, 1).dragTo(0, 3).release();
      mouse.right().pressAt(0, 2);
      myFixture.checkResultByText("a<selection>bc</selection>d");
      mouse.release();
    });
  }

  @Test
  public void testTripleClickWithDndDisabled() {
    myFixture.runTest(() -> {
      myFixture.initText("some text");
      myFixture.getEditor().getSettings().setDndEnabled(false);
      myFixture.mouse().tripleClickAt(0, 2);
      myFixture.checkResultByText("<selection>some text</selection>");
    });
  }

  @Test
  public void testSelectionAfterSelectingWholeLine() {
    myFixture.runTest(() -> {
      myFixture.initText("line 1\nline 2");
      myFixture.mouse().tripleClickAt(0, 2);
      myFixture.checkResultByText("<selection>li<caret>ne 1\n</selection>line 2");
      myFixture.delete();
      myFixture.checkResultByText("<caret>line 2");
      myFixture.rightWithSelection();
      myFixture.checkResultByText("<selection>l<caret></selection>ine 2");
    });
  }

  @Test
  public void testRightClickOutsideSelectionRemovesIt() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>some<caret></selection> text");
      myFixture.mouse().right().clickAt(0, 6);
      myFixture.checkResultByText("some t<caret>ext");
    });
  }

  @Test
  public void testRightClickOutsideButVeryCloseToSelectionDoesNotRemoveIt() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>some<caret></selection> text");
      myFixture.mouse().right().clickAtXY(42, 5);
      myFixture.checkResultByText("<selection>some<caret></selection> text");
    });
  }

  @Test
  public void testDragStartingBeyondSelectedLineEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>line1</selection>\nline2");
      myFixture.setEditorVisibleSize(100, 100);
      myFixture.mouse().pressAt(0, 10).dragTo(1, 10).release();
      myFixture.checkResultByText("line1<selection>\nline2</selection>");
    });
  }

  @Test
  public void testSizeUpdateAfterMovingFragmentWithFolding() {
    myFixture.runTest(() -> {
      myFixture.initText("line\nline\nline\nline\nlong line");
      myFixture.getEditor().getSettings().setAdditionalColumnsCount(0);
      myFixture.addCollapsedFoldRegion(19, 25, "...");
      myFixture.runWriteCommand(() -> ((EditorEx)myFixture.getEditor()).getDocument().moveText(19, 25, 0));
      assertEquals(80, myFixture.getEditor().getContentComponent().getPreferredSize().width);
    });
  }

  @Test
  public void testEditorIsNotScrolledOnSoftWrappingIfNotScrolledPreviously() {
    myFixture.runTest(() -> {
      myFixture.initText("long long line");
      EditorTestUtil.configureSoftWrapsAndViewport(myFixture.getEditor(), 100, 1);
      EditorTestUtil.configureSoftWrapsAndViewport(myFixture.getEditor(), 10, 1);
      assertEquals(0, myFixture.getEditor().getScrollingModel().getVerticalScrollOffset());
    });
  }

  @Test
  public void testClickOnBlockInlayDoesNotMoveCaret() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.addBlockInlay(0, true, 10 * TEST_CHAR_WIDTH);
      myFixture.mouse().clickAtXY(2 * TEST_CHAR_WIDTH, 0);
      myFixture.checkResultByText("<caret>text");
    });
  }

  @Test
  public void testDragInBlockInlayDoesNotCreateSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.addBlockInlay(0, true, 10 * TEST_CHAR_WIDTH);
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 100);
      myFixture.mouse().pressAtXY(2 * TEST_CHAR_WIDTH, 0).dragToXY(4 * TEST_CHAR_WIDTH, 0).release();
      myFixture.checkResultByText("<caret>text");
    });
  }

  @Test
  public void testDragBeyondEditorTopWithBlockInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("text");
      myFixture.addBlockInlay(0, true);
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 100);
      myFixture.mouse().pressAtXY(2 * TEST_CHAR_WIDTH, (int)(myFixture.getEditor().getLineHeight() * 1.5)).dragToXY(-1, -1).release();
      myFixture.checkResultByText("<selection><caret>te</selection>xt");
    });
  }

  @Test
  public void testCaretMovementAtFoldRegionWithEmptyPlaceholder() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>abc");
      myFixture.addCollapsedFoldRegion(1, 2, "");
      myFixture.right();
      Caret caret = myFixture.getEditor().getCaretModel().getPrimaryCaret();
      assertEquals(2, caret.getOffset());
      assertEquals(new LogicalPosition(0, 2), caret.getLogicalPosition());
      assertEquals(new VisualPosition(0, 1), caret.getVisualPosition());
      myFixture.right();
      assertEquals(3, caret.getOffset());
      assertEquals(new LogicalPosition(0, 3), caret.getLogicalPosition());
      assertEquals(new VisualPosition(0, 2), caret.getVisualPosition());
    });
  }

  @Test
  public void testDragStartingAtBlockInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("abc<caret>");
      myFixture.addBlockInlay(0, true, 10);
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 100);
      myFixture.mouse().pressAtXY(0, 0).dragToXY(0, myFixture.getEditor().getLineHeight()).release();
      myFixture.checkResultByText("abc<caret>");
    });
  }

  @Test
  public void testMouseDraggingWithCamelHumpsDisabledForMouse() {
    myFixture.runTest(() -> {
      boolean savedOption = EditorSettingsExternalizable.getInstance().isCamelWords();
      try {
        EditorSettingsExternalizable.getInstance().setCamelWords(true);
        myFixture.initText("AbcDefGhi");
        EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 100);
        myFixture.getEditor().getSettings().setMouseClickSelectionHonorsCamelWords(false);
        myFixture.mouse().doubleClickNoReleaseAt(0, 4).dragTo(0, 5).release();
        myFixture.checkResultByText("<selection>AbcDefGhi<caret></selection>");
      }
      finally {
        EditorSettingsExternalizable.getInstance().setCamelWords(savedOption);
      }
    });
  }

  @Test
  public void testSettingFontPreferences() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      EditorColorsScheme colorsScheme = myFixture.getEditor().getColorsScheme();

      FontPreferencesImpl preferences = new FontPreferencesImpl();
      preferences.register("CustomFont", 32);
      preferences.setUseLigatures(true);
      colorsScheme.setFontPreferences(preferences);

      FontPreferences p = colorsScheme.getFontPreferences();
      assertEquals(Collections.singletonList("CustomFont"), p.getRealFontFamilies());
      assertEquals(32, p.getSize("CustomFont"));
      assertTrue(p.useLigatures());

      FontPreferencesImpl preferences2 = new FontPreferencesImpl();
      preferences2.register("CustomFont2", 23);
      preferences2.setUseLigatures(false);
      colorsScheme.setFontPreferences(preferences2);

      FontPreferences p2 = colorsScheme.getFontPreferences();
      assertEquals(Collections.singletonList("CustomFont2"), p2.getRealFontFamilies());
      assertEquals(23, p2.getSize("CustomFont2"));
      assertFalse(p2.useLigatures());
      assertTrue(p.useLigatures(), "The font preferences are a snapshot, so a later change does not change them");
    });
  }

  @Test
  public void testDocumentChangeAfterWidthChange() {
    myFixture.runTest(() -> {
      myFixture.initText("Some long line of text");
      myFixture.configureSoftWraps(15);

      // emulate adding editor to a wide component
      SoftWrapApplianceManager.VisibleAreaWidthProvider widthProvider =
        ((SoftWrapModelImpl)myFixture.getEditor().getSoftWrapModel()).getApplianceManager().getWidthProvider();
      ((EditorTestUtil.TestWidthProvider)widthProvider).setVisibleAreaWidth(1_000_000);
      new JPanel().add(myFixture.getEditor().getComponent());

      myFixture.runWriteCommand(() -> myFixture.getEditor().getDocument().insertString(0, " "));
      myFixture.verifySoftWrapPositions();
    });
  }

  @Test
  public void testClickOnBlockInlayDoesNotRemoveSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>text<caret></selection>");
      myFixture.addBlockInlay(0, true, 100);
      myFixture.mouse().clickAtXY(50, myFixture.getEditor().getLineHeight() / 2);
      myFixture.checkResultByText("<selection>text<caret></selection>");
    });
  }

  @Test
  public void testWordSelectionOnMouseDragAroundPunctuation() {
    myFixture.runTest(() -> {
      myFixture.initText("((some (text)))");
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 100, 100);
      myFixture.mouse().doubleClickNoReleaseAt(0, 4).dragTo(0, 12).release();
      myFixture.checkResultByText("((<selection>some (text)<caret></selection>))");
    });
  }

  @Test
  public void testSurrogatePairInputInOverwriteMode() {
    myFixture.runTest(() -> {
      myFixture.initText("ab<caret>cd");
      ((EditorEx)myFixture.getEditor()).setInsertMode(false);
      JComponent component = myFixture.getEditor().getContentComponent();
      component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                   new AttributedString("\uD83D\uDE00" /* 'GRINNING FACE' emoji (U+1F600) */).getIterator(),
                                                   2, null, null));
      myFixture.checkResultByText("ab\uD83D\uDE00d");
    });
  }

  @Test
  public void testInputMethodComposing() {
    myFixture.runTest(() -> {
      myFixture.initText("hello<caret>");
      JComponent component = myFixture.getEditor().getContentComponent();
      component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                   new AttributedString("\u3145" /* ㅅ */).getIterator(),
                                                   0, TextHitInfo.afterOffset(0),
                                                   TextHitInfo.afterOffset(-1)));
      component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                   new AttributedString("\u3146" /* ㅆ */).getIterator(),
                                                   0, TextHitInfo.afterOffset(0),
                                                   TextHitInfo.afterOffset(-1)));
      myFixture.checkResultByText("hello\u3146");
    });
  }

  @Test
  public void testInputMethodCaretMove() {
    myFixture.runTest(() -> {
      myFixture.initText("hello<caret>");
      JComponent component = myFixture.getEditor().getContentComponent();
      component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                   new AttributedString("\u3145" /* ㅅ */).getIterator(),
                                                   0, null, null));
      component.dispatchEvent(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED,
                                                   new AttributedString("\u3146" /* ㅆ */).getIterator(),
                                                   0, null, null));
      myFixture.mouse().clickAtXY(0, myFixture.getEditor().getLineHeight() / 2);
      myFixture.checkResultByText("<caret>hello\u3146");
    });
  }

  @Test
  public void testScrollingToCaretWithWideCharacters() {
    myFixture.runTest(() -> {
      FontLayoutService.setInstance(new MockFontLayoutService(cp -> cp == 'Z' ? 2 * TEST_CHAR_WIDTH : TEST_CHAR_WIDTH,
                                                              TEST_LINE_HEIGHT, TEST_DESCENT));
      myFixture.initText(StringUtil.repeatSymbol('Z', 500) + "<caret>");
      myFixture.setEditorVisibleSize(100, 100);
      myFixture.type(' ');
      Rectangle visibleArea = myFixture.getEditor().getScrollingModel().getVisibleAreaOnScrollingFinished();
      Point caretPosition = myFixture.getEditor().visualPositionToXY(myFixture.getEditor().getCaretModel().getVisualPosition());
      assertTrue(visibleArea.contains(caretPosition));
    });
  }

  @Test
  public void testMouseSelectionWithBlockCaret() {
    myFixture.runTest(() -> {
      Registry.get("editor.block.caret.selection.vim-like").setValue(true, myFixture.getTestRootDisposable());
      myFixture.initText("abcdef");
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 1000, 1000);  // enable drag testing
      myFixture.getEditor().getSettings().setBlockCursor(true);
      int y = myFixture.getEditor().getLineHeight() / 2;

      myFixture.mouse().pressAtXY(3 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("abc<caret>def");
      myFixture.mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("abc<caret>def");
      myFixture.mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("ab<selection><caret>cd</selection>ef");
      myFixture.mouse().dragToXY(TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("a<selection><caret>bcd</selection>ef");
      myFixture.mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("ab<selection><caret>cd</selection>ef");
      myFixture.mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("abc<selection><caret>d</selection>ef");
      myFixture.mouse().dragToXY(4 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("abc<selection>d<caret>e</selection>f");
      myFixture.mouse().dragToXY(5 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("abc<selection>de<caret>f</selection>");
      myFixture.mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("ab<selection><caret>cd</selection>ef");
      myFixture.mouse().dragToXY(5 * TEST_CHAR_WIDTH + 1, y);
      myFixture.checkResultByText("abc<selection>de<caret>f</selection>");
    });
  }

  @Test
  public void testMouseSelectionAtLineEndWithBlockCaret() {
    myFixture.runTest(() -> {
      Registry.get("editor.block.caret.selection.vim-like").setValue(true, myFixture.getTestRootDisposable());
      myFixture.initText("abc\ndef");
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 1000, 1000);  // enable drag testing
      myFixture.getEditor().getSettings().setBlockCursor(true);
      int line1Y = myFixture.getEditor().getLineHeight() / 2;
      int line2Y = myFixture.getEditor().getLineHeight() * 3 / 2;

      myFixture.mouse().pressAtXY(3 * TEST_CHAR_WIDTH + 1, line1Y);
      myFixture.checkResultByText("abc<caret>\ndef");
      myFixture.mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, line1Y);
      myFixture.checkResultByText("abc<caret>\ndef");
      myFixture.mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, line1Y);
      myFixture.checkResultByText("ab<selection><caret>c\n</selection>def");
      myFixture.mouse().dragToXY(TEST_CHAR_WIDTH + 1, line1Y);
      myFixture.checkResultByText("a<selection><caret>bc\n</selection>def");
      myFixture.mouse().dragToXY(2 * TEST_CHAR_WIDTH + 1, line1Y);
      myFixture.checkResultByText("ab<selection><caret>c\n</selection>def");
      myFixture.mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, line1Y);
      myFixture.checkResultByText("abc<selection><caret>\n</selection>def");
      myFixture.mouse().dragToXY(3 * TEST_CHAR_WIDTH + 1, line2Y);
      myFixture.checkResultByText("abc<selection>\ndef<caret></selection>");
      myFixture.mouse().dragToXY(1, line2Y);
      myFixture.checkResultByText("abc<selection>\n<caret>d</selection>ef");
      myFixture.mouse().dragToXY(1, line1Y);
      myFixture.checkResultByText("<selection><caret>abc\n</selection>def");
    });
  }

  @Test
  public void testPressAndHoldInputMethodOnMac() {
    myFixture.runTest(() -> {
      if (!SystemInfo.isMac) return; // macOS-specific test
      myFixture.initText("<caret>b");
      JComponent component = myFixture.getEditor().getContentComponent();
      // 'A' key pressed
      dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a', KeyEvent.KEY_LOCATION_STANDARD));
      dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a', KeyEvent.KEY_LOCATION_UNKNOWN));
      myFixture.checkResultByText("a<caret>b");
      // popup with diacritic appears while key is held pressed
      requestSelectedTextFromEditorViaImeApi();
      myFixture.checkResultByText("a<caret>b");
      // 'A' key released
      dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_RELEASED, 1, 0, KeyEvent.VK_A, 'a', KeyEvent.KEY_LOCATION_STANDARD));
      myFixture.checkResultByText("a<caret>b");
      // 'Right arrow' key pressed to select first variant from the popup
      requestSelectedTextFromEditorViaImeApi();
      dispatchEventToEditor(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, 2,
                                                   new AttributedString("\u00e0" /* LATIN SMALL LETTER A WITH GRAVE */).getIterator(), 0,
                                                   null, TextHitInfo.beforeOffset(0)));
      myFixture.checkResultByText("\u00e0b"); // not checking caret/selection state, as we don't need to enforce the behaviour as of test creation time
      // 'Right arrow' key released
      dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_RELEASED, 3, 0, KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED,
                                           KeyEvent.KEY_LOCATION_STANDARD));
      myFixture.checkResultByText("\u00e0b"); // not checking caret/selection state, as we don't need to enforce the behaviour as of test creation time
      // 'Enter' key pressed to confirm selected variant
      requestSelectedTextFromEditorViaImeApi();
      dispatchEventToEditor(new InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, 4,
                                                   new AttributedString("\u00e0" /* LATIN SMALL LETTER A WITH GRAVE */).getIterator(), 1,
                                                   TextHitInfo.afterOffset(0), TextHitInfo.afterOffset(0)));
      myFixture.checkResultByText("\u00e0<caret>b");
      // 'Enter' key released
      dispatchEventToEditor(new KeyEvent(component, KeyEvent.KEY_RELEASED, 5, 0, KeyEvent.VK_ENTER, '\n', KeyEvent.KEY_LOCATION_STANDARD));
      myFixture.checkResultByText("\u00e0<caret>b");
    });
  }

  @Test
  public void testSelectionOutsideVisibleAreaWithBlockCaret() {
    myFixture.runTest(() -> {
      Registry.get("editor.block.caret.selection.vim-like").setValue(true, myFixture.getTestRootDisposable());
      myFixture.initText(StringUtil.repeatSymbol('\n', 20));
      EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 10, 10);  // enable drag testing
      myFixture.getEditor().getSettings().setBlockCursor(true);
      Registry.get("editor.scrolling.animation.interval.ms").setValue(1, myFixture.getTestRootDisposable()); // make scrolling faster

      EditorMouseFixture mouse = myFixture.mouse();
      mouse.pressAt(0, 0).dragTo(15, 0);

      // scroll till all text is selected
      while (myFixture.getEditor().getSelectionModel().getSelectionEnd() != myFixture.getEditor().getDocument().getTextLength()) {
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      }

      Rectangle visibleArea = myFixture.getEditor().getScrollingModel().getVisibleArea();
      mouse.dragToXY((int)visibleArea.getCenterX(), (int)visibleArea.getCenterY()).release(); // stop scrolling

      assertEquals(0, myFixture.getEditor().getSelectionModel().getSelectionStart());
      assertTrue(myFixture.getEditor().getSelectionModel().getSelectionEnd() > 0);
    });
  }

  @Test
  public void testLeadSelectionPosition() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>abc");
      Caret caret = myFixture.getEditor().getCaretModel().getPrimaryCaret();

      caret.moveToOffset(1);
      caret.setSelection(0, 1);

      assertEquals(0, caret.getLeadSelectionOffset());
      assertEquals(new VisualPosition(0, 0), caret.getLeadSelectionPosition());
    });
  }

  // IJPL-214455 editor should survive exceptions thrown by document listeners
  @Test
  public void testEditorIsNotCorruptedByNPE() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>");
      //noinspection deprecation
      myFixture.getEditor().getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          throw new NullPointerException("dummy");
        }
      });
      assertErrorLogged(NullPointerException.class, () -> myFixture.type('a'));
      assertErrorLogged(NullPointerException.class, () -> myFixture.type('b'));
      assertErrorLogged(NullPointerException.class, () -> myFixture.type('c'));
      myFixture.checkResultByText("abc<caret>");
    });
  }

  // IJPL-214455 editor should survive exceptions thrown by document listeners
  @Test
  public void testEditorIsNotCorruptedByPCE() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>");
      //noinspection deprecation
      myFixture.getEditor().getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          throw new ProcessCanceledException();
        }
      });
      /* TODO: fix me IJPL-214455
      assertErrorLoggedAndRethrown(ProcessCanceledException.class, () -> myFixture.type('a'));
      assertErrorLoggedAndRethrown(ProcessCanceledException.class, () -> myFixture.type('b'));
      assertErrorLoggedAndRethrown(ProcessCanceledException.class, () -> myFixture.type('c'));
      myFixture.checkResultByText("abc<caret>");
      */
    });
  }

  // IJPL-214455 editor should survive exceptions thrown by document listeners
  @Test
  public void testEditorIsNotCorruptedByNonSuppressibleException() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>");
      NullPointerException cachedException = new NullPointerException("dummy");
      //noinspection deprecation
      myFixture.getEditor().getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent event) {
          throw cachedException;
        }
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          throw cachedException;
        }
      });
      assertErrorLoggedTwice(NullPointerException.class, () -> myFixture.type('a'));
      assertErrorLoggedTwice(NullPointerException.class, () -> myFixture.type('b'));
      assertErrorLoggedTwice(NullPointerException.class, () -> myFixture.type('c'));
      myFixture.checkResultByText("abc<caret>");
    });
  }

  // IJPL-251200
  @Test
  public void testAltClickOnFoldingAnchorTogglesNestedRegions() {
    myFixture.runTest(() -> {
      myFixture.initText("a{\nb{\nc\n}\nd{\ne\n}\n}");
      FoldRegion outer = myFixture.addCollapsedFoldRegion(1, 18, "...");
      FoldRegion first = myFixture.addCollapsedFoldRegion(4, 9, "...");
      FoldRegion second = myFixture.addCollapsedFoldRegion(11, 16, "...");

      altClickFoldingAnchor(outer);
      assertTrue(outer.isExpanded());
      assertTrue(first.isExpanded());
      assertTrue(second.isExpanded());

      altClickFoldingAnchor(outer);
      assertFalse(outer.isExpanded());
      assertFalse(first.isExpanded());
      assertFalse(second.isExpanded());
    });
  }

  private void altClickFoldingAnchor(@NotNull FoldRegion region) {
    EditorEx editor = (EditorEx)myFixture.getEditor();
    EditorGutterComponentEx gutter = editor.getGutterComponentEx();
    int x = gutter.getFoldingAreaOffset() + 1; // the anchor hit area excludes its left edge
    int[] lineYRange = editor.visualLineToYRange(editor.offsetToVisualPosition(region.getStartOffset()).line);
    int y = IntStream.range(lineYRange[0], lineYRange[1])
      .filter(lineY -> gutter.findFoldingAnchorAt(x, lineY) == region)
      .findFirst()
      .orElseThrow();
    gutter.dispatchEvent(new MouseEvent(gutter, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                                        InputEvent.ALT_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
  }

  private void dispatchEventToEditor(AWTEvent event) {
    // this method ensures key events are dispatched to editor component, even if it's not focused
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(myFixture.getEditor().getContentComponent(), event);
  }

  private void requestSelectedTextFromEditorViaImeApi() {
    myFixture.getEditor().getContentComponent().getInputMethodRequests().getSelectedText(null);
  }

  private static <T extends Throwable> void assertErrorLogged(Class<T> clazz, ThrowableRunnable<?> runnable) {
    TestLoggerKt.assertErrorLogged(clazz, runnable);
  }

  @SuppressWarnings("SameParameterValue")
  private static <T extends Throwable> void assertErrorLoggedAndRethrown(Class<T> clazz, ThrowableRunnable<?> runnable) {
    UsefulTestCase.assertThrows(clazz, () -> assertErrorLogged(clazz, runnable));
  }

  @SuppressWarnings("SameParameterValue")
  private static <T extends Throwable> void assertErrorLoggedTwice(Class<T> clazz, ThrowableRunnable<?> runnable) {
    try {
      assertErrorLogged(clazz, runnable);
    }
    catch (TestLoggerFactory.TestLoggerAssertionError ex) {
      Throwable[] suppressed = ex.getSuppressed();
      assertThat(suppressed).hasSize(2);
      for (Throwable throwable : suppressed) {
        assertInstanceOf(LoggedError.class, throwable);
        assertInstanceOf(clazz, throwable.getCause());
      }
    }
  }
}
