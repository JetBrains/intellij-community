// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.MutableCollectionComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ListModelEditorBase<T> extends CollectionModelEditor<T, ListItemEditor<T>> {
  // we use combobox model to avoid code duplication - in any case, we expose it as CollectionListModel
  protected final MyModel model = new MyModel();

  public ListModelEditorBase(@NotNull ListItemEditor<T> itemEditor) {
    super(itemEditor);
  }

  @NotNull
  public CollectionListModel<T> getModel() {
    return model;
  }

  @NotNull
  @Override
  protected List<T> getItems() {
    return model.items();
  }

  @Override
  public void reset(@NotNull List<? extends T> items) {
    super.reset(items);
    model.replaceAll(items);
  }

  public final void ensureNonEmptyNames(@NotNull @NlsContexts.DialogMessage String errorMessage) throws ConfigurationException {
    List<T> items = getItems();
    for (int i = items.size() - 1; i >= 0; i--) {
      T item = items.get(i);
      if (StringUtil.isEmptyOrSpaces(itemEditor.getName(item))) {
        if (itemEditor.isEmpty(item)) {
          removeEmptyItem(i);
        }
        else {
          throw new ConfigurationException(errorMessage);
        }
      }
    }
  }

  protected void removeEmptyItem(int i) {
  }

  @Override
  protected boolean isEditable(@NotNull T item) {
    return itemEditor.isEditable(item);
  }

  @NotNull
  public List<T> apply() {
    final List<T> items = getItems();
    if (!helper.hasModifiedItems()) {
      return items;
    }

    helper.process((newItem, oldItem) -> {
      itemEditor.applyModifiedProperties(newItem, oldItem);
      silentlyReplaceItem(newItem, oldItem, -1);
      return true;
    });

    helper.reset(items);
    return items;
  }

  @Override
  protected void silentlyReplaceItem(@NotNull T oldItem, @NotNull T newItem, int index) {
    super.silentlyReplaceItem(oldItem, newItem, index);
    model.checkSelectionOnSilentReplace(oldItem, newItem);
  }

  protected final class MyModel extends MutableCollectionComboBoxModel<T> {
    @NotNull List<T> items() {
      return super.getInternalList();
    }

    void checkSelectionOnSilentReplace(@NotNull T oldItem, @NotNull T newItem) {
      if (mySelection == oldItem) {
        mySelection = newItem;
      }
    }

    @Override
    protected void itemReplaced(@NotNull T existingItem, @Nullable T newItem) {
      helper.remove(existingItem);
    }

    @Override
    public void removeAll() {
      super.removeAll();
      helper.reset(null);
    }
  }
}