// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatform4TestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies that disposing the disposable passed to {@link EditorTextField#setDisposedWith(Disposable)} releases the
 * editor and uninstalls the document listener, regardless of whether the editor was initialized before or after
 * {@code setDisposedWith} was called.
 * <p>
 * The editor is only released because the internal {@code myDisposable} is registered as a child of the manual
 * disposable, so these tests also guard that registration.
 */
public class EditorTextFieldDisposalTest extends LightPlatform4TestCase {
  @Override
  protected boolean runInDispatchThread() {
    return true;
  }

  /** The editor does not exist yet when {@code setDisposedWith} is called; it is initialized afterwards. */
  @Test
  public void testDisposalWhenEditorInitializedAfterSetDisposedWith() {
    EditorTextField field = new EditorTextField("initial text");
    Disposable manualDisposable = Disposer.newDisposable("test manual disposable");

    field.setDisposedWith(manualDisposable);
    CountingDocumentListener listener = addCountingListener(field);

    EditorEx editor = field.getEditor(true); // initializes lazily because the manual disposable is set
    assertNotNull("the editor must be initialized via getEditor(true) in manual mode", editor);

    assertDisposedProperly(field, manualDisposable, listener, editor);
  }

  /** The editor already exists (it was initialized via the Swing hierarchy) when {@code setDisposedWith} is called. */
  @Test
  public void testDisposalWhenEditorInitializedBeforeSetDisposedWith() {
    EditorTextField field = new EditorTextField("initial text");
    Disposable manualDisposable = Disposer.newDisposable("test manual disposable");

    field.addNotify(); // marks the field as "in hierarchy" so the editor can be initialized below
    try {
      CountingDocumentListener listener = addCountingListener(field);
      EditorEx editor = field.getEditor(true); // initializes while still in automatic mode
      assertNotNull("the editor must be initialized once the field is in the hierarchy", editor);

      field.setDisposedWith(manualDisposable); // the editor (and myDisposable) already exists at this point

      assertDisposedProperly(field, manualDisposable, listener, editor);
    }
    finally {
      field.removeNotify();
    }
  }

  /** In manual mode the editor lifecycle is bound to the manual disposable, not to add/remove from the hierarchy. */
  @Test
  public void testManualModeKeepsEditorAcrossRemoveAndAddNotify() {
    EditorTextField field = new EditorTextField("initial text");
    Disposable manualDisposable = Disposer.newDisposable("test manual disposable");

    field.setDisposedWith(manualDisposable);
    EditorEx editor = field.getEditor(true);
    assertNotNull(editor);

    field.addNotify();
    field.removeNotify();

    assertSame("removeNotify must not release the editor in manual mode", editor, field.getEditor(false));
    assertFalse("the editor must not be disposed by removeNotify in manual mode", editor.isDisposed());

    Disposer.dispose(manualDisposable);
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    assertNull("disposing the manual disposable must release the editor", field.getEditor(false));
    assertTrue("disposing the manual disposable must dispose the editor", editor.isDisposed());
  }

  private void assertDisposedProperly(@NotNull EditorTextField field,
                                      @NotNull Disposable manualDisposable,
                                      @NotNull CountingDocumentListener listener,
                                      @NotNull EditorEx editor) {
    Document document = field.getDocument();

    // The document listener must be installed and notified while the field is alive.
    editDocument(document, "while alive");
    assertEquals("the document listener must be notified before disposal", 1, listener.count.get());

    Disposer.dispose(manualDisposable);

    // releaseEditorLater() nulls the editor synchronously, but the actual release happens via invokeLater().
    assertNull("disposing the manual disposable must release the editor", field.getEditor(false));
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    assertTrue("disposing the manual disposable must dispose the editor", editor.isDisposed());

    // The document listener must have been uninstalled, so further edits are not delivered.
    editDocument(document, "after disposal");
    assertEquals("the document listener must be uninstalled after disposal", 1, listener.count.get());
  }

  private static @NotNull CountingDocumentListener addCountingListener(@NotNull EditorTextField field) {
    CountingDocumentListener listener = new CountingDocumentListener();
    field.addDocumentListener(listener);
    return listener;
  }

  private void editDocument(@NotNull Document document, @NotNull String text) {
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText(text));
  }

  private static final class CountingDocumentListener implements DocumentListener {
    final AtomicInteger count = new AtomicInteger();

    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      count.incrementAndGet();
    }
  }
}
