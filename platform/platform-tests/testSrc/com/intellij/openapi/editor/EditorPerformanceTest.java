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
}
