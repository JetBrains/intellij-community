/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.actions;

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public class CutLineBackwardActionTest extends LightPlatformCodeInsightTestCase {

  public void testZeroPosition() {
    String text = 
      "<caret>class Test {\n" +
      "}";
    doTest(text, text);
  }
  
  public void testNonZeroPositionOfFirstLine() {
    doTest(
      "class <caret>Test {\n" +
      "}",
      "<caret>Test {\n" +
      "}"
    );
  }
  
  public void testZeroPositionNonFirstLine() {
    doTest(
      """
        class Test {
        <caret>    // This is comment string
        }""",
      "<caret>    // This is comment string\n" +
      "}"
    );
  }

  public void testNonZeroPositionNonFirstLine() {
    doTest(
      """
        class Test {
            // This is the comment string
            // <caret>int i;
        }""",
      """
        class Test {
        <caret>int i;
        }"""
    );
  }
  
  private void doTest(@NotNull String before, @NotNull String after) {
    configureFromFileText(getTestName(false) + ".txt", before);
    cutLineBackward();
    checkResultByText(after);
  }
}
