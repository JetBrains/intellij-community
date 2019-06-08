// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ListModelEditor<T> extends ListModelEditorBase<T> {
  private final ToolbarDecorator toolbarDecorator;

  private final JBList<T> list = new JBList<>(model);

  public ListModelEditor(@NotNull ListItemEditor<T> itemEditor) {
    super(itemEditor);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(SimpleListCellRenderer.create("", o -> itemEditor.getName(o)));

    toolbarDecorator = ToolbarDecorator.createDecorator(list, model)
      .setAddAction(button -> {
        if (!model.isEmpty()) {
          T lastItem = model.getElementAt(model.getSize() - 1);
          if (ListModelEditor.this.itemEditor.isEmpty(lastItem)) {
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

  @NotNull
  public ListModelEditor<T> disableUpDownActions() {
    toolbarDecorator.disableUpDownActions();
    return this;
  }

  @NotNull
  public JComponent createComponent() {
    return toolbarDecorator.createPanel();
  }

  @NotNull
  public JBList getList() {
    return list;
  }

  @Nullable
  public T getSelected() {
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