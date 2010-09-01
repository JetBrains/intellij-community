/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.table.TableView;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.table.TableCellEditor;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.EventObject;

public abstract class ChangesTrackingTableView<T> extends TableView<T> {

  private Disposable myEditorListenerDisposable;

  protected abstract void onCellValueChanged(int row, int column, Object value);

  protected abstract void onEditingStopped();

  @Override
  public TableCellEditor getCellEditor(int row, int column) {
    final TableCellEditor editor = super.getCellEditor(row, column);
    if (column == 0 && editor instanceof DefaultCellEditor) {
      //((DefaultCellEditor)editor).setClickCountToStart(1);
    }
    return editor;
  }

  @Override
  public boolean editCellAt(final int row, final int column, EventObject e) {
    if (super.editCellAt(row, column, e)) {
      assert myEditorListenerDisposable == null;
      myEditorListenerDisposable = new Disposable() {
        @Override
        public void dispose() {
        }
      };
      addChangeListener(getEditorComponent(), new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
          onCellValueChanged(row, column, getValue(getEditorComponent()));
        }
      }, myEditorListenerDisposable);
      return true;
    }
    return false;
  }

  @Override
  public void removeEditor() {
    if (myEditorListenerDisposable != null) {
      Disposer.dispose(myEditorListenerDisposable);
      myEditorListenerDisposable = null;
    }

    onEditingStopped();
    super.removeEditor();
  }

  public static Object getValue(Component component) {
    if (component instanceof CellEditorComponentWithBrowseButton) {
      final JTextField textField = (JTextField)((CellEditorComponentWithBrowseButton)component).getChildComponent();
      return textField.getText();
    }
    else if (component instanceof JTextField) {
      return ((JTextField)component).getText();
    }
    else if (component instanceof JComboBox) {
      return ((JComboBox)component).getSelectedItem();
    }
    throw new UnsupportedOperationException("editor control of type " + component.getClass().getName() + " is not supported");
  }

  private static void addChangeListener(final Component component, final ChangeListener listener, Disposable parentDisposable) {
    if (component instanceof CellEditorComponentWithBrowseButton) {
      addChangeListener(((CellEditorComponentWithBrowseButton)component).getChildComponent(), listener, parentDisposable);
    }
    else if (component instanceof JTextField) {
      final DocumentAdapter documentListener = new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          listener.stateChanged(new ChangeEvent(component));
        }
      };
      final Document document = ((JTextField)component).getDocument();
      document.addDocumentListener(documentListener);
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          document.removeDocumentListener(documentListener);
        }
      });
    }
    else if (component instanceof JComboBox) {
      final ActionListener comboListener = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          listener.stateChanged(new ChangeEvent(component));
        }
      };
      ((JComboBox)component).addActionListener(comboListener);
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          ((JComboBox)component).removeActionListener(comboListener);
        }
      });
    }
    else {
      throw new UnsupportedOperationException("editor control of type " + component.getClass().getName() + " is not supported");
    }
  }

}