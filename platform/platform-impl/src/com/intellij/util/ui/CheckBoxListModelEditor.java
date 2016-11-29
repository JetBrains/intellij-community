/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.util.Consumer;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class CheckBoxListModelEditor<T> {
  private final CheckBoxList<T> list;
  private final ToolbarDecorator toolbarDecorator;
  private final Function<T, String> toNameConverter;

  public CheckBoxListModelEditor(@NotNull Function<T, String> toNameConverter, @NotNull String emptyText) {
    this.toNameConverter = toNameConverter;
    list = new CheckBoxList<>();
    list.setEmptyText(emptyText);
    // toolbar decorator is responsible for border
    list.setBorder(null);
    toolbarDecorator = ToolbarDecorator.createDecorator(list);
  }

  @NotNull
  public CheckBoxListModelEditor<T> editAction(final @NotNull Function<T, T> consumer) {
    final Runnable action = () -> {
      T item = getSelectedItem();
      if (item != null) {
        T newItem = consumer.fun(item);
        if (newItem != null) {
          list.updateItem(item, newItem, StringUtil.notNullize(toNameConverter.fun(newItem)));
        }
        list.requestFocus();
      }
    };
    toolbarDecorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        action.run();
      }
    });
    EditSourceOnDoubleClickHandler.install(list, action);
    return this;
  }

  @NotNull
  public CheckBoxListModelEditor<T> copyAction(final @NotNull Consumer<T> consumer) {
    toolbarDecorator.addExtraAction(new ToolbarDecorator.ElementActionButton(IdeBundle.message("button.copy"), PlatformIcons.COPY_ICON) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        int[] indices = list.getSelectedIndices();
        if (indices == null || indices.length == 0) {
          return;
        }

        for (int index : indices) {
          T item = list.getItemAt(index);
          if (item != null) {
            consumer.consume(item);
          }
        }
      }
    });
    return this;
  }

  public ToolbarDecorator getToolbar() {
    return toolbarDecorator;
  }

  @NotNull
  public JComponent createComponent() {
    return toolbarDecorator.createPanel();
  }

  @NotNull
  public DefaultListModel getModel() {
    return ((DefaultListModel)list.getModel());
  }

  public void selectItemByName(@NotNull String name) {
    for (int i = 0; i < list.getItemsCount(); i++) {
      T item = list.getItemAt(i);
      if (item != null && name.equalsIgnoreCase(toNameConverter.fun(item))) {
        list.setSelectedIndex(i);
      }
    }
  }

  @Nullable
  private T getSelectedItem() {
    int index = list.getSelectedIndex();
    return index == -1 ? null : list.getItemAt(index);
  }

  public CheckBoxList<T> getList() {
    return list;
  }

  public void reset(@NotNull List<Pair<T, Boolean>> items) {
    list.clear();
    for (Pair<T, Boolean> item : items) {
      list.addItem(item.first, toNameConverter.fun(item.first), item.second);
    }
  }

  public boolean isModified(@NotNull List<Pair<T, Boolean>> oldItems) {
    if (oldItems.size() != list.getItemsCount()) {
      return true;
    }

    for (int i = 0; i < list.getItemsCount(); i++) {
      T item = list.getItemAt(i);
      if (item == null) {
        return true;
      }

      Pair<T, Boolean> oldItem = oldItems.get(i);
      if (oldItem.second != list.isItemSelected(i) || !oldItem.first.equals(item)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public List<T> getItems() {
    int count = list.getItemsCount();
    List<T> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      T item = list.getItemAt(i);
      if (item != null) {
        result.add(item);
      }
    }
    return result;
  }

  @NotNull
  public List<Pair<T, Boolean>> apply() {
    int count = list.getItemsCount();
    List<Pair<T, Boolean>> result = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      T item = list.getItemAt(i);
      if (item != null) {
        result.add(Pair.create(item, list.isItemSelected(i)));
      }
    }
    return result;
  }
}