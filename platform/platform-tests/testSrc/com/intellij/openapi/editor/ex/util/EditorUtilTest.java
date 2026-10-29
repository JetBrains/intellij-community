/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.ex.util;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.impl.BreakpointArea;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.editor.impl.InterLineBreakpointConfiguration;
import com.intellij.openapi.editor.impl.InterLineBreakpointConfigurationProvider;
import com.intellij.openapi.editor.impl.InterLineBreakpointHitArea;
import com.intellij.openapi.editor.impl.InterLineBreakpointProperties;
import com.intellij.openapi.editor.impl.Interval;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.DocumentUtil;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.editor.markup.FontFamilies;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class EditorUtilTest {
  private static final ExtensionPointName<InterLineBreakpointConfigurationProvider> INTER_LINE_CONFIGURATION_PROVIDERS =
    ExtensionPointName.create("com.intellij.editor.interLineBreakpointConfigurationProvider");

  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testFontFamiliesImpactFontStyle() {
    TextAttributes attributes = new TextAttributes();
    assertFalse(EditorUtil.attributesImpactFontStyle(attributes));
    assertFalse(EditorUtil.attributesImpactFontStyleOrColor(attributes));

    attributes.setFontFamilies(FontFamilies.of("JetBrains Mono"));

    assertTrue(EditorUtil.attributesImpactFontStyle(attributes));
    assertTrue(EditorUtil.attributesImpactFontStyleOrColor(attributes));
    assertFalse(EditorUtil.attributesImpactForegroundColor(attributes));
  }

  @Test
  public void testGetNotFoldedLineStartEndOffsets() {
    myFixture.runTest(() -> {
      myFixture.configureFromFileText(myFixture.getTestName() + ".txt",
                            "aaa\nbbb\nccc\nddd");
      EditorTestUtil.addFoldRegion(myFixture.getEditor(), 4, 8, "...", true);

      assertVisualLineRange(2, 0, 3);
      assertVisualLineRange(4, 4, 11);
      assertVisualLineRange(7, 4, 11);
      assertVisualLineRange(8, 4, 11);
      assertVisualLineRange(9, 4, 11);
      assertVisualLineRange(13, 12, 15);
    });
  }

  @Test
  public void testScrollFromBeginToEnd() {
    myFixture.runTest(() -> {
      createSmallEditor("<caret>12345\n67890");
      EditorUtil.scrollToTheEnd(myFixture.getEditor());
      // caret should be at the end, but editor shouldn't be scrolled to it
      assertCaretAt(1, 5, false);
    });
  }

  @Test
  public void testScrollFromLastLineToEnd() {
    myFixture.runTest(() -> {
      createSmallEditor("12345\n<caret>12345");
      EditorUtil.scrollToTheEnd(myFixture.getEditor());
      // caret should be at the end, and visible
      assertCaretAt(1, 5, true);
    });
  }

  @Test
  public void testScrollToEndWithEmptyLastLine() {
    myFixture.runTest(() -> {
      createSmallEditor("12345\n12345<caret>\n");
      Rectangle oldVisibleArea = myFixture.getEditor().getScrollingModel().getVisibleAreaOnScrollingFinished();
      EditorUtil.scrollToTheEnd(myFixture.getEditor(), true);

      // caret should be at the end, but only vertical scrolling should happen
      assertCaretAt(2, 0, false);
      Rectangle newVisibleArea = myFixture.getEditor().getScrollingModel().getVisibleAreaOnScrollingFinished();
      assertEquals(oldVisibleArea.x, newVisibleArea.x);
      assertTrue(oldVisibleArea.y < newVisibleArea.y);
    });
  }

  @Test
  public void testLogicalLineToYRange() {
    myFixture.runTest(() -> {
      createEditor("line1\nline2\nlong long line\n");
      assertTrue(EditorTestUtil.configureSoftWraps(myFixture.getEditor(), 10), "Failed to activate soft wrapping");
      int lineHeight = myFixture.getEditor().getLineHeight();

      @NotNull Pair<@NotNull Interval, @Nullable Interval> p1 = EditorUtil.logicalLineToYRange(myFixture.getEditor(), 1);
      assertEquals(lineHeight, p1.first.intervalStart());
      assertEquals(lineHeight * 2, p1.first.intervalEnd());
      assertEquals(lineHeight, p1.second.intervalStart());
      assertEquals(lineHeight * 2, p1.second.intervalEnd());

      @NotNull Pair<@NotNull Interval, @Nullable Interval> p2 = EditorUtil.logicalLineToYRange(myFixture.getEditor(), 2);
      assertEquals(lineHeight * 2, p2.first.intervalStart());
      assertEquals(lineHeight * 4, p2.first.intervalEnd());
      assertEquals(lineHeight * 2, p2.second.intervalStart());
      assertEquals(lineHeight * 4, p2.second.intervalEnd());
    });
  }

  @Test
  public void testLogicalLineToYRangeShared() {
    myFixture.runTest(() -> {
      createEditor("line1\nline2\n");
      EditorTestUtil.addFoldRegion(myFixture.getEditor(), 5, 6, "...", true);

      @NotNull Pair<@NotNull Interval, @Nullable Interval> p = EditorUtil.logicalLineToYRange(myFixture.getEditor(), 1);
      assertEquals(0, p.first.intervalStart());
      assertEquals(myFixture.getEditor().getLineHeight(), p.first.intervalEnd());
      assertNull(p.second);
    });
  }

  @Test
  public void testYToLogicalLineRange() {
    myFixture.runTest(() -> {
      createEditor("line1\nline2\nline3\n");
      EditorTestUtil.addFoldRegion(myFixture.getEditor(), 5, 6, "...", true);
      int lineHeight = myFixture.getEditor().getLineHeight();

      Interval i1 = EditorUtil.yToLogicalLineRange(myFixture.getEditor(), lineHeight / 2);
      assertEquals(0, i1.intervalStart());
      assertEquals(1, i1.intervalEnd());

      Interval i2 = EditorUtil.yToLogicalLineRange(myFixture.getEditor(), lineHeight * 3 / 2);
      assertEquals(2, i2.intervalStart());
      assertEquals(2, i2.intervalEnd());
    });
  }

  @Test
  public void testInterLineBreakpointHitAreaLarge() {
    myFixture.runTest(() -> {
      InterLineBreakpointConfiguration configuration = createInterLineBreakpointEditor(InterLineBreakpointHitArea.LARGE, 1, 2, 3);

      assertBreakpointAreas(Map.ofEntries(
        Map.entry(17, new BreakpointArea.OnLine(0)),
        Map.entry(18, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(19, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(21, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(22, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(24, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(26, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(27, new BreakpointArea.OnLine(1)),
        Map.entry(33, new BreakpointArea.OnLine(1)),
        Map.entry(40, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(41, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(43, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(44, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(46, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(48, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(49, new BreakpointArea.OnLine(2)),
        Map.entry(55, new BreakpointArea.OnLine(2)),
        Map.entry(62, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(63, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(65, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(66, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(68, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(70, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(71, new BreakpointArea.OnLine(3))
      ));
    });
  }

  @Test
  public void testInterLineBreakpointHitAreaMedium() {
    myFixture.runTest(() -> {
      InterLineBreakpointConfiguration configuration = createInterLineBreakpointEditor(InterLineBreakpointHitArea.MEDIUM, 1, 2, 3);

      assertBreakpointAreas(Map.ofEntries(
        Map.entry(18, new BreakpointArea.OnLine(0)),
        Map.entry(19, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(21, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(22, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(24, new BreakpointArea.InterLine(1, configuration)),
        Map.entry(26, new BreakpointArea.OnLine(1)),
        Map.entry(33, new BreakpointArea.OnLine(1)),
        Map.entry(40, new BreakpointArea.OnLine(1)),
        Map.entry(41, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(43, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(44, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(46, new BreakpointArea.InterLine(2, configuration)),
        Map.entry(48, new BreakpointArea.OnLine(2)),
        Map.entry(55, new BreakpointArea.OnLine(2)),
        Map.entry(62, new BreakpointArea.OnLine(2)),
        Map.entry(63, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(65, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(66, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(68, new BreakpointArea.InterLine(3, configuration)),
        Map.entry(70, new BreakpointArea.OnLine(3))
      ));
    });
  }

  @Test
  public void testInterLineBreakpointHitAreaNone() {
    myFixture.runTest(() -> {
      ExtensionTestUtil.maskExtensions(INTER_LINE_CONFIGURATION_PROVIDERS, List.of(), myFixture.getTestRootDisposable());
      createEditor("line0\nline1\nline2\nline3\n", 2.2f, 22);

      assertBreakpointAreas(Map.ofEntries(
        Map.entry(18, new BreakpointArea.OnLine(0)),
        Map.entry(19, new BreakpointArea.OnLine(0)),
        Map.entry(21, new BreakpointArea.OnLine(0)),
        Map.entry(22, new BreakpointArea.OnLine(1)),
        Map.entry(24, new BreakpointArea.OnLine(1)),
        Map.entry(26, new BreakpointArea.OnLine(1)),
        Map.entry(33, new BreakpointArea.OnLine(1)),
        Map.entry(40, new BreakpointArea.OnLine(1)),
        Map.entry(41, new BreakpointArea.OnLine(1)),
        Map.entry(43, new BreakpointArea.OnLine(1)),
        Map.entry(44, new BreakpointArea.OnLine(2)),
        Map.entry(46, new BreakpointArea.OnLine(2)),
        Map.entry(48, new BreakpointArea.OnLine(2)),
        Map.entry(55, new BreakpointArea.OnLine(2)),
        Map.entry(62, new BreakpointArea.OnLine(2)),
        Map.entry(63, new BreakpointArea.OnLine(2)),
        Map.entry(65, new BreakpointArea.OnLine(2)),
        Map.entry(66, new BreakpointArea.OnLine(3)),
        Map.entry(68, new BreakpointArea.OnLine(3)),
        Map.entry(70, new BreakpointArea.OnLine(3))
      ));
    });
  }

  @SuppressWarnings("deprecation")
  @Test
  public void testDisposeWithEditorDisposesChildWhenEditorDisposableIsDisposed() {
    myFixture.runTest(() -> {
      var editorFactory = EditorFactory.getInstance();
      var editor = (EditorImpl)editorFactory.createEditor(editorFactory.createDocument("text"));
      try {
        // EditorImpl.release() on the EDT can dispose this disposable after a background caller checks isDisposed()
        Disposer.dispose(editor.getDisposable());
        var child = Disposer.newCheckedDisposable();

        EditorUtil.disposeWithEditor(editor, child);

        assertTrue(child.isDisposed());
      }
      finally {
        editorFactory.releaseEditor(editor);
      }
    });
  }

  @Test
  public void testRunBatchFoldingOperationOutsideOfBulkUpdateRunsOperationOnce() {
    myFixture.runTest(() -> {
      createEditor("text");
      var document = myFixture.getEditor().getDocument();
      var runCount = new AtomicInteger();

      DocumentUtil.executeInBulk(document, () -> {
        EditorUtil.runBatchFoldingOperationOutsideOfBulkUpdate(myFixture.getEditor(), runCount::incrementAndGet);
        assertEquals(0, runCount.get());
      });
      assertEquals(1, runCount.get());

      DocumentUtil.executeInBulk(document, () -> {});
      assertEquals(1, runCount.get());
    });
  }

  @Test
  public void testRunBatchFoldingOperationOutsideOfBulkUpdateSkipsEditorReleasedInBulkUpdate() {
    myFixture.runTest(() -> {
      var editorFactory = EditorFactory.getInstance();
      var document = editorFactory.createDocument("text");
      var editor = editorFactory.createEditor(document);
      var runCount = new AtomicInteger();

      DocumentUtil.executeInBulk(document, () -> {
        EditorUtil.runBatchFoldingOperationOutsideOfBulkUpdate(editor, runCount::incrementAndGet);
        editorFactory.releaseEditor(editor);
      });
      assertEquals(0, runCount.get());
    });
  }

  @Test
  public void testRunBatchFoldingOperationOutsideOfBulkUpdateSkipsReleasedEditor() {
    myFixture.runTest(() -> {
      var editorFactory = EditorFactory.getInstance();
      var document = editorFactory.createDocument("text");
      var editor = editorFactory.createEditor(document);
      editorFactory.releaseEditor(editor);
      var runCount = new AtomicInteger();

      DocumentUtil.executeInBulk(document, () -> {
        EditorUtil.runBatchFoldingOperationOutsideOfBulkUpdate(editor, runCount::incrementAndGet);
      });
      assertEquals(0, runCount.get());
    });
  }

  private void createEditor(String text) {
    myFixture.configureFromFileText(myFixture.getTestName() + ".txt", text);
  }

  @SuppressWarnings("SameParameterValue")
  private void createEditor(String text, float lineSpacing, int expectedLineHeight) {
    createEditor(text);
    myFixture.getEditor().getColorsScheme().setLineSpacing(lineSpacing);
    int lineHeight = myFixture.getEditor().getLineHeight();
    assertEquals(expectedLineHeight, lineHeight);
  }

  private void createSmallEditor(String text) {
    myFixture.configureFromFileText(myFixture.getTestName() + ".txt", text);
    EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 4, 2);
  }

  private void assertCaretAt(int line, int column, boolean isVisible) {
    LogicalPosition position = myFixture.getEditor().getCaretModel().getLogicalPosition();
    assertEquals(line, position.line);
    assertEquals(column, position.column);
    Point caretXY = myFixture.getEditor().logicalPositionToXY(position);
    assertEquals(isVisible, myFixture.getEditor().getScrollingModel().getVisibleAreaOnScrollingFinished().contains(caretXY));
  }

  private void assertVisualLineRange(int offset, int lineStartOffset, int lineEndOffset) {
    assertEquals(lineStartOffset, EditorUtil.getNotFoldedLineStartOffset(myFixture.getEditor(), offset));
    assertEquals(lineEndOffset, EditorUtil.getNotFoldedLineEndOffset(myFixture.getEditor(), offset));
  }

  @SuppressWarnings("SameParameterValue")
  private @NotNull InterLineBreakpointConfiguration createInterLineBreakpointEditor(@NotNull InterLineBreakpointHitArea hitArea,
                                                                                   int @NotNull ... lines) {
    var provider = new TestInterLineBreakpointConfigurationProvider(hitArea, lines);
    ExtensionTestUtil.maskExtensions(INTER_LINE_CONFIGURATION_PROVIDERS, List.of(provider), myFixture.getTestRootDisposable());
    createEditor("line0\nline1\nline2\nline3\n", 2.2f, 22);
    PlatformTestUtil.waitWithEventsDispatching("Inter-line configuration wasn't initialized",
                                               () -> InterLineBreakpointConfigurationProvider.findConfigurationForLine(
                                                 myFixture.getEditor(), lines[0]) == provider.getConfiguration(),
                                               10);
    return provider.getConfiguration();
  }

  private void assertBreakpointAreas(@NotNull Map<Integer, BreakpointArea> expectedAreas) {
    Map<Integer, BreakpointArea> actualAreas = new LinkedHashMap<>();
    for (Integer y : expectedAreas.keySet()) {
      Editor editor = myFixture.getEditor();
      actualAreas.put(y, EditorUtil.yToLogicalLineWithInterLineDetection(
        editor, new MouseEvent(editor.getContentComponent(), 0, 0, 0, 0, y, 1, false)));
    }
    assertEquals(expectedAreas, actualAreas);
  }

  private static final class TestInterLineBreakpointConfigurationProvider implements InterLineBreakpointConfigurationProvider {
    private final int @NotNull [] myLines;
    private final @NotNull InterLineBreakpointConfiguration myConfiguration;

    private TestInterLineBreakpointConfigurationProvider(@NotNull InterLineBreakpointHitArea hitArea, int @NotNull [] lines) {
      myLines = lines;
      myConfiguration = new InterLineBreakpointConfiguration(
        AllIcons.Actions.Cancel,
        AllIcons.Actions.Cancel,
        "test",
        new InterLineBreakpointProperties(false),
        null,
        hitArea,
        this::isConfiguredLine
      );
    }

    @Override
    public @NotNull String getUniqueId() {
      return "EditorUtilTest.InterLineBreakpointConfigurationProvider." + myConfiguration.getHitArea();
    }

    @Override
    public @NotNull Flow<InterLineBreakpointConfiguration> getConfiguration(@NotNull Editor editor) {
      return FlowKt.flowOf(myConfiguration);
    }

    private @NotNull InterLineBreakpointConfiguration getConfiguration() {
      return myConfiguration;
    }

    private boolean isConfiguredLine(int line) {
      for (int configuredLine : myLines) {
        if (configuredLine == line) {
          return true;
        }
      }
      return false;
    }
  }
}
