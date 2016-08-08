/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedComboBoxEditor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.MutableCollectionComboBoxModel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public final class ComboBoxModelEditor<T> extends ListModelEditorBase<T> {
  private final ComboBox<T> comboBox;

  public ComboBoxModelEditor(@NotNull ListItemEditor<T> itemEditor) {
    super(itemEditor);

    comboBox = new ComboBox<>(model);
    comboBox.setEditor(new NameEditor());
    comboBox.setRenderer(new MyListCellRenderer());
  }

  @NotNull
  @Override
  public MutableCollectionComboBoxModel<T> getModel() {
    return model;
  }

  @NotNull
  public ComboBox getComboBox() {
    return comboBox;
  }

  private class NameEditor extends FixedComboBoxEditor {
    private T item = null;
    private boolean mutated;

    public NameEditor() {
      getField().getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          if (item != null && itemEditor.isEditable(item)) {
            String newName = getField().getText();
            if (newName.equals(itemEditor.getName(item))) {
              return;
            }

            if (!mutated) {
              mutated = true;
              item = getMutable(item);
            }
            ((KeymapImpl)item).setName(newName);
          }
        }
      });
    }

    @Override
    public void setItem(Object newItem) {
      if (newItem != null && newItem != item) {
        //noinspection unchecked
        item = (T)newItem;
        mutated = false;
        getField().setText(itemEditor.getName(item));
      }
    }

    @Override
    public Object getItem() {
      return item;
    }
  }

  private class MyListCellRenderer extends ListCellRendererWrapper<T> {
    @Override
    public void customize(JList list, T item, int index, boolean selected, boolean hasFocus) {
      if (item != null) {
        setText(itemEditor.getName(item));
      }
    }
  }
}
