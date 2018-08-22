// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.table;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public abstract class JBTableRowEditor extends JPanel {
  public interface RowDocumentListener {
    void documentChanged(DocumentEvent e, int column);
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

  public void fireDocumentChanged(DocumentEvent e, int column) {
    for (RowDocumentListener listener : myListeners) {
      listener.documentChanged(e, column);
    }
  }

  @Nullable
  public final MouseEvent getMouseEvent() {
    if (myMouseEvent != null && myMouseEvent.getClickCount() == 0) return null;
    return myMouseEvent;
  }

  public final void setMouseEvent(@Nullable MouseEvent e) {
    myMouseEvent = e;
  }

  public static JPanel createLabeledPanel(String labelText, JComponent component) {
    final JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 4, 2, true, false));
    final JBLabel label = new JBLabel(labelText, UIUtil.ComponentStyle.SMALL);
    IJSwingUtilities.adjustComponentsOnMac(label, component);
    panel.add(label);
    panel.add(component);
    return panel;
  }

  public class RowEditorChangeListener implements DocumentListener {
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
