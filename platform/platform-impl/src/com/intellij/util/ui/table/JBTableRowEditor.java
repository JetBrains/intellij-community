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
package com.intellij.util.ui.table;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
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
    public void documentChanged(DocumentEvent e) {
      fireDocumentChanged(e, myColumn);
    }
  }
}
