// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.table;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBTableRowEditor extends JPanel {
  public interface RowDocumentListener {
    void documentChanged(@NotNull DocumentEvent e, int column);
  }

  private final List<RowDocumentListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private MouseEvent myMouseEvent;

  public abstract void prepareEditor(JTable table, int row);

  public abstract JBTableRow getValue();

  public abstract JComponent getPreferredFocusedComponent();

  public abstract JComponent[] getFocusableComponents();

  public final void addDocumentListener(RowDocumentListener listener) {
    myListeners.add(listener);
  }

  public void fireDocumentChanged(@NotNull DocumentEvent e, int column) {
    for (RowDocumentListener listener : myListeners) {
      listener.documentChanged(e, column);
    }
  }

  public final @Nullable MouseEvent getMouseEvent() {
    if (myMouseEvent != null && myMouseEvent.getClickCount() == 0) return null;
    return myMouseEvent;
  }

  public final void setMouseEvent(@Nullable MouseEvent e) {
    myMouseEvent = e;
  }

  public static JPanel createLabeledPanel(@NlsContexts.Label String labelText, JComponent component) {
    final JPanel panel = new JPanel(new BorderLayout(JBUI.scale(4), JBUI.scale(2)));
    panel.add(new JBLabel(labelText, UIUtil.ComponentStyle.SMALL), BorderLayout.NORTH);
    panel.add(component, BorderLayout.CENTER);
    return panel;
  }

  public final class RowEditorChangeListener implements DocumentListener {
    private final int myColumn;

    public RowEditorChangeListener(int column) {
      myColumn = column;
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      fireDocumentChanged(e, myColumn);
    }
  }
}
