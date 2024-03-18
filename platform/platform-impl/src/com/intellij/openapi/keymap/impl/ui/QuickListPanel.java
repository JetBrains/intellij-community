// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

final class QuickListPanel {
  private final CollectionListModel<String> myActionsModel;
  private JPanel myPanel;
  private final JBList<String> myActionsList;
  JTextField myName;
  private JTextField myDescription;
  private JPanel myListPanel;
  QuickList item;

  QuickListPanel(final @NotNull CollectionListModel<QuickList> model) {
    myActionsModel = new MyCollectionListModel();
    myActionsList = new JBList<>(myActionsModel);
    myActionsList.setCellRenderer(new MyListCellRenderer());
    myActionsList.getEmptyText().setText(KeyMapBundle.message("no.actions"));
    myActionsList.setEnabled(true);

    myListPanel.add(ToolbarDecorator.createDecorator(myActionsList)
                      .setAddAction(new AnActionButtonRunnable() {
                        @Override
                        public void run(AnActionButton button) {
                          List<QuickList> items = model.getItems();
                          ChooseActionsDialog dialog = new ChooseActionsDialog(myActionsList, KeymapManager.getInstance().getActiveKeymap(), items.toArray(
                            new QuickList[0]));
                          if (dialog.showAndGet()) {
                            String[] ids = dialog.getTreeSelectedActionIds();
                            for (String id : ids) {
                              includeActionId(id);
                            }
                            List<String> list = myActionsModel.getItems();
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
                      .addExtraAction(new DumbAwareAction(KeyMapBundle.message("keymap.action.add.separator"), null, AllIcons.General.SeparatorH) {
                        @Override
                        public void actionPerformed(@NotNull AnActionEvent e) {
                          myActionsModel.add(QuickList.SEPARATOR_ID);
                        }
                      })
                      .setButtonComparator(
                        CommonActionsPanel.Buttons.ADD.getText(),
                        KeyMapBundle.message("keymap.action.add.separator"),
                        CommonActionsPanel.Buttons.REMOVE.getText(),
                        CommonActionsPanel.Buttons.UP.getText(),
                        CommonActionsPanel.Buttons.DOWN.getText())
                      .createPanel(), BorderLayout.CENTER);
  }

  public void apply() {
    if (item == null) {
      return;
    }

    item.setName(myName.getText().trim());
    item.setDescription(myDescription.getText().trim());

    ListModel<String> model = myActionsList.getModel();
    int size = model.getSize();
    String[] ids;
    if (size == 0) {
      ids = ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
    else {
      ids = new String[size];
      for (int i = 0; i < size; i++) {
        ids[i] = model.getElementAt(i);
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

    myName.setText(item.getDisplayName());
    myName.setEnabled(QuickListsManager.getInstance().getSchemeManager().isMetadataEditable(item));
    myDescription.setText(item.getDescription());

    myActionsModel.removeAll();
    for (String id : item.getActionIds()) {
      includeActionId(id);
    }
  }

  private void includeActionId(@NotNull String id) {
    if (QuickList.SEPARATOR_ID.equals(id) || myActionsModel.getElementIndex(id) == -1) {
      myActionsModel.add(id);
    }
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private static final class MyCollectionListModel extends CollectionListModel<String> {
    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
      String element = getElementAt(oldIndex);
      remove(oldIndex);
      add(newIndex, element);
    }
  }

  private static final class MyListCellRenderer extends DefaultListCellRenderer {
    @Override
    public @NotNull Component getListCellRendererComponent(@NotNull JList list, Object value, int index, boolean selected, boolean focused) {
      super.getListCellRendererComponent(list, value, index, selected, focused);
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
          icon = null; // AllIcons.Actions.QuickList;
        }
        setIcon(ActionsTree.getEvenIcon(icon));
      }

      return this;
    }

    @Override
    public Dimension getPreferredSize() {
      return UIUtil.updateListRowHeight(super.getPreferredSize());
    }
  }
}
