// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretActionListener;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import javax.swing.text.JTextComponent;
import java.lang.ref.WeakReference;

/**
 * How it works:<br>
 * 1. if {@link #beforeAllCaretsAction} / {@link #beforeDocumentChange(Document)} gets called, then {@link JTextComponent#fireCaretUpdate}
 *    will be called ONLY after {@link #afterAllCaretsAction} / {@link #afterDocumentChange(Document)} will have been called respectively
 *    (if the primary caret position will have been changed)<br>
 * 2. otherwise, if any of {@link #documentChanged}, {@link #caretPositionChanged}, {@link #caretAdded}, {@link #caretRemoved} gets called,
 *    {@link JTextComponent#fireCaretUpdate} will be called as well (again, if the primary caret position will have been changed)
 * <p>
 * <p/>
 *
 * Why we need all these listeners:<br>
 * -  if we don't install {@link CaretListener}, we'll miss some caret updates which arrive outside of {@link CaretActionListener} events,
 *    e.g. the test {@link com.intellij.openapi.editor.impl.EditorComponentCaretListenerTest#testCaretNotificationsCausedByUndo testCaretNotificationsCausedByUndo}
 *    won't get a notification after pasting a text into the editor;<br>
 * -  if we don't install {@link CaretActionListener}, we'll sometimes get incorrect position for {@link javax.swing.event.CaretEvent#getMark()},
 *    because position of the caret can be updated a bit earlier than the selection.
 *    E.g. the test {@link com.intellij.openapi.editor.impl.EditorComponentCaretListenerTest#testCaretNotificationsOfSelectionMovementsWithoutTextModificationsFromTopLeft testCaretNotificationsOfSelectionMovementsWithoutTextModificationsFromTopLeft}
 *    will get the wrong position of the selection at the first moving of the caret to the right;<br>
 * -  if we don't install {@link DocumentListener}, we'll miss caret movements caused by document changes. See {@link CaretListener#caretPositionChanged} for more info.
 *    E.g. the test {@link com.intellij.openapi.editor.impl.EditorComponentCaretListenerTest#testCaretNotificationsCausedByUndo testCaretNotificationsCausedByUndo}
 *    won't get a notification after undoing the pasting
 */
final class EditorSwingCaretUpdatesCourier implements CaretListener, CaretActionListener, BulkAwareDocumentListener.Simple {
  private static final Logger LOG = Logger.getInstance(EditorSwingCaretUpdatesCourier.class);

  private final EditorComponentImpl component;
  private final EditorImpl editor;
  /** true if {@link #beforeAllCaretsAction} has been called, but {@link #afterAllCaretsAction} - has still not */
  private boolean isInsideCaretsAction = false;
  private boolean isInsideBulkDocumentUpdate = false;
  private @NotNull WeakReference<Caret> myLastKnownPrimaryCaret;
  private int myPrimaryCaretLastKnownDot;
  private int myPrimaryCaretLastKnownMark;

  @RequiresEdt
  EditorSwingCaretUpdatesCourier(@NotNull EditorComponentImpl component) {
    this.component = component;
    editor = component.getEditor();
    assert(!editor.isDisposed());

    final @NotNull var caretModel = editor.getCaretModel();
    final @NotNull var primaryCaret = caretModel.getPrimaryCaret();

    myLastKnownPrimaryCaret = new WeakReference<>(primaryCaret);
    EditorThreading.run(() -> {
      myPrimaryCaretLastKnownDot = primaryCaret.getOffset();
      myPrimaryCaretLastKnownMark = primaryCaret.getLeadSelectionOffset();
    });

    caretModel.addCaretActionListener(this, editor.getDisposable());
    caretModel.addCaretListener(this, editor.getDisposable());
    editor.getDocument().addDocumentListener(this, editor.getDisposable());
  }


  // ---- CaretActionListener ----

  @Override
  @RequiresEdt
  public void beforeAllCaretsAction() {
    isInsideCaretsAction = true;
  }

  @Override
  @RequiresEdt
  public void afterAllCaretsAction() {
    isInsideCaretsAction = false;

    if (isInsideBulkUpdate()) {
      return;
    }

    final var currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();
    primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
  }


  // ---- CaretListener ----

  @Override
  @RequiresEdt
  public void caretPositionChanged(@NotNull CaretEvent event) {
    if (isInsideBulkUpdate()) {
      return;
    }

    final Caret changedCaret = event.getCaret();
    final Caret currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();

    if (changedCaret != currentPrimaryCaret) {
      // Filter out changes of secondary carets
      return;
    }

    primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
  }

  @Override
  @RequiresEdt
  public void caretAdded(@NotNull CaretEvent event) {
    // Adding a caret may cause a change of the primary caret instance

    if (isInsideBulkUpdate()) {
      return;
    }

    final Caret addedCaret = event.getCaret();
    final Caret currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();

    if (addedCaret != currentPrimaryCaret) {
      // The added caret hasn't become primary, so we're not interested in its position
      return;
    }

    primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
  }

  @Override
  @RequiresEdt
  public void caretRemoved(@NotNull CaretEvent event) {
    // Removing a caret may cause a switching of the primary caret

    if (isInsideBulkUpdate()) {
      return;
    }

    final var currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();

    if (myLastKnownPrimaryCaret.refersTo(currentPrimaryCaret)) {
      // The removal of a caret didn't cause a switching of the primary caret, so its position isn't supposed to have changed
      return;
    }

    primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
  }


  // ---- BulkAwareDocumentListener ----

  @Override
  @RequiresEdt
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    isInsideBulkDocumentUpdate = true;
  }

  @Override
  @RequiresEdt
  public void afterDocumentChange(@NotNull Document document) {
    isInsideBulkDocumentUpdate = false;

    if (isInsideBulkUpdate()) {
      return;
    }

    final Caret currentPrimaryCaret = editor.getCaretModel().getPrimaryCaret();
    primaryCaretPositionPossiblyChanged(currentPrimaryCaret);
  }


  // ---- implementation details ----

  @RequiresEdt
  private boolean isInsideBulkUpdate() {
    return isInsideCaretsAction || isInsideBulkDocumentUpdate;
  }

  @RequiresEdt // if you're going to remove this requirement, don't forget to make access to the fields thread-safe
  private void primaryCaretPositionPossiblyChanged(final @NotNull Caret currentPrimaryCaret) {
    final int currentPrimaryCaretDot = currentPrimaryCaret.getOffset();
    final int currentPrimaryCaretMark = currentPrimaryCaret.getLeadSelectionOffset();

    if (!myLastKnownPrimaryCaret.refersTo(currentPrimaryCaret)) {
      myLastKnownPrimaryCaret.clear();
      myLastKnownPrimaryCaret = new WeakReference<>(currentPrimaryCaret);
    }

    if (currentPrimaryCaretDot < 0) {
      LOG.error(
        "currentPrimaryCaretDot < 0",
        String.format("currentPrimaryCaretDot == %d", currentPrimaryCaretDot),
        String.format("currentPrimaryCaret == %s", currentPrimaryCaret)
      );
    }
    else if (currentPrimaryCaretMark < 0) { // I don't think it worth adding almost the same error twice, so it's an else-if
      LOG.error(
        "currentPrimaryCaretMark < 0",
        String.format("currentPrimaryCaretMark == %d", currentPrimaryCaretMark),
        String.format("currentPrimaryCaret == %s", currentPrimaryCaret)
      );
    }

    if ((myPrimaryCaretLastKnownDot == currentPrimaryCaretDot) && (myPrimaryCaretLastKnownMark == currentPrimaryCaretMark)) {
      // The position hasn't changed
      return;
    }

    myPrimaryCaretLastKnownDot = currentPrimaryCaretDot;
    myPrimaryCaretLastKnownMark = currentPrimaryCaretMark;

    component.fireSwingCaretUpdate(new javax.swing.event.CaretEvent(component) {
      @Override
      public int getDot() {
        return currentPrimaryCaretDot;
      }

      @Override
      public int getMark() {
        return currentPrimaryCaretMark;
      }
    });
  }
}
