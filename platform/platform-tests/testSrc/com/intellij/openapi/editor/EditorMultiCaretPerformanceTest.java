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

import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;

public class EditorMultiCaretPerformanceTest extends AbstractEditorTest {
  public void testTyping() {
    int caretCount = 1000;
    int charactersToType = 100;
    String initialText = StringUtil.repeat("<caret>\n", caretCount);
    initText(initialText);
    PlatformTestUtil.startPerformanceTest("Typing with large number of carets", 100_000, () -> {
      for (int i = 0; i < charactersToType; i++) {
        type('a');
      }
    }).attempts(1).assertTiming();
    checkResultByText(StringUtil.repeat(StringUtil.repeat("a", charactersToType) + "<caret>\n", caretCount));
  }

  public void testTypingWithManyFoldRegions() {
    int caretCount = 1000;
    int charactersToType = 100;
    String initialText = StringUtil.repeat(" <caret> \n", caretCount);
    initText(initialText);
    getEditor().getFoldingModel().runBatchFoldingOperation(() -> {
      for (int i = 0; i < caretCount; i++) {
        addFoldRegion(getEditor().getDocument().getLineStartOffset(i), getEditor().getDocument().getLineEndOffset(i), "...");
      }
    });
    PlatformTestUtil.startPerformanceTest("Typing with large number of carets with a lot of fold regions", 150_000, () -> {
      for (int i = 0; i < charactersToType; i++) {
        type('a');
      }
    }).attempts(1).assertTiming();
    checkResultByText(StringUtil.repeat(' ' + StringUtil.repeat("a", charactersToType) + "<caret> \n", caretCount));
  }

  public void testTypingInXml() {
    int caretCount = 1000;
    int charactersToType = 100;
    String initialText = "<root>\n" + StringUtil.repeat("  <node><caret></node>\n", caretCount) + "</root>";
    init(initialText, StdFileTypes.XML);
    PlatformTestUtil.startPerformanceTest("Typing in XML with large number of carets", 100_000, () -> {
      for (int i = 0; i < charactersToType; i++) {
        type('a');
      }
    }).attempts(1).assertTiming();
    checkResultByText("<root>\n" + StringUtil.repeat("  <node>" + StringUtil.repeat("a", charactersToType) + "<caret></node>\n", caretCount)
                      + "</root>");
  }
}
