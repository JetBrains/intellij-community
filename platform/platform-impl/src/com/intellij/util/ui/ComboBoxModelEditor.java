// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
        protected void textChanged(@NotNull DocumentEvent e) {
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
