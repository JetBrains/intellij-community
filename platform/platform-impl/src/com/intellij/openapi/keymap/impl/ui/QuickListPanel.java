/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBDefaultTreeCellRenderer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class QuickListPanel {
  private static final Icon EMPTY_ICON = new EmptyIcon(18, 18);

  private static final Icon QUICK_LIST_ICON = IconLoader.getIcon("/actions/quickList.png");
  private JButton myRemoveActionButton;
  private JButton myIncludeActionButton;
  private JButton myMoveActionDownButton;
  private JButton myMoveActionUpButton;
  private JPanel myPanel;
  private JTree myActionsTree;
  private JList myActionsList;
  private JTextField myDisplayName;
  private JTextField myDescription;
  private JButton myAddSeparatorButton;
  private final boolean myEditable;

  public QuickListPanel(QuickList origin, final QuickList[] allQuickLists, Project project) {
    myEditable = !QuickListsManager.getInstance().getSchemesManager().isShared(origin);
    Group rootGroup = ActionsTreeUtil.createMainGroup(project, null, allQuickLists);
    DefaultMutableTreeNode root = ActionsTreeUtil.createNode(rootGroup);
    DefaultTreeModel model = new DefaultTreeModel(root);
    myActionsTree.setModel(model);
    myActionsTree.setCellRenderer(new MyTreeCellRenderer(myActionsTree));

    myActionsList.setModel(new DefaultListModel());
    myActionsList.setCellRenderer(new MyListCellRenderer());

    myActionsTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        update();
      }
    });

    myActionsTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
          includeSelectedAction();
        }
      }
    });

    myActionsList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && !e.isPopupTrigger()) {
          excludeSelectionAction();
        }
      }
    });

    myActionsList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        update();
      }
    });

    myActionsTree.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelectedAction();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myIncludeActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        includeSelectedAction();
      }
    });

    myAddSeparatorButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        addSeparator();
      }
    });

    myActionsList.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelectionAction();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myRemoveActionButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        excludeSelectionAction();
      }
    });

    myMoveActionUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int idx = myActionsList.getSelectedIndex();
        if (idx > 0) {
          DefaultListModel listModel = (DefaultListModel)myActionsList.getModel();
          Object oldValue = listModel.get(idx);
          listModel.removeElementAt(idx);
          listModel.add(--idx, oldValue);
          myActionsList.getSelectionModel().setSelectionInterval(idx, idx);
        }
      }
    });

    myMoveActionDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int idx = myActionsList.getSelectedIndex();
        DefaultListModel listModel = (DefaultListModel)myActionsList.getModel();
        if (idx < listModel.getSize() - 1) {
          Object oldValue = listModel.get(idx);
          listModel.removeElementAt(idx);
          listModel.add(++idx, oldValue);
          myActionsList.getSelectionModel().setSelectionInterval(idx, idx);
        }
      }
    });

    myDisplayName.setText(origin.getDisplayName());
    myDescription.setText(origin.getDescription());

    String[] ids = origin.getActionIds();
    for (String id : ids) {
      includeActionId(id);
    }

    update();
  }

  public void addNameListener(DocumentAdapter adapter){
    myDisplayName.getDocument().addDocumentListener(adapter);
  }

  public void addDescriptionListener(final DocumentAdapter adapter) {
    myDescription.getDocument().addDocumentListener(adapter);
  }

  private void excludeSelectionAction() {
    int[] ids = myActionsList.getSelectedIndices();
    for (int i = ids.length - 1; i >=0; i--) {
      ((DefaultListModel)myActionsList.getModel()).remove(ids[i]);
    }
    update();
  }

  private void includeSelectedAction() {
    String[] ids = getTreeSelectedActionIds();
    for (String id : ids) {
      includeActionId(id);
    }
    DefaultListModel listModel = (DefaultListModel)myActionsList.getModel();
    int size = listModel.getSize();
    ListSelectionModel selectionModel = myActionsList.getSelectionModel();
    if (size > 0) {
      selectionModel.removeIndexInterval(0, size - 1);
    }
    for (String id1 : ids) {
      int idx = listModel.lastIndexOf(id1);
      if (idx >= 0) {
        selectionModel.addSelectionInterval(idx, idx);
      }
    }
    update();
  }

  private void addSeparator() {
    DefaultListModel model = (DefaultListModel)myActionsList.getModel();
    model.addElement(QuickList.SEPARATOR_ID);
    update();
  }

  public JList getActionsList() {
    return myActionsList;
  }

  public String getDescription() {
    return myDescription.getText();
  }

  public String getDisplayName() {
    return myDisplayName.getText();
  }

  private void update() {
    if (myEditable) {
      myIncludeActionButton.setEnabled(getTreeSelectedActionIds().length > 0);
      myRemoveActionButton.setEnabled(myActionsList.getSelectedValues().length > 0);
      boolean enableMove = myActionsList.getSelectedValues().length == 1;
      myMoveActionUpButton.setEnabled(enableMove && myActionsList.getSelectedIndex() > 0);
      myMoveActionDownButton.setEnabled(enableMove && myActionsList.getSelectedIndex() < myActionsList.getModel().getSize() - 1);
    }
    else {
      myIncludeActionButton.setEnabled(false);
      myRemoveActionButton.setEnabled(false);
      myMoveActionUpButton.setEnabled(false);
      myMoveActionDownButton.setEnabled(false);
      myAddSeparatorButton.setEnabled(false);

    }
  }

  private void includeActionId(String id) {
    DefaultListModel model = (DefaultListModel)myActionsList.getModel();
    if (!QuickList.SEPARATOR_ID.equals(id) && model.contains(id)) return;
    model.addElement(id);
  }

  private String[] getTreeSelectedActionIds() {
    TreePath[] paths = myActionsTree.getSelectionPaths();
    if (paths == null) return ArrayUtil.EMPTY_STRING_ARRAY;

    ArrayList<String> actions = new ArrayList<String>();
    for (TreePath path : paths) {
      Object node = path.getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode defNode = (DefaultMutableTreeNode)node;
        Object userObject = defNode.getUserObject();
        if (userObject instanceof String) {
          actions.add((String)userObject);
        }
        else if (userObject instanceof QuickList) {
          actions.add(((QuickList)userObject).getActionId());
        }
      }
    }
    return ArrayUtil.toStringArray(actions);
  }

  public JPanel getPanel() {
    return myPanel;
  }

  private class MyTreeCellRenderer extends JBDefaultTreeCellRenderer {
    private MyTreeCellRenderer(@NotNull JTree tree) {
      super(tree);
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean sel,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      Icon icon = null;
      if (value instanceof DefaultMutableTreeNode) {
        boolean used = false;
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof Group) {
          Group group = (Group)userObject;
          final String name = group.getName();
          setText(name != null ? name : group.getId());
          icon = expanded ? group.getOpenIcon() : group.getIcon();

          if (icon == null) {
            icon = expanded ? getOpenIcon() : getClosedIcon();
          }


        }
        else if (userObject instanceof String) {
          String actionId = (String)userObject;
          used = ((DefaultListModel)myActionsList.getModel()).lastIndexOf(actionId) >= 0;
          AnAction action = ActionManager.getInstance().getAction(actionId);
          String text = action == null ? actionId : action.getTemplatePresentation().getText();
          if (text == null || text.length() == 0) text = actionId;
          setText(text);
          if (action != null) {
            Icon actionIcon = action.getTemplatePresentation().getIcon();
            if (actionIcon != null) {
              icon = actionIcon;
            }
          }
        }
        else if (userObject instanceof QuickList) {
          QuickList list = (QuickList)userObject;
          icon = QUICK_LIST_ICON;
          setText(list.getDisplayName());
          used = ((DefaultListModel)myActionsList.getModel()).lastIndexOf(list.getActionId()) >= 0;
        }
        else if (userObject instanceof Separator) {
          // TODO[vova,anton]: beautify
          setText("-------------");

        }
        else {
          throw new IllegalArgumentException("unknown userObject: " + userObject);
        }

        setIcon(ActionsTree.getEvenIcon(icon));

        if (sel) {
          setForeground(getSelectionForeground(tree));
        }
        else {
          Color foreground = used ? UIUtil.getTextInactiveTextColor() :UIUtil.getTreeForeground();
          setForeground(foreground);
        }
      }
      return this;
    }
  }

  private static class MyListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list,
                                                  Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      Icon icon = null;
      String actionId = (String)value;
      if (QuickList.SEPARATOR_ID.equals(actionId)) {
        // TODO[vova,anton]: beautify
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
        if (actionId.startsWith(QuickList.QUICK_LIST_PREFIX)){
          icon = QUICK_LIST_ICON;
        }
        setIcon(ActionsTree.getEvenIcon(icon));
      }

      return this;
    }
  }
}