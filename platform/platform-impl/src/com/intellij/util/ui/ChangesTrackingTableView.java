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

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.table.TableView;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.table.TableCellEditor;
import java.util.EventObject;

public abstract class ChangesTrackingTableView<T> extends TableView<T> {

  private DocumentAdapter myMessageUpdater;

  protected abstract void onTextChanged(int row, int column, String value);

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
      assert myMessageUpdater == null;
      final JTextField textField;
      if (getEditorComponent() instanceof CellEditorComponentWithBrowseButton) {
        textField = (JTextField)((CellEditorComponentWithBrowseButton)editorComp).getChildComponent();
      }
      else {
        textField = (JTextField)getEditorComponent();
      }
      myMessageUpdater = new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          onTextChanged(row, column, textField.getText());
        }
      };
      textField.getDocument().addDocumentListener(myMessageUpdater);
      return true;
    }
    return false;
  }

  @Override
  public void removeEditor() {
    if (myMessageUpdater != null) {
      final JTextField textField;
      if (getEditorComponent() instanceof CellEditorComponentWithBrowseButton) {
        textField = (JTextField)((CellEditorComponentWithBrowseButton)editorComp).getChildComponent();
      }
      else {
        textField = (JTextField)getEditorComponent();
      }
      textField.getDocument().removeDocumentListener(myMessageUpdater);
      myMessageUpdater = null;
    }

    onEditingStopped();
    super.removeEditor();
  }
}
