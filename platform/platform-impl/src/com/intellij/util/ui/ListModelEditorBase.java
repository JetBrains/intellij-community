/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.MutableCollectionComboBoxModel;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectObjectProcedure;
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
  public void reset(@NotNull List<T> items) {
    super.reset(items);
    model.replaceAll(items);
  }

  public final void ensureNonEmptyNames(@NotNull String errorMessage) throws ConfigurationException {
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

    helper.process(new TObjectObjectProcedure<T, T>() {
      @Override
      public boolean execute(T newItem, T oldItem) {
        itemEditor.applyModifiedProperties(newItem, oldItem);
        int index = ContainerUtil.indexOfIdentity(items, newItem);
        if (index == -1) {
          LOG.error("Inconsistent model", newItem.toString());
        }
        model.setElementAt(oldItem, index);
        return true;
      }
    });

    helper.reset(items);
    return items;
  }

  protected final class MyModel extends MutableCollectionComboBoxModel<T> {
    @NotNull
    final List<T> items() {
      return super.getInternalList();
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