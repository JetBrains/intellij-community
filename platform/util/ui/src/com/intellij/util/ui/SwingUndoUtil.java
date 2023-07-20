// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;

public final class SwingUndoUtil {
  private static final Key<UndoManager> UNDO_MANAGER = Key.create("undoManager");

  private static final Action REDO_ACTION = new AbstractAction() {
    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      Object source = e.getSource();
      UndoManager manager = ClientProperty.get(source instanceof Component ? (Component)source : null, UNDO_MANAGER);
      if (manager != null && manager.canRedo()) {
        manager.redo();
      }
    }
  };
  private static final Action UNDO_ACTION = new AbstractAction() {
    @Override
    public void actionPerformed(@NotNull ActionEvent e) {
      Object source = e.getSource();
      UndoManager manager = ClientProperty.get(source instanceof Component ? (Component)source : null, UNDO_MANAGER);
      if (manager != null && manager.canUndo()) {
        manager.undo();
      }
    }
  };

  private static final DocumentListener SET_TEXT_CHECKER = new DocumentAdapter() {
    @Override
    protected void textChanged(@NotNull DocumentEvent e) {
      Document document = e.getDocument();
      if (document instanceof AbstractDocument) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        for (StackTraceElement element : stackTrace) {
          if (!element.getClassName().equals(JTextComponent.class.getName()) || !element.getMethodName().equals("setText")) continue;
          UndoableEditListener[] undoableEditListeners = ((AbstractDocument)document).getUndoableEditListeners();
          for (final UndoableEditListener listener : undoableEditListeners) {
            if (listener instanceof UndoManager) {
              Runnable runnable = ((UndoManager)listener)::discardAllEdits;
              SwingUtilities.invokeLater(runnable);
              return;
            }
          }
        }
      }
    }
  };

  public static @Nullable UndoManager getUndoManager(Component component) {
    if (component instanceof JTextComponent) {
      Object o = ((JTextComponent)component).getClientProperty(UNDO_MANAGER);
      if (o instanceof UndoManager) {
        return (UndoManager)o;
      }
    }
    return null;
  }

  public static void addUndoRedoActions(@NotNull JTextComponent textComponent) {
    if (textComponent.getClientProperty(UNDO_MANAGER) instanceof UndoManager) {
      return;
    }

    UndoManager undoManager = new UndoManager();
    textComponent.putClientProperty(UNDO_MANAGER, undoManager);
    textComponent.getDocument().addUndoableEditListener(undoManager);
    textComponent.getDocument().addDocumentListener(SET_TEXT_CHECKER);
    textComponent.getInputMap()
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, SystemInfoRt.isMac ? Event.META_MASK : Event.CTRL_MASK), "undoKeystroke");
    textComponent.getActionMap().put("undoKeystroke", UNDO_ACTION);
    textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, (SystemInfoRt.isMac
                                                                           ? Event.META_MASK : Event.CTRL_MASK) | Event.SHIFT_MASK),
                                    "redoKeystroke");
    textComponent.getActionMap().put("redoKeystroke", REDO_ACTION);
  }

  public static void resetUndoRedoActions(@NotNull JTextComponent textComponent) {
    UndoManager undoManager = ClientProperty.get(textComponent, UNDO_MANAGER);
    if (undoManager != null) {
      undoManager.discardAllEdits();
    }
  }

  /**
   * Use this method to replace a text field text completely but keep the previous value in the undo stack.
   * Ordinary {@link JTextField#setText} calls reset undo stack.
   *
   * @see #SET_TEXT_CHECKER
   * @see #resetUndoRedoActions usages
   */
  public static void setTextWithUndo(@NotNull JTextComponent field, @NotNull String text) {
    if (field.getText().equals(text)) return;
    addUndoRedoActions(field);
    Document doc = field.getDocument();
    UndoManager undoManager = Objects.requireNonNull(ClientProperty.get(field, UNDO_MANAGER));
    CompoundEdit edits = new CompoundEdit();
    UndoableEditListener tempListener = new UndoableEditListener() {
      @Override
      public void undoableEditHappened(UndoableEditEvent e) {
        edits.addEdit(e.getEdit());
      }
    };
    doc.removeUndoableEditListener(undoManager);
    doc.addUndoableEditListener(tempListener);
    try {
      doc.remove(0, doc.getLength());
      doc.insertString(0, text, null);
      edits.end();
      undoManager.addEdit(edits);
    }
    catch (BadLocationException e) {
      UIManager.getLookAndFeel().provideErrorFeedback(field);
    }
    finally {
      doc.removeUndoableEditListener(tempListener);
      doc.addUndoableEditListener(undoManager);
    }
  }
}
