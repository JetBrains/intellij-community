/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static com.intellij.openapi.editor.impl.EditorModelFixture.LOREM_IPSUM;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_CHAR_WIDTH;
import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@RunInEdt
@PerformanceUnitTest
@StressTestApplication
@TestApplication
public class EditorPaintingPerformanceTest {
  private static final Logger LOG = Logger.getInstance(EditorPaintingPerformanceTest.class);

  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  private static final int EDITOR_WIDTH_PX = 1000;

  @Test
  public void testScrollingThroughLongTextFile() {
    myFixture.initText(StringUtil.repeat(LOREM_IPSUM + '\n', 15000));

    doTestScrollingPerformance("scrolling through text file with many lines");
    // attempt.min.ms varies ~5% (from experiments)
  }

  @Test
  public void testScrollingThroughLongSoftWrappedLine() {
    myFixture.initText(StringUtil.repeat(LOREM_IPSUM + ' ', 15000));
    EditorTestUtil.configureSoftWraps(myFixture.getEditor(), EDITOR_WIDTH_PX, TEST_CHAR_WIDTH);
    
    doTestScrollingPerformance("scrolling through long soft wrapped line");
    // attempt.min.ms varies ~4% (from experiments)
  }

  private void doTestScrollingPerformance(String message) {
    EditorImpl editor = (EditorImpl)myFixture.getEditor();
    int editorHeight = editor.getPreferredHeight();
    int[] result = {0};
    Benchmark.newBenchmark(message, () -> {
      for (int y = 0; y < editorHeight; y += 1000) {
        Rectangle clip = new Rectangle(0, y, EDITOR_WIDTH_PX, 1000);
        NullGraphics2D g = new NullGraphics2D(clip);
        editor.getContentComponent().paintComponent(g);
        result[0] += g.getResult();
      }
    }).warmupIterations(50)
      .attempts(100)
      .start();
    LOG.debug(String.valueOf(result[0]));
  }
}
