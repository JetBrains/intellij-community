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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;

public class EditorMultiCaretPerformanceTest extends AbstractEditorTest {
  public void testTyping() {
    int caretCount = 1000;
    int charactersToType = 100;
    String initialText = StringUtil.repeat("<caret>\n", caretCount);
    initText(initialText);
    PlatformTestUtil.startPerformanceTest("Typing with large number of carets", 85000, () -> {
      for (int i = 0; i < charactersToType; i++) {
        type('a');
      }
    }).attempts(1).assertTiming();
    checkResultByText(StringUtil.repeat(StringUtil.repeat("a", charactersToType) + "<caret>\n", caretCount));
  }
}
