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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ListModelEditor<T> extends ListModelEditorBase<T> {
  private final ToolbarDecorator toolbarDecorator;

  private JBList list = new JBList(model);

  public ListModelEditor(@NotNull ListItemEditor<T> itemEditor) {
    super(itemEditor);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new MyListCellRenderer());

    toolbarDecorator = ToolbarDecorator.createDecorator(list, model)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
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
        }
      })
    .setRemoveActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        return areSelectedItemsRemovable(list.getSelectionModel());
      }
    });
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
    //noinspection unchecked
    return (T)list.getSelectedValue();
  }

  public void reset(@NotNull List<T> items) {
    super.reset(items);

    // todo should we really do this?
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!model.isEmpty()) {
          list.setSelectedIndex(0);
        }
      }
    });
  }

  private class MyListCellRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      setBackground(UIUtil.getListBackground(selected));
      if (value != null) {
        //noinspection unchecked
        append((itemEditor.getName(((T)value))));
      }
    }
  }

  @Override
  protected void removeEmptyItem(int i) {
    ListUtil.removeIndices(getList(), new int[]{i});
  }
}