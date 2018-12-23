// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.wm.IdeFocusManager;

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
      @Override
      public void setValue(Object value) {
        myComponent.getTextField().setText((value != null) ? value.toString() : "");
      }

      @Override
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

  @Override
  public Object getCellEditorValue() {
    return delegate.getCellEditorValue();
  }

  @Override
  public boolean isCellEditable(EventObject anEvent) {
    return delegate.isCellEditable(anEvent);
  }

  @Override
  public boolean shouldSelectCell(EventObject anEvent) {
    return delegate.shouldSelectCell(anEvent);
  }

  @Override
  public boolean stopCellEditing() {
    return delegate.stopCellEditing();
  }

  @Override
  public void cancelCellEditing() {
    delegate.cancelCellEditing();
  }

  @Override
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

    @Override
    public void actionPerformed(ActionEvent e) {
      TableCellEditorWithButton.this.stopCellEditing();
    }

    @Override
    public void itemStateChanged(ItemEvent e) {
      TableCellEditorWithButton.this.stopCellEditing();
    }
  }

  private static class MyComponent extends JPanel {
    private final JTextField myTextField;
    private final FixedSizeButton myButton;

    MyComponent() {
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

    @Override
    public boolean requestDefaultFocus() {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(myTextField, true);
      });
      return true;
    }
  }
}
