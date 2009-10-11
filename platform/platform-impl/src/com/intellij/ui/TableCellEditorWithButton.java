/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.ui.FixedSizeButton;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.*;
import java.io.Serializable;
import java.util.EventObject;

public class TableCellEditorWithButton extends AbstractCellEditor implements TableCellEditor {

  protected final MyComponent myComponent;
  protected final EditorDelegate delegate;
  protected final int myClickCountToStart = 1;

  public TableCellEditorWithButton() {
    myComponent = new MyComponent();
    delegate = new EditorDelegate() {
      public void setValue(Object value) {
        myComponent.getTextField().setText((value != null) ? value.toString() : "");
      }

      public Object getCellEditorValue() {
        return myComponent.getTextField().getText();
      }
    };
    myComponent.getTextField().addActionListener(delegate);
  }

  public JButton getButton() {
    return myComponent.getButton();
  }

  public JTextField getTextField() {
    return myComponent.getTextField();
  }

  public Object getCellEditorValue() {
    return delegate.getCellEditorValue();
  }

  public boolean isCellEditable(EventObject anEvent) {
    return delegate.isCellEditable(anEvent);
  }

  public boolean shouldSelectCell(EventObject anEvent) {
    return delegate.shouldSelectCell(anEvent);
  }

  public boolean stopCellEditing() {
    return delegate.stopCellEditing();
  }

  public void cancelCellEditing() {
    delegate.cancelCellEditing();
  }

  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
    delegate.setValue(value);
    return myComponent;
  }

  protected class EditorDelegate implements ActionListener, ItemListener, Serializable {

    protected Object value;

    public Object getCellEditorValue() {
      return value;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    public boolean isCellEditable(EventObject anEvent) {
      if (anEvent instanceof MouseEvent) {
        return ((MouseEvent)anEvent).getClickCount() >= myClickCountToStart;
      }
      return true;
    }

    public boolean shouldSelectCell(EventObject anEvent) {
      return true;
    }

    public boolean stopCellEditing() {
      fireEditingStopped();
      return true;
    }

    public void cancelCellEditing() {
      fireEditingCanceled();
    }

    public void actionPerformed(ActionEvent e) {
      TableCellEditorWithButton.this.stopCellEditing();
    }

    public void itemStateChanged(ItemEvent e) {
      TableCellEditorWithButton.this.stopCellEditing();
    }
  }

  private class MyComponent extends JPanel {
    private final JTextField myTextField;
    private final FixedSizeButton myButton;

    public MyComponent() {
      super(new GridBagLayout());

      myTextField = new JTextField();
      myButton = new FixedSizeButton(myTextField);

      add(myTextField, new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.WEST, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
      add(myButton, new GridBagConstraints(1, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    }

    public FixedSizeButton getButton() {
      return myButton;
    }

    public JTextField getTextField() {
      return myTextField;
    }

    public boolean requestDefaultFocus() {
      myTextField.requestFocus();
      return true;
    }
  }
}
