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
public class CutLineEndActionsTest extends LightPlatformCodeInsightTestCase {
  
  public void testNonEmptyLineEnd() {
    doTest(
      """
        class Test {
            // This is a <caret>comment string
        }""",
      """
        class Test {
            // This is a <caret>
        }"""
    );
  }

  public void testEmptyLineEnd() {
    doTest(
      """
        class Test {
            <caret>  \s
            // some comment
        }""",
      """
        class Test {
            <caret>    // some comment
        }"""
    );
  }
  
  public void testAtLineEnd() {
    doTest(
      """
        class Test {
            // This is a comment string1<caret>
            // This is a comment string2
        }""",
      """
        class Test {
            // This is a comment string1<caret>    // This is a comment string2
        }"""
    );
  }
  
  public void testAtDocumentEnd() {
    String text = 
      "class Test{\n" +
      "}<caret>";
    doTest(text, text);
  }
  
  public void testEmptyLastLineEnd() {
    doTest(
      "class Test {\n" +
      "}<caret>         ",
      "class Test {\n" +
      "}<caret>"
    );
  }
  
  private void doTest(@NotNull String before, @NotNull String after) {
    configureFromFileText(getTestName(false) + ".txt", before);
    cutToLineEnd();
    checkResultByText(after);
  }
}
