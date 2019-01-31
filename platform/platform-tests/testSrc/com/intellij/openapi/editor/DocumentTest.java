// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class DocumentTest extends LightPlatformTestCase {
  public void testCorrectlyAddingAndRemovingListeners() {
    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      final Document doc = new DocumentImpl("");
      final StringBuilder b = new StringBuilder();
      doc.addDocumentListener(new DocumentListener() {
        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent e) {
          b.append("before1 ");
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent e) {
          b.append("after1 ");
        }
      });

      doc.addDocumentListener(new DocumentListener() {
        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent event) {
          doc.removeDocumentListener(this);
        }
      });
      doc.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent e) {
          doc.removeDocumentListener(this);
        }
      });

      doc.addDocumentListener(new DocumentListener() {
        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent e) {
          b.append("before2 ");
        }

        @Override
        public void documentChanged(@NotNull DocumentEvent e) {
          b.append("after2 ");
        }
      });


      doc.setText("foo");
      assertEquals("before2 before1 after1 after2 ", b.toString());
    });
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
