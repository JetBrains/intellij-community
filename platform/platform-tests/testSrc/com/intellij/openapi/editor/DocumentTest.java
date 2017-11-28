/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;

public class DocumentTest extends LightPlatformTestCase {
  public void testCorrectlyAddingAndRemovingListeners() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() {
        final Document doc = new DocumentImpl("");
        final StringBuilder b = new StringBuilder();
        doc.addDocumentListener(new DocumentListener() {
          @Override
          public void beforeDocumentChange(DocumentEvent e) {
            b.append("before1 ");
          }

          @Override
          public void documentChanged(DocumentEvent e) {
            b.append("after1 ");
          }
        });

        doc.addDocumentListener(new DocumentListener() {
          @Override
          public void beforeDocumentChange(DocumentEvent event) {
            doc.removeDocumentListener(this);
          }
        });
        doc.addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(DocumentEvent e) {
            doc.removeDocumentListener(this);
          }
        });

        doc.addDocumentListener(new DocumentListener() {
          @Override
          public void beforeDocumentChange(DocumentEvent e) {
            b.append("before2 ");
          }

          @Override
          public void documentChanged(DocumentEvent e) {
            b.append("after2 ");
          }
        });


        doc.setText("foo");
        assertEquals("before2 before1 after1 after2 ", b.toString());
      }
    }.execute().throwException();
  }

  public void testModificationInsideCommandAssertion() {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    assertTrue(!commandProcessor.isUndoTransparentActionInProgress() &&
               commandProcessor.getCurrentCommand() == null);

    final Document doc = new DocumentImpl("xxx");

    mustThrow(() -> doc.insertString(1, "x"));
    mustThrow(() -> doc.deleteString(1, 2));
    mustThrow(() -> doc.replaceString(1, 2, "s"));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.insertString(1, "s"));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.deleteString(1, 2));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.replaceString(1, 2, "xxx"));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> doc.setText("sss"));

    DocumentImpl console = new DocumentImpl("xxxx", true);
    // need no stinking command
    console.insertString(1,"s");
    console.deleteString(1, 2);
    console.replaceString(1, 2, "xxx");
    console.setText("sss");


  }
  
  public void testEmptyDocumentLineCount() {
    WriteCommandAction.runWriteCommandAction(ourProject, () -> {
      DocumentImpl document = new DocumentImpl("");
      assertEquals(0, document.getLineCount());
      document.insertString(0, "a");
      assertEquals(1, document.getLineCount());
      document.deleteString(0, 1);
      assertEquals(0, document.getLineCount());
    });
  }

  private static void mustThrow(Runnable runnable) {
    try {
      ApplicationManager.getApplication().runWriteAction(runnable);
      fail("Must throw IncorrectOperationException");
    }
    catch (IncorrectOperationException ignored) {
    }
  }
}
