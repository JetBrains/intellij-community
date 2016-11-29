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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class QuickListPanel {
  private final CollectionListModel<Object> actionsModel;
  private JPanel myPanel;
  private final JBList myActionsList;
  JTextField myName;
  private JTextField myDescription;
  private JPanel myListPanel;
  QuickList item;

  public QuickListPanel(@NotNull final CollectionListModel<QuickList> model) {
    actionsModel = new CollectionListModel<>();
    myActionsList = new JBList(actionsModel);
    myActionsList.setCellRenderer(new MyListCellRenderer());
    myActionsList.getEmptyText().setText(KeyMapBundle.message("no.actions"));
    myActionsList.setEnabled(true);

    myListPanel.add(ToolbarDecorator.createDecorator(myActionsList)
                      .setAddAction(new AnActionButtonRunnable() {
                        @Override
                        public void run(AnActionButton button) {
                          List<QuickList> items = model.getItems();
                          ChooseActionsDialog dialog = new ChooseActionsDialog(myActionsList, KeymapManager.getInstance().getActiveKeymap(), items.toArray(new QuickList[items.size()]));
                          if (dialog.showAndGet()) {
                            String[] ids = dialog.getTreeSelectedActionIds();
                            for (String id : ids) {
                              includeActionId(id);
                            }
                            List<Object> list = actionsModel.getItems();
                            int size = list.size();
                            ListSelectionModel selectionModel = myActionsList.getSelectionModel();
                            if (size > 0) {
                              selectionModel.removeIndexInterval(0, size - 1);
                            }
                            for (String id1 : ids) {
                              int idx = list.lastIndexOf(id1);
                              if (idx >= 0) {
                                selectionModel.addSelectionInterval(idx, idx);
                              }
                            }
                          }
                        }
                      })
                      .addExtraAction(new AnActionButton("Add Separator", AllIcons.General.SeparatorH) {
                        @Override
                        public void actionPerformed(@Nullable AnActionEvent e) {
                          actionsModel.add(QuickList.SEPARATOR_ID);
                        }
                      })
                      .setButtonComparator("Add", "Add Separator", "Remove", "Up", "Down")
                      .createPanel(), BorderLayout.CENTER);
  }

  public void apply() {
    if (item == null) {
      return;
    }

    item.setName(myName.getText().trim());
    item.setDescription(myDescription.getText().trim());

    ListModel model = myActionsList.getModel();
    int size = model.getSize();
    String[] ids;
    if (size == 0) {
      ids = ArrayUtil.EMPTY_STRING_ARRAY;
    }
    else {
      ids = new String[size];
      for (int i = 0; i < size; i++) {
        ids[i] = (String)model.getElementAt(i);
      }
    }

    item.setActionIds(ids);
  }

  public void setItem(@Nullable QuickList item) {
    apply();

    this.item = item;
    if (item == null) {
      return;
    }

    myName.setText(item.getName());
    myName.setEnabled(QuickListsManager.getInstance().getSchemeManager().isMetadataEditable(item));
    myDescription.setText(item.getDescription());

    actionsModel.removeAll();
    for (String id : item.getActionIds()) {
      includeActionId(id);
    }
  }

  private void includeActionId(@NotNull String id) {
    if (QuickList.SEPARATOR_ID.equals(id) || actionsModel.getElementIndex(id) == -1) {
      actionsModel.add(id);
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private static class MyListCellRenderer extends DefaultListCellRenderer {
    @NotNull
    @Override
    public Component getListCellRendererComponent(@NotNull JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      Icon icon = null;
      String actionId = (String)value;
      if (QuickList.SEPARATOR_ID.equals(actionId)) {
        setText("-------------");
      }
      else {
        AnAction action = ActionManager.getInstance().getAction(actionId);
        setText(action != null ? action.getTemplatePresentation().getText() : actionId);
        if (action != null) {
          Icon actionIcon = action.getTemplatePresentation().getIcon();
          if (actionIcon != null) {
            icon = actionIcon;
          }
        }
        if (actionId.startsWith(QuickList.QUICK_LIST_PREFIX)) {
          icon = AllIcons.Actions.QuickList;
        }
        setIcon(ActionsTree.getEvenIcon(icon));
      }

      return this;
    }
  }
}
