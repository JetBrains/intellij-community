// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@RunInEdt
@PerformanceUnitTest
@StressTestApplication
@TestApplication
public class EditorPerformanceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private static final int LARGE_FILE_LINE_COUNT = 100_000;

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testEditingWithALotOfHighlighters() {
    myFixture.initText(StringUtil.repeat("\nLorem ipsum", 10_000));
    TextAttributes attributes = new TextAttributes(Color.red, null, null, null, Font.PLAIN);
    for (int offset = 0; offset < myFixture.getEditor().getDocument().getTextLength(); offset++) {
      myFixture.getEditor().getMarkupModel().addRangeHighlighter(offset, offset + 1, 0, attributes, HighlighterTargetArea.EXACT_RANGE);
    }
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    Benchmark.newBenchmark("Editing with a lot of highlighters", () -> {
      for (int i = 0; i < 50; i++) {
        myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER);
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue(); // let gutter update its width
      }
    }).warmupIterations(50)
      .attempts(100)
      .start();
    // attempt.min.ms varies ~15% (from experiments)
  }

  /**
   * Each edit replaces the text around a line break in the middle of a large file and keeps the line count.
   * The editor must recalculate only the widths of the two changed lines, not of all lines.
   */
  @Test
  public void testEditOfTwoLinesInLargeFile() {
    Document document = initLargeFile();
    int offset = document.getLineEndOffset(LARGE_FILE_LINE_COUNT / 2) - 1;
    String[] replacements = {"x\ny", "y\nx"};
    Benchmark.newBenchmark("Edit of two lines in a large file", () -> {
      for (int i = 0; i < 100; i++) {
        String replacement = replacements[i % 2];
        myFixture.runWriteCommand(() -> document.replaceString(offset, offset + 3, replacement));
        myFixture.getEditor().getContentComponent().getPreferredSize();
      }
    }).warmupIterations(20)
      .attempts(50)
      .start();
  }

  /**
   * Each edit adds or removes a line break in the middle of a large file.
   * The editor must recalculate only the widths of the changed lines, not of all lines.
   */
  @Test
  public void testNewAndRemovedLineInLargeFile() {
    Document document = initLargeFile();
    int offset = document.getLineEndOffset(LARGE_FILE_LINE_COUNT / 2) - 1;
    Benchmark.newBenchmark("New and removed line in a large file", () -> {
      for (int i = 0; i < 50; i++) {
        myFixture.runWriteCommand(() -> document.insertString(offset, "\n"));
        myFixture.getEditor().getContentComponent().getPreferredSize();
        myFixture.runWriteCommand(() -> document.deleteString(offset, offset + 1));
        myFixture.getEditor().getContentComponent().getPreferredSize();
      }
    }).warmupIterations(20)
      .attempts(50)
      .start();
  }

  /**
   * Makes a file of {@link #LARGE_FILE_LINE_COUNT} lines, with the longest line at the start, and calculates the editor width once.
   */
  private Document initLargeFile() {
    myFixture.initText("The longest line of the file\n" + StringUtil.repeat("line x\n", LARGE_FILE_LINE_COUNT));
    myFixture.getEditor().getCaretModel().moveToOffset(0);
    myFixture.getEditor().getContentComponent().getPreferredSize();
    return myFixture.getEditor().getDocument();
  }
}
