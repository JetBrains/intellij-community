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

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ide.KillRingTransferable;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.IOException;

/**
 * @author Denis Zhdanov
 * @since 04/19/2011
 */
public class KillToWordEndActionTest extends LightPlatformCodeInsightTestCase {

  public void testAtWordStart() throws IOException {
    doTest(
      "this is a <caret>string",
      "this is a <caret>"
    );
  }
  
  public void testInTheMiddle() throws IOException {
    doTest(
      "th<caret>is is a string",
      "th<caret> is a string"
    );
  }

  public void testAtWordEnd() throws IOException {
    doTest(
      "this is<caret> a string",
      "this is<caret> string"
    );
  }

  public void testAtWhiteSpaceBetweenWords() throws IOException {
    doTest(
      "this  <caret>     is  a string",
      "this  <caret>  a string"
    );
  }

  public void testAfterLastWordOnTheLineEnd() throws IOException {
    doTest(
      "this is the first string<caret>     \n" +
      "this is the second string",
      "this is the first string<caret>this is the second string"
    );
  }

  public void testAfterLastWord() throws IOException {
    doTest(
      "this is a string<caret>",
      "this is a string<caret>"
    );
  }

  public void testAfterLastWordBeforeWhiteSpace() throws IOException {
    doTest(
      "this is a string<caret>  ",
      "this is a string<caret>"
    );
  }
  
  public void testAtWhiteSpaceAtLineEnd() throws IOException {
    doTest(
      "this is the first string  <caret>     \n" +
      "this is the second string",
      "this is the first string  <caret>this is the second string"
    );
  }
  
  private void doTest(@NotNull String before, @NotNull String after) throws IOException {
    configureFromFileText(getTestName(false) + ".txt", before);
    killToWordEnd();
    checkResultByText(after);
  }

  public void testSubsequentKillsInterleavedByCaretMove() throws Exception {
    String text = "<caret>first second third";
    configureFromFileText(getTestName(false) + ".txt", text);
    killToWordEnd();
    checkResultByText(" second third");
    
    getEditor().getCaretModel().moveCaretRelatively(1, 0, false, false, false);
    getEditor().getCaretModel().moveCaretRelatively(-1, 0, false, false, false);
    killToWordEnd();
    Transferable contents = CopyPasteManager.getInstance().getContents();
    assertTrue(contents instanceof KillRingTransferable);
    Object string = contents.getTransferData(DataFlavor.stringFlavor);
    assertEquals(" second", string);
  }
}
