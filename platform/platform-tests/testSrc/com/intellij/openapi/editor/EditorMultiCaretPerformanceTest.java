/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.junit5.RunInEdt;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@RunInEdt
@PerformanceUnitTest
@StressTestApplication
@TestApplication
public class EditorMultiCaretPerformanceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);
  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));
  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  void testTyping() {
    int caretCount = 1000;
    int charactersToType = 100;
    String initialText = StringUtil.repeat("<caret>\n", caretCount);
    myFixture.initText(initialText);
    Benchmark.newBenchmark("Typing with large number of carets", () -> {
      for (int i = 0; i < charactersToType; i++) {
        myFixture.type('a');
      }
    }).warmupIterations(0).attempts(1).start();
    myFixture.checkResultByText(StringUtil.repeat(StringUtil.repeat("a", charactersToType) + "<caret>\n", caretCount));
  }

  @Test
  void testTypingWithManyFoldRegions() {
    int caretCount = 1000;
    int charactersToType = 100;
    String initialText = StringUtil.repeat(" <caret> \n", caretCount);
    myFixture.initText(initialText);
    myFixture.getEditor().getFoldingModel().runBatchFoldingOperation(() -> {
      for (int i = 0; i < caretCount; i++) {
        Document document = myFixture.getEditor().getDocument();
        myFixture.addFoldRegion(document.getLineStartOffset(i), document.getLineEndOffset(i), "...");
      }
    });
    Benchmark.newBenchmark("Typing with large number of carets with a lot of fold regions", () -> {
      for (int i = 0; i < charactersToType; i++) {
        myFixture.type('a');
      }
    }).warmupIterations(0).attempts(1).start();
    myFixture.checkResultByText(StringUtil.repeat(' ' + StringUtil.repeat("a", charactersToType) + "<caret> \n", caretCount));
  }

  @Test
  void testTypingInXml() {
    int caretCount = 1000;
    int charactersToType = 100;
    String initialText = "<root>\n" + StringUtil.repeat("  <node><caret></node>\n", caretCount) + "</root>";
    myFixture.init(initialText, StdFileTypes.XML);
    Benchmark.newBenchmark("Typing in XML with large number of carets", () -> {
      for (int i = 0; i < charactersToType; i++) {
        myFixture.type('a');
      }
    }).warmupIterations(0).attempts(1).start();
    myFixture.checkResultByText("<root>\n" + StringUtil.repeat("  <node>" + StringUtil.repeat("a", charactersToType) + "<caret></node>\n", caretCount)
                      + "</root>");
  }
}
