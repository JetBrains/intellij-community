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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 4/19/11 4:21 PM
 */
public class KillToWordStartActionTest extends LightPlatformCodeInsightTestCase {

  public void testAfterWordEnd() {
    doTest(
      "this is a test string    <caret>",
      "this is a test <caret>"
    );
  }

  public void testAtWordEnd() {
    doTest(
      "this is a test string<caret>",
      "this is a test <caret>"
    );
  }

  public void testInTheMiddleOfTheWord() {
    doTest(
      "this is a test str<caret>ing",
      "this is a test <caret>ing"
    );
  }

  public void testInTheWordStart() {
    doTest(
      "this is a test <caret>string",
      "this is a <caret>string"
    );
  }
  
  public void testInWhiteSpaceAtLineStart() {
    doTest(
      "this is the first string  \n" +
      " <caret>  this is the second string",
      "this is the first <caret>  this is the second string"
    );
  }

  public void testAtLineStart() {
    doTest(
      "this is the first string  \n" +
      "<caret>  this is the second string",
      "this is the first <caret>  this is the second string"
    );
  }

  public void testInWhiteSpaceAtDocumentStart() {
    doTest(
      "    <caret>  this is the first string",
      "<caret>  this is the first string"
    );
  }

  public void testAtDocumentStart() {
    doTest(
      "<caret>  this is the first string",
      "<caret>  this is the first string"
    );
  }

  public void testEscapeChars() {
    configureFromFileText(getTestName(false) + ".java", "class Foo { String s = \"a\\nb<caret>\"; }");
    killToWordStart();
    checkResultByText("class Foo { String s = \"a\\n<caret>\"; }");
  }
  
  public void testNearDocumentStartInCamelHumpsMode() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    boolean savedValue = editorSettings.isCamelWords();
    editorSettings.setCamelWords(true);
    try {
      doTest("abc<caret>", "<caret>");
    }
    finally {
      editorSettings.setCamelWords(savedValue);
    }
  }

  private void doTest(@NotNull String before, @NotNull String after) {
    configureFromFileText(getTestName(false) + ".txt", before);
    killToWordStart();
    checkResultByText(after);
  }

}
