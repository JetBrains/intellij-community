// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedComboBoxEditor;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.MutableCollectionComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.DocumentEvent;

public final class ComboBoxModelEditor<T> extends ListModelEditorBase<T> {
  private final ComboBox<T> comboBox;

  public ComboBoxModelEditor(@NotNull ListItemEditor<T> itemEditor) {
    super(itemEditor);

    comboBox = new ComboBox<>(model);
    comboBox.setEditor(new NameEditor());
    comboBox.setRenderer(SimpleListCellRenderer.create("", value -> itemEditor.getName(value)));
  }

  @Override
  public @NotNull MutableCollectionComboBoxModel<T> getModel() {
    return model;
  }

  public @NotNull ComboBox getComboBox() {
    return comboBox;
  }

  private final class NameEditor extends FixedComboBoxEditor {
    private T item = null;
    private boolean mutated;

    NameEditor() {
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
}
