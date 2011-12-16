package com.intellij.openapi.editor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.testFramework.LightPlatformTestCase;

public class DocumentTest extends LightPlatformTestCase {
  public void testCorrectlyAddingAndRemovingListeners() throws Exception {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final Document doc = new DocumentImpl("");
        final StringBuilder b = new StringBuilder();
        doc.addDocumentListener(new DocumentAdapter() {
          @Override
          public void beforeDocumentChange(DocumentEvent e) {
            b.append("before1 ");
          }

          @Override
          public void documentChanged(DocumentEvent e) {
            b.append("after1 ");
          }
        });

        doc.addDocumentListener(new DocumentAdapter() {
          @Override
          public void beforeDocumentChange(DocumentEvent event) {
            doc.removeDocumentListener(this);
          }
        });
        doc.addDocumentListener(new DocumentAdapter() {
          @Override
          public void documentChanged(DocumentEvent e) {
            doc.removeDocumentListener(this);
          }
        });

        doc.addDocumentListener(new DocumentAdapter() {
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
}
