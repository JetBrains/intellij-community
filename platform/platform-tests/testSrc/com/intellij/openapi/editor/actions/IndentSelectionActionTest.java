/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public class IndentSelectionActionTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testWithoutSelection() throws Exception {
    verifyAction("some text",
                 null);
  }

  public void testWithInlineSelection() throws Exception {
    verifyAction("some <selection>text</selection>",
                 null);
  }

  public void testWithLineSelection() throws Exception {
    verifyAction("<selection>some text</selection>",
                 "    <selection>some text</selection>");
  }

  public void testWithLineSelectionExcludingSpaces() throws Exception {
    verifyAction(" <selection>some text</selection>",
                 "     <selection>some text</selection>");
  }

  private void verifyAction(String input, String expectedResult) {
    myFixture.configureByText(getTestName(false) + ".txt", input);
    IndentSelectionAction action = new IndentSelectionAction();
    assertEquals(expectedResult != null, myFixture.testAction(action).isEnabled());
    if (expectedResult != null) {
      myFixture.checkResult(expectedResult);
    }
  }
}
