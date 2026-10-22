// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorThreading;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.UndoableEditListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.Segment;
import java.util.ArrayList;
import java.util.List;

/** {@linkplain javax.swing.text.PlainDocument} does a lot of work we don't need.
 * This exists simply to be able to send editing events to the screen reader. */
@SuppressWarnings("UnnecessaryFullyQualifiedName")
final class EditorAccessibilityDocument implements javax.swing.text.Document, javax.swing.text.Element {
  private final EditorImpl editor;
  private final EditorAccessibleText myAccessibleText;
  private List<javax.swing.event.DocumentListener> myListeners;

  EditorAccessibilityDocument(@NotNull EditorImpl editor, @NotNull EditorAccessibleText accessibleText) {
    this.editor = editor;
    myAccessibleText = accessibleText;
  }

  /** Redispatch an IDE {@link DocumentEvent} to a Swing {@link javax.swing.event.DocumentListener} */
  void fireDocumentChange(final DocumentEvent event) {
    List<javax.swing.event.DocumentListener> listeners = myListeners;
    if (listeners == null) {
      return;
    }

    int offset = myAccessibleText.fromDocumentOffset(event.getOffset());
    int length = myAccessibleText.fromDocumentOffset(event.getOffset() + event.getNewLength()) - offset;
    javax.swing.event.DocumentEvent swingEvent = new javax.swing.event.DocumentEvent() {
      @Override
      public int getOffset() {
        return offset;
      }

      @Override
      public int getLength() {
        return length;
      }

      @Override
      public javax.swing.text.Document getDocument() {
        return EditorAccessibilityDocument.this;
      }

      @Override
      public EventType getType() {
        return event.getOldLength() == 0 ? EventType.INSERT : event.getNewLength() == 0 ? EventType.REMOVE : EventType.CHANGE;
      }

      @Override
      public @Nullable ElementChange getChange(Element element) {
        return null;
      }
    };
    for (javax.swing.event.DocumentListener listener : listeners) {
      javax.swing.event.DocumentEvent.EventType type = swingEvent.getType();
      if (type == javax.swing.event.DocumentEvent.EventType.INSERT) {
        listener.insertUpdate(swingEvent);
      } else if (type == javax.swing.event.DocumentEvent.EventType.REMOVE) {
        listener.removeUpdate(swingEvent);
      } else if (type == javax.swing.event.DocumentEvent.EventType.CHANGE) {
        listener.changedUpdate(swingEvent);
      }
    }
  }

  @Override
  public int getLength() {
    return editor.getDocument().getTextLength();
  }

  @Override
  public void addDocumentListener(javax.swing.event.DocumentListener documentListener) {
    if (myListeners == null) {
      myListeners = new ArrayList<>(2);
    }
    myListeners.add(documentListener);
  }

  @Override
  public void removeDocumentListener(javax.swing.event.DocumentListener documentListener) {
    if (myListeners != null) {
      myListeners.remove(documentListener);
    }
  }

  @Override
  public void addUndoableEditListener(UndoableEditListener undoableEditListener) {
  }

  @Override
  public void removeUndoableEditListener(UndoableEditListener undoableEditListener) {
  }

  @Override
  public @Nullable Object getProperty(Object o) {
    return null;
  }

  @Override
  public void putProperty(Object o, Object o1) {
  }

  @Override
  public void remove(final int offset, final int length) {
    EditorComponentImpl.editDocumentSafely(editor, offset, length, null);
  }

  @Override
  public void insertString(final int offset, final String text, AttributeSet attributeSet) {
    EditorComponentImpl.editDocumentSafely(editor, offset, 0, text);
  }

  @Override
  public String getText(final int offset, final int length) {
    return EditorThreading.compute(() -> editor.getDocument().getText(new TextRange(offset, offset + length)));
  }

  @Override
  public void getText(int offset, int length, Segment segment) {
    char[] s = getText(offset, length).toCharArray();
    segment.array = s;
    segment.offset = 0;
    segment.count = s.length;
  }

  @Override
  public @NotNull Position getStartPosition() {
    notSupported();
    return null;
  }

  @Override
  public @NotNull Position getEndPosition() {
    notSupported();
    return null;
  }

  @Override
  public @NotNull Position createPosition(int i) {
    notSupported();
    return null;
  }

  @Override
  public Element[] getRootElements() {
    return new Element[] { this };
  }

  @Override
  public Element getDefaultRootElement() {
    return this;
  }

  @SuppressWarnings("UseRunReadActionBlockingShortcut")
  @Override
  public void render(Runnable runnable) {
    ApplicationManager.getApplication().runReadAction(runnable);
  }

  // ---- Implements Element for the root element ----
  //
  // This is here because the accessibility code ends up calling some JTextComponent
  // methods; in particular, CAccessibleText calls root.getElementIndex(index)
  // to map an offset to a line number, and getRangeForLine calls root.getElement(lineIndex)
  // to get a range object for a given line, and then getStartOffset() and getEndOffset()
  // on the result.

  @Override
  public javax.swing.text.Document getDocument() {
    return this;
  }

  @Override
  public @Nullable Element getParentElement() {
    return null;
  }

  @Override
  public @Nullable String getName() {
    return null;
  }

  @Override
  public @Nullable AttributeSet getAttributes() {
    return null;
  }

  @Override
  public int getStartOffset() {
    return 0;
  }

  @Override
  public int getEndOffset() {
    return myAccessibleText.getLength();
  }

  @Override
  public int getElementIndex(int i) {
    // For the root element this asks for the index of the offset, which
    // means the line number
    return myAccessibleText.getLineNumber(Math.clamp(i, 0, myAccessibleText.getLength()));
  }

  @Override
  public int getElementCount() {
    return myAccessibleText.getLineCount();
  }

  @Override
  public Element getElement(final int i) {
    return new Element() {
      @Override
      public javax.swing.text.Document getDocument() {
        return EditorAccessibilityDocument.this;
      }

      @Override
      public Element getParentElement() {
        return EditorAccessibilityDocument.this;
      }

      @Override
      public @Nullable String getName() {
        return null;
      }

      @Override
      public @Nullable AttributeSet getAttributes() {
        return null;
      }

      @Override
      public int getStartOffset() {
        return myAccessibleText.getLineStartOffset(i);
      }

      @Override
      public int getEndOffset() {
        return myAccessibleText.getLineEndOffset(i);
      }

      @Override
      public int getElementIndex(int i) {
        return 0;
      }

      @Override
      public int getElementCount() {
        return 0;
      }

      @Override
      public @Nullable Element getElement(int i) {
        return null;
      }

      @Override
      public boolean isLeaf() {
        return true;
      }
    };
  }

  @Override
  public boolean isLeaf() {
    return false;
  }

  private static void notSupported() {
    throw new RuntimeException("Not supported for this text implementation");
  }
}
