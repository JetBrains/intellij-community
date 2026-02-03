// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public final class ListModelEditor<T> extends ListModelEditorBase<T> {
  private final ToolbarDecorator toolbarDecorator;

  private final JBList<T> list = new JBList<>(model);

  public ListModelEditor(@NotNull ListItemEditor<T> itemEditor) {
    super(itemEditor);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(BuilderKt.textListCellRenderer(o -> itemEditor.getName(o)));

    toolbarDecorator = ToolbarDecorator.createDecorator(list, model)
      .setAddAction(button -> {
        if (!model.isEmpty()) {
          T lastItem = model.getElementAt(model.getSize() - 1);
          if (this.itemEditor.isEmpty(lastItem)) {
            ScrollingUtil.selectItem(list, ContainerUtil.indexOfIdentity(model.getItems(), lastItem));
            return;
          }
        }

        T item = createElement();
        model.add(item);
        ScrollingUtil.selectItem(list, ContainerUtil.indexOfIdentity(model.getItems(), item));
      })
    .setRemoveActionUpdater(e -> areSelectedItemsRemovable(list.getSelectionModel()));
  }

  public @NotNull ListModelEditor<T> disableUpDownActions() {
    toolbarDecorator.disableUpDownActions();
    return this;
  }

  public @NotNull JComponent createComponent() {
    return toolbarDecorator.createPanel();
  }

  public @NotNull JBList getList() {
    return list;
  }

  public @Nullable T getSelected() {
    return list.getSelectedValue();
  }

  @Override
  public void reset(@NotNull List<? extends T> items) {
    super.reset(items);

    // todo should we really do this?
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      if (!model.isEmpty()) {
        list.setSelectedIndex(0);
      }
    });
  }

  @Override
  protected void removeEmptyItem(int i) {
    ListUtil.removeIndices(getList(), new int[]{i});
  }
}