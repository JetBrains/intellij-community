// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DocumentTest extends LightPlatformTestCase {

  /** @noinspection deprecation*/
  public void testCorrectlyAddingAndRemovingListeners() {
    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      Document doc = new DocumentImpl("");
      StringBuilder b = new StringBuilder();
      doc.addDocumentListener(
        new DocumentListener() {
          @Override
          public void beforeDocumentChange(@NotNull DocumentEvent e) {
            b.append("before1 ");
          }
          @Override
          public void documentChanged(@NotNull DocumentEvent e) {
            b.append("after1 ");
          }
        }
      );
      doc.addDocumentListener(
        new DocumentListener() {
          @Override
          public void beforeDocumentChange(@NotNull DocumentEvent event) {
            doc.removeDocumentListener(this);
          }
        }
      );
      doc.addDocumentListener(
        new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent e) {
            doc.removeDocumentListener(this);
          }
        }
      );
      doc.addDocumentListener(
        new DocumentListener() {
          @Override
          public void beforeDocumentChange(@NotNull DocumentEvent e) {
            b.append("before2 ");
          }
          @Override
          public void documentChanged(@NotNull DocumentEvent e) {
            b.append("after2 ");
          }
        }
      );
      doc.setText("foo");
      assertEquals("before2 before1 after1 after2 ", b.toString());
    });
  }

  public void testModificationInsideCommandAssertion1() {
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    assertTrue(
      !commandProcessor.isUndoTransparentActionInProgress() &&
      !commandProcessor.isCommandInProgress()
    );
    Document doc = new DocumentImpl("xxx");
    mustThrow(() -> doc.insertString(1, "x"));
    mustThrow(() -> doc.deleteString(1, 2));
    mustThrow(() -> doc.replaceString(1, 2, "s"));
  }

  public void testModificationInsideCommandAssertion2() {
    Document doc = new DocumentImpl("xxx");
    runWriteCommandAction(() -> doc.insertString(1, "s"));
    runWriteCommandAction(() -> doc.deleteString(1, 2));
    runWriteCommandAction(() -> doc.replaceString(1, 2, "xxx"));
    runWriteCommandAction(() -> doc.setText("sss"));
  }

  public void testModificationInsideCommandAssertion3() {
    DocumentImpl console = new DocumentImpl("xxxx", true);
    // need no stinking command
    console.insertString(1,"s");
    console.deleteString(1, 2);
    console.replaceString(1, 2, "xxx");
    console.setText("sss");
  }

  public void testEmptyDocumentLineCount() {
    runWriteCommandAction(() -> {
      DocumentImpl document = new DocumentImpl("");
      assertEquals(0, document.getLineCount());
      document.insertString(0, "a");
      assertEquals(1, document.getLineCount());
      document.deleteString(0, 1);
      assertEquals(0, document.getLineCount());
    });
  }

  public void testClearLineFlagsInBeforeDocumentChange() {
    DocumentImpl document = new DocumentImpl("one\ntwo");
    runWriteCommandAction(() -> document.insertString(0, "x"));
    assertTrue(document.isLineModified(0));
    assertFalse(document.isLineModified(1));
    document.addDocumentListener(
      new DocumentListener() {
        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent event) {
          document.clearLineModificationFlags();
        }
      },
      getTestRootDisposable()
    );
    runWriteCommandAction(() -> document.insertString(document.getLineStartOffset(1), "y"));
    assertFalse(document.isLineModified(0));
    assertTrue(document.isLineModified(1));
  }

  public void testSetTextPreservesModificationStampSetInDocumentChanged() {
    DocumentImpl document = new DocumentImpl("one\ntwo");
    long expectedModStamp = 42;
    document.addDocumentListener(
      new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          document.setModificationStamp(expectedModStamp);
        }
      },
      getTestRootDisposable()
    );
    runWriteCommandAction(() -> document.setText("three\nfour"));
    assertEquals(expectedModStamp, document.getModificationStamp());
  }

  public void testInsertStringPreservesIncrementedModificationSequenceSetInBeforeDocumentChange() {
    DocumentImpl document = new DocumentImpl("one\ntwo");
    int initialModSequence = document.getModificationSequence();
    document.addDocumentListener(
      new DocumentListener() {
        @Override
        public void beforeDocumentChange(@NotNull DocumentEvent event) {
          document.setModificationStamp(LocalTimeCounter.currentTime(), true);
        }
      },
      getTestRootDisposable()
    );
    runWriteCommandAction(() -> document.insertString(0, "x"));
    assertEquals(initialModSequence + 2, document.getModificationSequence());
  }

  public void testSetTextClearsLineFlags() {
    DocumentImpl document = new DocumentImpl("one\ntwo");
    runWriteCommandAction(() -> document.insertString(document.getLineStartOffset(0), "x"));
    runWriteCommandAction(() -> document.insertString(document.getLineStartOffset(1), "x"));
    assertTrue(document.isLineModified(0));
    assertTrue(document.isLineModified(1));
    runWriteCommandAction(() -> document.setText("xone\nxtwo"));
    assertFalse(document.isLineModified(0));
    assertFalse(document.isLineModified(1));
  }

  public void testWholeTextReplacementReusesImmutableSequenceSharingAffix() {
    DocumentImpl document = new DocumentImpl("abcOLDxyz", true);

    ImmutableCharSequence viaSetText = CharArrayUtil.createImmutableCharSequence("abcNEWxyz");
    document.setText(viaSetText);
    assertEquals("abcNEWxyz", document.getText());
    assertSame(viaSetText, document.getImmutableCharSequence());

    ImmutableCharSequence viaReplaceText = CharArrayUtil.createImmutableCharSequence("abcZZZxyz");
    document.replaceText(viaReplaceText, LocalTimeCounter.currentTime());
    assertEquals("abcZZZxyz", document.getText());
    assertSame(viaReplaceText, document.getImmutableCharSequence());
  }

  public void testClearLineFlagsOnEmptyDocumentWithInitializedLineSet() {
    DocumentImpl document = new DocumentImpl("");
    runWriteCommandAction(() -> document.insertString(0, "x"));
    runWriteCommandAction(() -> document.deleteString(0, 1));
    assertEquals(0, document.getLineCount());
    document.clearLineModificationFlags();
  }

  public void testCharsSequenceIsLiveAfterDocumentMutations() {
    Document document = new DocumentImpl("abc", true);
    CharSequence live = document.getCharsSequence();
    CharSequence immutableBeforeChange = document.getImmutableCharSequence();

    document.insertString(1, "X");

    assertSame(live, document.getCharsSequence());
    assertEquals("aXbc", live.toString());
    assertEquals(4, live.length());
    assertEquals('X', live.charAt(1));
    assertEquals(document.getText(), live.toString());
    assertEquals("abc", immutableBeforeChange.toString());

    document.replaceString(0, document.getTextLength(), "xy\nz");

    assertSame(live, document.getCharsSequence());
    assertEquals("xy\nz", live.toString());
    assertEquals(4, live.length());
    assertEquals('\n', live.charAt(2));
    assertEquals("z", live.subSequence(3, 4).toString());
    assertEquals(document.getText(), live.toString());
  }

  public void testCharsSequenceReflectsSetTextAndDeleteString() {
    Document document = new DocumentImpl("one\ntwo", true);
    CharSequence live = document.getCharsSequence();

    document.setText("three");

    assertSame(live, document.getCharsSequence());
    assertEquals("three", live.toString());
    assertEquals(document.getTextLength(), live.length());

    document.deleteString(1, 3);

    assertSame(live, document.getCharsSequence());
    assertEquals("tee", live.toString());
    assertEquals(document.getText(), live.toString());
  }

  public void testBulkUpdateStartingSwitchesModeAndNotifiesAllListenersWhenOneListenerThrowsPce() {
    DocumentImpl document = new DocumentImpl("abc", true);
    List<String> notifications = new ArrayList<>();
    DocumentListener listenerAfterFailure = new DocumentListener() {
      @Override
      public void bulkUpdateStarting(@NotNull Document document) {
        notifications.add("after failure");
      }
    };
    DocumentListener failingListener = new DocumentListener() {
      @Override
      public void bulkUpdateStarting(@NotNull Document document) {
        notifications.add("failure");
        throw new ProcessCanceledException();
      }
    };
    document.addDocumentListener(listenerAfterFailure);
    document.addDocumentListener(failingListener);

    try {
      document.setInBulkUpdate(true);
      fail("Must throw ProcessCanceledException");
    }
    catch (ProcessCanceledException ignored) {
      assertTrue(document.isInBulkUpdate());
    }
    document.setInBulkUpdate(false);

    assertEquals(List.of("failure", "after failure"), notifications);
    assertFalse(document.isInBulkUpdate());
  }

  private void runWriteCommandAction(Runnable action) {
    WriteCommandAction.runWriteCommandAction(getProject(), action);
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
