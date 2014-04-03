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

import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class DeleteToLineStartAndEndActionsTest extends AbstractEditorTest {
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

  public void testMoveCaretFromVirtualSpaceToRealOffset() throws IOException {
    boolean virtualSpacesBefore = EditorSettingsExternalizable.getInstance().isVirtualSpace();
    EditorSettingsExternalizable.getInstance().setVirtualSpace(true);

    try {
      doTestDeleteToStart("\n a a \n b b \n", new VisualPosition(1, 10), "\n<caret>\n b b \n", new VisualPosition(1, 0));
      doTestDeleteToStart("\n a a \n b b \n", new VisualPosition(2, 10), "\n a a \n<caret>\n", new VisualPosition(2, 0));
      doTestDeleteToStart("\n a a \n b b \n", new VisualPosition(3, 10), "\n a a \n b b \n<caret>", new VisualPosition(3, 0));

      doTestDeleteToStart("\n a a \n b b \n", new VisualPosition(20, 10), "\n a a \n b b \n<caret>", new VisualPosition(3, 0));

      doTestDeleteToEnd("\n a a \n b b \n", new VisualPosition(1, 10), "\n a a <caret> b b \n", new VisualPosition(1, 5));

      // keep old behavior - not sure if it was intentional or not
      doTestDeleteToEnd("\n a a \n b b \n", new VisualPosition(3, 10), "\n a a \n b b \n<caret>", new VisualPosition(3, 10));
    }
    finally {
      EditorSettingsExternalizable.getInstance().setVirtualSpace(virtualSpacesBefore);
    }
  }

  public void testDeleteSelectionFirst() throws IOException {
    doTestDeleteToStart("aaa <selection>bbb \n ccc</selection><caret> ddd", "aaa <caret> ddd");
    doTestDeleteToEnd("aaa <selection>bbb \n ccc</selection><caret> ddd", "aaa <caret> ddd");
  }

  private void doTestDeleteToStart(@NotNull String before, @NotNull String after) throws IOException {
    doTestDelete(true, before, null, after, null);
  }

  private void doTestDeleteToStart(@NotNull String before, @Nullable VisualPosition positionBefore,
                                   @NotNull String after, @Nullable VisualPosition positionAfter) throws IOException {
    doTestDelete(true, before, positionBefore, after, positionAfter);
  }

  private void doTestDeleteToEnd(@NotNull String before, @NotNull String after) throws IOException {
    doTestDelete(false, before, null, after, null);
  }

  private void doTestDeleteToEnd(@NotNull String before, @Nullable VisualPosition positionBefore,
                                 @NotNull String after, @Nullable VisualPosition positionAfter) throws IOException {
    doTestDelete(false, before, positionBefore, after, positionAfter);
  }

  private void doTestDelete(boolean toStart,
                            @NotNull String before, @Nullable VisualPosition positionBefore,
                            @NotNull String after, @Nullable VisualPosition positionAfter) throws IOException {
    configureFromFileText(getTestName(false) + ".txt", before);
    if (positionBefore != null) mouse().clickAt(positionBefore.line, positionBefore.column);

    if (toStart) {
      deleteToLineStart();
    }
    else {
      deleteToLineEnd();
    }

    checkResultByText(after);

    if (positionAfter != null) {
      assertEquals(positionAfter, getEditor().getCaretModel().getVisualPosition());
    }
  }
}
