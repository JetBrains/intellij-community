// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
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

public final class CheckBoxListModelEditor<T> {
  private final CheckBoxList<T> list;
  private final ToolbarDecorator toolbarDecorator;
  private final Function<? super T, @NlsContexts.Checkbox String> toNameConverter;

  public CheckBoxListModelEditor(@NotNull Function<? super T, @NlsContexts.Checkbox String> toNameConverter, @NotNull @NlsContexts.StatusText String emptyText) {
    this.toNameConverter = toNameConverter;
    list = new CheckBoxList<>();
    list.setEmptyText(emptyText);
    // toolbar decorator is responsible for border
    list.setBorder(null);
    toolbarDecorator = ToolbarDecorator.createDecorator(list);
  }

  public @NotNull CheckBoxListModelEditor<T> editAction(final @NotNull Function<? super T, ? extends T> consumer) {
    final Runnable action = () -> {
      T item = getSelectedItem();
      if (item != null) {
        T newItem = consumer.fun(item);
        if (newItem != null) {
          list.updateItem(item, newItem, StringUtil.notNullize(toNameConverter.fun(newItem)));
        }
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(list, true));
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

  public @NotNull CheckBoxListModelEditor<T> copyAction(final @NotNull Consumer<? super T> consumer) {
    toolbarDecorator.addExtraAction(new DumbAwareAction(IdeBundle.message("button.copy"), null, PlatformIcons.COPY_ICON) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        int[] indices = list.getSelectedIndices();
        if (indices == null) {
          return;
        }

        for (int index : indices) {
          T item = list.getItemAt(index);
          if (item != null) {
            consumer.consume(item);
          }
        }
      }
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(list.getSelectedIndex() > -1);
      }
    });
    return this;
  }

  public ToolbarDecorator getToolbar() {
    return toolbarDecorator;
  }

  public @NotNull JComponent createComponent() {
    return toolbarDecorator.createPanel();
  }

  public @NotNull DefaultListModel getModel() {
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

  private @Nullable T getSelectedItem() {
    int index = list.getSelectedIndex();
    return index == -1 ? null : list.getItemAt(index);
  }

  public CheckBoxList<T> getList() {
    return list;
  }

  public void reset(@NotNull List<? extends Pair<T, Boolean>> items) {
    list.clear();
    for (Pair<T, Boolean> item : items) {
      list.addItem(item.first, toNameConverter.fun(item.first), item.second);
    }
  }

  public boolean isModified(@NotNull List<? extends Pair<T, Boolean>> oldItems) {
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

  public @NotNull List<T> getItems() {
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

  public @NotNull List<Pair<T, Boolean>> apply() {
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