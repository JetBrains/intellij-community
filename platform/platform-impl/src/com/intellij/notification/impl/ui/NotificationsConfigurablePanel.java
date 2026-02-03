// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.impl.NotificationParentGroup;
import com.intellij.notification.impl.NotificationParentGroupBean;
import com.intellij.notification.impl.NotificationSettings;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.tree.IndexTreePathState;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class NotificationsConfigurablePanel {

  private static final class NotificationsTreeTable {
    private static final int ID_COLUMN = 0;
    private static final int DISPLAY_TYPE_COLUMN = 1;
    private static final int LOG_COLUMN = 2;
    private static final int READ_ALOUD_COLUMN = 3;
  }

  public static final class NotificationsTreeTableModel extends DefaultTreeModel implements TreeTableModel {
    private final List<NotificationSettingsWrapper> mySettings = new ArrayList<>();
    private JTree myTree;

    NotificationsTreeTableModel() {
      super(null);

      List<DefaultMutableTreeNode> rootChildren = new ArrayList<>();

      Map<NotificationParentGroupBean, List<DefaultMutableTreeNode>> parentChildrenTable = new HashMap<>();
      for (NotificationSettings setting : NotificationsConfigurationImpl.getInstanceImpl().getAllSettings()) {
        NotificationSettingsWrapper wrapper = new NotificationSettingsWrapper(setting);
        mySettings.add(wrapper);

        NotificationParentGroupBean parentGroup = NotificationParentGroup.findParent(setting);
        DefaultMutableTreeNode treeNode = new DefaultMutableTreeNode(wrapper, false);
        if (parentGroup == null) {
          rootChildren.add(treeNode);
        }
        else {
          wrapper.setTitle(NotificationParentGroup.getReplaceTitle(wrapper.getGroupId()));
          if (wrapper.getTitle() == null && parentGroup.titlePrefix != null) {
            wrapper.setTitle(StringUtil.substringAfter(wrapper.getGroupId(), parentGroup.titlePrefix));
          }

          List<DefaultMutableTreeNode> children = parentChildrenTable.get(parentGroup);
          if (children == null) {
            parentChildrenTable.put(parentGroup, children = new ArrayList<>());
          }
          children.add(treeNode);
        }
      }

      for (NotificationParentGroupBean parentGroup : NotificationParentGroup.getParents()) {
        if (parentGroup.parentId == null) {
          DefaultMutableTreeNode node = new DefaultMutableTreeNode(parentGroup);
          addParentGroup(parentGroup, node, parentChildrenTable);
          rootChildren.add(node);
        }
      }

      rootChildren.sort((node1, node2) -> {
        Object object1 = node1.getUserObject();
        Object object2 = node2.getUserObject();
        if (object1 instanceof NotificationParentGroupBean) {
          if (object2 instanceof NotificationParentGroupBean) {
            return object1.toString().compareTo(object2.toString());
          }
          return -1;
        }
        if (object2 instanceof NotificationSettingsWrapper) {
          return ((NotificationSettingsWrapper)object1).getGroupId().compareTo(((NotificationSettingsWrapper)object2).getGroupId());
        }
        return 1;
      });

      DefaultMutableTreeNode root = new DefaultMutableTreeNode();
      for (DefaultMutableTreeNode child : rootChildren) {
        root.add(child);
      }
      setRoot(root);
    }

    private static void addParentGroup(@NotNull NotificationParentGroupBean parent,
                                       @NotNull DefaultMutableTreeNode node,
                                       @NotNull Map<NotificationParentGroupBean, List<DefaultMutableTreeNode>> parentChildrenTable) {
      for (NotificationParentGroupBean child : NotificationParentGroup.getChildren(parent)) {
        DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
        addParentGroup(child, childNode, parentChildrenTable);
        node.add(childNode);
      }

      List<DefaultMutableTreeNode> nodes = parentChildrenTable.get(parent);
      if (nodes != null) {
        for (DefaultMutableTreeNode childNode : nodes) {
          node.add(childNode);
        }
      }
    }

    @Override
    public int getColumnCount() {
      return SystemInfo.isMac ? 4 : 3;
    }

    @Override
    public String getColumnName(int column) {
      return IdeBundle.message(switch (column) {
        case NotificationsTreeTable.ID_COLUMN -> "notifications.configurable.column.group";
        case NotificationsTreeTable.LOG_COLUMN -> "notifications.configurable.column.log";
        case NotificationsTreeTable.READ_ALOUD_COLUMN -> "notifications.configurable.column.read.aloud";
        default -> "notifications.configurable.column.popup";
      });
    }

    @Override
    public Class getColumnClass(int column) {
      if (NotificationsTreeTable.DISPLAY_TYPE_COLUMN == column) {
        return NotificationDisplayType.class;
      }
      if (NotificationsTreeTable.LOG_COLUMN == column) {
        return Boolean.class;
      }
      if (NotificationsTreeTable.READ_ALOUD_COLUMN == column) {
        return Boolean.class;
      }

      return TreeTableModel.class;
    }

    @Override
    public boolean isCellEditable(Object node, int column) {
      return column > 0 && ((DefaultMutableTreeNode)node).getUserObject() instanceof NotificationSettingsWrapper;
    }

    @Override
    public Object getValueAt(Object node, int column) {
      if (column == 0) {
        return node;
      }

      Object object = ((DefaultMutableTreeNode)node).getUserObject();
      if (object instanceof NotificationParentGroupBean) {
        return null;
      }

      NotificationSettingsWrapper wrapper = (NotificationSettingsWrapper)object;
      return switch (column) {
        case NotificationsTreeTable.LOG_COLUMN -> wrapper.isShouldLog();
        case NotificationsTreeTable.READ_ALOUD_COLUMN -> wrapper.isShouldReadAloud();
        //case NotificationsTreeTable.DISPLAY_TYPE_COLUMN,
        default -> wrapper.getDisplayType();
      };
    }

    @Override
    public void setValueAt(Object value, Object node, int column) {
      NotificationSettingsWrapper wrapper = (NotificationSettingsWrapper)((DefaultMutableTreeNode)node).getUserObject();

      switch (column) {
        case NotificationsTreeTable.DISPLAY_TYPE_COLUMN -> wrapper.setDisplayType((NotificationDisplayType)value);
        case NotificationsTreeTable.LOG_COLUMN -> wrapper.setShouldLog((Boolean)value);
        case NotificationsTreeTable.READ_ALOUD_COLUMN -> wrapper.setShouldReadAloud((Boolean)value);
      }
    }

    @Override
    public void setTree(JTree tree) {
      myTree = tree;
      tree.setRootVisible(false);
    }

    public TreePath removeRow(int row) {
      Pair<DefaultMutableTreeNode, Object> rowValue = getRowValue(row);
      if (rowValue.second instanceof NotificationSettingsWrapper) {
        ((NotificationSettingsWrapper)rowValue.second).remove();
      }
      else {
        removeChildSettings(rowValue.first);
      }
      return removeNode(rowValue.first);
    }

    private static void removeChildSettings(DefaultMutableTreeNode node) {
      int count = node.getChildCount();
      for (int i = 0; i < count; i++) {
        DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
        Object object = child.getUserObject();
        if (object instanceof NotificationSettingsWrapper) {
          ((NotificationSettingsWrapper)object).remove();
        }
        else {
          removeChildSettings(child);
        }
      }
    }

    private TreePath removeNode(DefaultMutableTreeNode node) {
      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
      if (parent != null) {
        IndexTreePathState state = new IndexTreePathState(TreeUtil.getPathFromRoot(node));
        removeNodeFromParent(node);
        if (parent.isLeaf()) {
          return removeNode(parent);
        }
        return state.getRestoredPath();
      }
      return null;
    }

    public Pair<DefaultMutableTreeNode, Object> getRowValue(int row) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getPathForRow(row).getLastPathComponent();
      return Pair.create(node, node.getUserObject());
    }

    public List<NotificationSettingsWrapper> getAllSettings() {
      return mySettings;
    }
  }
}
