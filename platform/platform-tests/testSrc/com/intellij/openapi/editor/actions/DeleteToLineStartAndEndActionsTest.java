/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.io.IOException;

public class DeleteToLineStartAndEndActionsTest extends LightPlatformCodeInsightTestCase {
  public void testEmpty() throws IOException {
    doTestDeleteToStart("<caret>", "<caret>");
    doTestDeleteToEnd("<caret>", "<caret>");
  }
  public void testEmptyLine() throws IOException {
    doTestDeleteToStart("\n<caret>\n\n", "\n<caret>\n\n");
    doTestDeleteToEnd("\n<caret>\n\n", "\n<caret>\n");
  }

  public void testEndOfNonEmptyLine() throws IOException {
    doTestDeleteToStart("\n a a a <caret>\n\n", "\n<caret>\n\n");
    doTestDeleteToEnd("\n a a a <caret>\n\n", "\n a a a <caret>\n");

    doTestDeleteToStart(" a a a <caret>", "<caret>");
    doTestDeleteToEnd(" a a a <caret>", " a a a <caret>");
  }

  public void testBeginningOfNonEmptyLine() throws IOException {
    doTestDeleteToStart("\n<caret> a a a \n\n", "\n<caret> a a a \n\n");
    doTestDeleteToEnd("\n<caret> a a a \n\n", "\n<caret>\n\n");

    doTestDeleteToStart("<caret> a a a ", "<caret> a a a ");
    doTestDeleteToEnd("<caret> a a a ", "<caret>");
  }

  public void testMiddleOfNonEmptyLine() throws IOException {
    doTestDeleteToStart("\n a a <caret> b b \n\n", "\n<caret> b b \n\n");
    doTestDeleteToEnd("\n a a <caret> b b \n\n", "\n a a <caret>\n\n");

    doTestDeleteToStart(" a a <caret> b b ", "<caret> b b ");
    doTestDeleteToEnd(" a a <caret> b b ", " a a <caret>");
  }

  public void testDeleteSelectionFirst() throws IOException {
    doTestDeleteToStart("aaa <selection>bbb \n ccc</selection><caret> ddd", "aaa <caret> ddd");
    doTestDeleteToEnd("aaa <selection>bbb \n ccc</selection><caret> ddd", "aaa <caret> ddd");
  }

  private void doTestDeleteToStart(@NotNull String before, @NotNull String after) throws IOException {
    configureFromFileText(getTestName(false) + ".txt", before);
    deleteToLineStart();
    checkResultByText(after);
  }

  private void doTestDeleteToEnd(@NotNull String before, @NotNull String after) throws IOException {
    configureFromFileText(getTestName(false) + ".txt", before);
    deleteToLineEnd();
    checkResultByText(after);
  }
}
