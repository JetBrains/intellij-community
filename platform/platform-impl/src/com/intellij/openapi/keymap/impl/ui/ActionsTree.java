/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Set;

public class ActionsTree {
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;
  private static final Icon OPEN_ICON = new DefaultTreeCellRenderer().getOpenIcon();
  private static final Icon CLOSE_ICON = AllIcons.Nodes.Folder;

  private final JTree myTree;
  private DefaultMutableTreeNode myRoot;
  private final JScrollPane myComponent;
  private Keymap myKeymap;
  private Group myMainGroup = new Group("", null, null);
  private boolean myShowBoundActions = Registry.is("keymap.show.alias.actions");

  @NonNls
  private static final String ROOT = "ROOT";

  private String myFilter = null;

  public ActionsTree() {
    myRoot = new DefaultMutableTreeNode(ROOT);

    myTree = new Tree(new MyModel(myRoot)) {
      @Override
      public void paint(Graphics g) {
        super.paint(g);
        Rectangle visibleRect = getVisibleRect();
        Rectangle clip = g.getClipBounds();
        for (int row = 0; row < getRowCount(); row++) {
          Rectangle rowBounds = getRowBounds(row);
          rowBounds.x = 0;
          rowBounds.width = Integer.MAX_VALUE;

          if (rowBounds.intersects(clip)) {
            Object node = getPathForRow(row).getLastPathComponent();

            if (node instanceof DefaultMutableTreeNode) {
              Object data = ((DefaultMutableTreeNode)node).getUserObject();
              Rectangle fullRowRect = new Rectangle(visibleRect.x, rowBounds.y, visibleRect.width, rowBounds.height);
              paintRowData(this, data, fullRowRect, (Graphics2D)g);
            }
          }
        }
        
      }
    };
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    myTree.putClientProperty(WideSelectionTreeUI.STRIPED_CLIENT_PROPERTY, Boolean.TRUE);
    myTree.setCellRenderer(new KeymapsRenderer());

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myComponent = ScrollPaneFactory.createScrollPane(myTree,
                                                     ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void addTreeSelectionListener(TreeSelectionListener l) {
    myTree.getSelectionModel().addTreeSelectionListener(l);
  }

  @Nullable
  private Object getSelectedObject() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) return null;
    return ((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject();
  }

  @Nullable
  public String getSelectedActionId() {
    Object userObject = getSelectedObject();
    if (userObject instanceof String) return (String)userObject;
    if (userObject instanceof QuickList) return ((QuickList)userObject).getActionId();
    return null;
  }

  @Nullable
  public QuickList getSelectedQuickList() {
    Object userObject = getSelectedObject();
    if (!(userObject instanceof QuickList)) return null;
    return (QuickList)userObject;
  }

  public void reset(Keymap keymap, final QuickList[] allQuickLists) {
    reset(keymap, allQuickLists, myFilter, null);
  }

  public Group getMainGroup() {
    return myMainGroup;
  }

  public JTree getTree(){
    return myTree;
  }

  public void filter(final String filter, final QuickList[] currentQuickListIds) {
    myFilter = filter;
    reset(myKeymap, currentQuickListIds, filter, null);
  }

  private void reset(final Keymap keymap, final QuickList[] allQuickLists, String filter, @Nullable KeyboardShortcut shortcut) {
    myKeymap = keymap;

    final PathsKeeper pathsKeeper = new PathsKeeper();
    pathsKeeper.storePaths();

    myRoot.removeAllChildren();

    ActionManager actionManager = ActionManager.getInstance();
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myComponent));
    Group mainGroup = ActionsTreeUtil.createMainGroup(project, myKeymap, allQuickLists, filter, true, filter != null && filter.length() > 0 ?
                                                                                                      ActionsTreeUtil.isActionFiltered(filter, true) :
                                                                                                      (shortcut != null ? ActionsTreeUtil.isActionFiltered(actionManager, myKeymap, shortcut) : null));
    if ((filter != null && filter.length() > 0 || shortcut != null) && mainGroup.initIds().isEmpty()){
      mainGroup = ActionsTreeUtil.createMainGroup(project, myKeymap, allQuickLists, filter, false, filter != null && filter.length() > 0 ?
                                                                                                   ActionsTreeUtil.isActionFiltered(filter, false) :
                                                                                                   ActionsTreeUtil.isActionFiltered(actionManager, myKeymap, shortcut));
    }
    myRoot = ActionsTreeUtil.createNode(mainGroup);
    myMainGroup = mainGroup;
    MyModel model = (MyModel)myTree.getModel();
    model.setRoot(myRoot);
    model.nodeStructureChanged(myRoot);

    pathsKeeper.restorePaths();
  }

  public void filterTree(final KeyboardShortcut keyboardShortcut, final QuickList [] currentQuickListIds) {
    reset(myKeymap, currentQuickListIds, myFilter, keyboardShortcut);
  }

  private class MyModel extends DefaultTreeModel implements TreeTableModel {
    protected MyModel(DefaultMutableTreeNode root) {
      super(root);
    }

    @Override
    public void setTree(JTree tree) {
    }

    public int getColumnCount() {
      return 2;
    }

    public String getColumnName(int column) {
      switch (column) {
        case 0: return KeyMapBundle.message("action.column.name");
        case 1: return KeyMapBundle.message("shortcuts.column.name");
      }
      return "";
    }

    public Object getValueAt(Object value, int column) {
      if (!(value instanceof DefaultMutableTreeNode)) {
        return "???";
      }

      if (column == 0) {
        return value;
      }
      else if (column == 1) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof QuickList) {
          userObject = ((QuickList)userObject).getActionId();
        }

        if (userObject instanceof String) {
          Shortcut[] shortcuts = myKeymap.getShortcuts((String)userObject);
          return KeymapUtil.getShortcutsText(shortcuts);
        }
        else {
          return "";
        }
      }
      else {
        return "???";
      }
    }

    public Object getChild(Object parent, int index) {
      return ((TreeNode)parent).getChildAt(index);
    }

    public int getChildCount(Object parent) {
      return ((TreeNode)parent).getChildCount();
    }

    public Class getColumnClass(int column) {
      if (column == 0) {
        return TreeTableModel.class;
      }
      else {
        return Object.class;
      }
    }

    public boolean isCellEditable(Object node, int column) {
      return column == 0;
    }

    public void setValueAt(Object aValue, Object node, int column) {
    }
  }


  private static boolean isActionChanged(String actionId, Keymap oldKeymap, Keymap newKeymap) {
    if (!newKeymap.canModify()) return false;

    Shortcut[] oldShortcuts = oldKeymap.getShortcuts(actionId);
    Shortcut[] newShortcuts = newKeymap.getShortcuts(actionId);
    return !Comparing.equal(oldShortcuts, newShortcuts);
  }

  private static boolean isGroupChanged(Group group, Keymap oldKeymap, Keymap newKeymap) {
    if (!newKeymap.canModify()) return false;

    ArrayList children = group.getChildren();
    for (Object child : children) {
      if (child instanceof Group) {
        if (isGroupChanged((Group)child, oldKeymap, newKeymap)) {
          return true;
        }
      }
      else if (child instanceof String) {
        String actionId = (String)child;
        if (isActionChanged(actionId, oldKeymap, newKeymap)) {
          return true;
        }
      }
      else if (child instanceof QuickList) {
        String actionId = ((QuickList)child).getActionId();
        if (isActionChanged(actionId, oldKeymap, newKeymap)) {
          return true;
        }
      }
    }
    return false;
  }

  public void selectAction(String actionId) {
    final JTree tree = myTree;

    String path = myMainGroup.getActionQualifiedPath(actionId);
    if (path == null) {
      return;
    }
    final DefaultMutableTreeNode node = getNodeForPath(path);
    if (node == null) {
      return;
    }

    TreeUtil.selectInTree(node, true, tree);
  }

  @Nullable
  private DefaultMutableTreeNode getNodeForPath(String path) {
    Enumeration enumeration = ((DefaultMutableTreeNode)myTree.getModel().getRoot()).preorderEnumeration();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
      if (Comparing.equal(getPath(node), path)) {
        return node;
      }
    }
    return null;
  }

  private ArrayList<DefaultMutableTreeNode> getNodesByPaths(ArrayList<String> paths){
    final ArrayList<DefaultMutableTreeNode> result = new ArrayList<DefaultMutableTreeNode>();
    Enumeration enumeration = ((DefaultMutableTreeNode)myTree.getModel().getRoot()).preorderEnumeration();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
      final String path = getPath(node);
      if (paths.contains(path)) {
        result.add(node);
      }
    }
    return result;
  }

  @Nullable
  private String getPath(DefaultMutableTreeNode node) {
    final Object userObject = node.getUserObject();
    if (userObject instanceof String) {
      String actionId = (String)userObject;

      final TreeNode parent = node.getParent();
      if (parent instanceof DefaultMutableTreeNode) {
        final Object object = ((DefaultMutableTreeNode)parent).getUserObject();
        if (object instanceof Group) {
          return ((Group)object).getActionQualifiedPath(actionId);
        }
      }

      return myMainGroup.getActionQualifiedPath(actionId);
    }
    if (userObject instanceof Group) {
      return ((Group)userObject).getQualifiedPath();
    }
    if (userObject instanceof QuickList) {
      return ((QuickList)userObject).getDisplayName();
    }
    return null;
  }

  public static Icon getEvenIcon(Icon icon) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    if (icon != null && icon.getIconHeight() <= EMPTY_ICON.getIconHeight() && icon.getIconWidth() <= EMPTY_ICON.getIconWidth()) {
      layeredIcon
        .setIcon(icon, 1, (-icon.getIconWidth() + EMPTY_ICON.getIconWidth()) / 2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight()) / 2);
    }
    return layeredIcon;
  }

  private class PathsKeeper {
    private ArrayList<String> myPathsToExpand;
    private ArrayList<String> mySelectionPaths;

    public void storePaths() {
      myPathsToExpand = new ArrayList<String>();
      mySelectionPaths = new ArrayList<String>();

      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();

      TreePath path = new TreePath(root.getPath());
      if (myTree.isPathSelected(path)){
        addPathToList(root, mySelectionPaths);
      }
      if (myTree.isExpanded(path) || root.getChildCount() == 0){
        addPathToList(root, myPathsToExpand);
        _storePaths(root);
      }
    }

    private void addPathToList(DefaultMutableTreeNode root, ArrayList<String> list) {
      String path = getPath(root);
      if (!StringUtil.isEmpty(path)) {
        list.add(path);
      }
    }

    private void _storePaths(DefaultMutableTreeNode root) {
      ArrayList<TreeNode> childNodes = childrenToArray(root);
      for (final Object childNode1 : childNodes) {
        DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNode1;
        TreePath path = new TreePath(childNode.getPath());
        if (myTree.isPathSelected(path)) {
          addPathToList(childNode, mySelectionPaths);
        }
        if ((myTree.isExpanded(path) || childNode.getChildCount() == 0) && !childNode.isLeaf()) {
          addPathToList(childNode, myPathsToExpand);
          _storePaths(childNode);
        }
      }
    }

    public void restorePaths() {
      final ArrayList<DefaultMutableTreeNode> nodesToExpand = getNodesByPaths(myPathsToExpand);
      for (DefaultMutableTreeNode node : nodesToExpand) {
        myTree.expandPath(new TreePath(node.getPath()));
      }

      if (myTree.getSelectionModel().getSelectionCount() == 0) {
        final ArrayList<DefaultMutableTreeNode> nodesToSelect = getNodesByPaths(mySelectionPaths);
        if (!nodesToSelect.isEmpty()) {
          for (DefaultMutableTreeNode node : nodesToSelect) {
            TreeUtil.selectInTree(node, false, myTree);
          }
        }
        else {
          myTree.setSelectionRow(0);
        }
      }
    }


    private ArrayList<TreeNode> childrenToArray(DefaultMutableTreeNode node) {
      ArrayList<TreeNode> arrayList = new ArrayList<TreeNode>();
      for(int i = 0; i < node.getChildCount(); i++){
        arrayList.add(node.getChildAt(i));
      }
      return arrayList;
    }
  }

  private class KeymapsRenderer extends ColoredTreeCellRenderer {
    // Make sure that the text rendered by this method is 'searchable' via com.intellij.openapi.keymap.impl.ui.ActionsTree.filter method.
    public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      final boolean showIcons = UISettings.getInstance().SHOW_ICONS_IN_MENUS;
      Keymap originalKeymap = myKeymap != null ? myKeymap.getParent() : null;
      Icon icon = null;
      String text;
      boolean bound = false;

      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        boolean changed;
        if (userObject instanceof Group) {
          Group group = (Group)userObject;
          text = group.getName();

          changed = originalKeymap != null && isGroupChanged(group, originalKeymap, myKeymap);
          icon = group.getIcon();
          if (icon == null){
            icon = CLOSE_ICON;
          }
        }
        else if (userObject instanceof String) {
          String actionId = (String)userObject;
          bound = myShowBoundActions && ((KeymapImpl)myKeymap).isActionBound(actionId);
          AnAction action = ActionManager.getInstance().getActionOrStub(actionId);
          if (action != null) {
            text = action.getTemplatePresentation().getText();
            if (text == null || text.length() == 0) { //fill dynamic presentation gaps
              text = actionId;
            }
            Icon actionIcon = action.getTemplatePresentation().getIcon();
            if (actionIcon != null) {
              icon = actionIcon;
            }
          }
          else {
            text = actionId;
          }
          changed = originalKeymap != null && isActionChanged(actionId, originalKeymap, myKeymap);
        }
        else if (userObject instanceof QuickList) {
          QuickList list = (QuickList)userObject;
          icon = AllIcons.Actions.QuickList;
          text = list.getDisplayName();

          changed = originalKeymap != null && isActionChanged(list.getActionId(), originalKeymap, myKeymap);
        }
        else if (userObject instanceof Separator) {
          // TODO[vova,anton]: beautify
          changed = false;
          text = "-------------";
        }
        else {
          throw new IllegalArgumentException("unknown userObject: " + userObject);
        }

        if (showIcons) {
          setIcon(ActionsTree.getEvenIcon(icon));
        }

        Color foreground;
        if (selected) {
          foreground = UIUtil.getTreeSelectionForeground();
        }
        else {
          if (changed) {
            foreground = PlatformColors.BLUE;
          }
          else {
            foreground = UIUtil.getTreeForeground();
          }

          if (bound) {
            foreground = JBColor.MAGENTA;
          }
        }
        SearchUtil.appendFragments(myFilter, text, Font.PLAIN, foreground,
                                   selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground(), this);
      }
    }
  }
  
  private void paintRowData(Tree tree, Object data, Rectangle bounds, Graphics2D g) {
    Shortcut[] shortcuts = null;
    Set<String> abbreviations = null;
    if (data instanceof String) {
      final String actionId = (String)data;
      shortcuts = myKeymap.getShortcuts(actionId);
      abbreviations = AbbreviationManager.getInstance().getAbbreviations(actionId);
    }
    else if (data instanceof QuickList) {
      shortcuts = myKeymap.getShortcuts(((QuickList)data).getActionId());
    }

    final GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

    int totalWidth = 0;
    final FontMetrics metrics = tree.getFontMetrics(tree.getFont());
    if (shortcuts != null && shortcuts.length > 0) {
      for (Shortcut shortcut : shortcuts) {
        totalWidth += metrics.stringWidth(KeymapUtil.getShortcutText(shortcut));
        totalWidth += 10;
      }
      totalWidth -= 5;

      int x = bounds.x + bounds.width - totalWidth;
      int fontHeight = (int)metrics.getMaxCharBounds(g).getHeight();

      Color c1 = new Color(234, 200, 162);
      Color c2 = new Color(208, 200, 66);

      g.translate(0, bounds.y - 1);
      
      for (Shortcut shortcut : shortcuts) {
        int width = metrics.stringWidth(KeymapUtil.getShortcutText(shortcut));
        UIUtil.drawSearchMatch(g, x, x + width, bounds.height, c1, c2);
        g.setColor(Gray._50);
        g.drawString(KeymapUtil.getShortcutText(shortcut), x, fontHeight);

        x += width;
        x += 10;
      }
      g.translate(0, -bounds.y + 1);
    }
    if (Registry.is("actionSystem.enableAbbreviations") && abbreviations != null && abbreviations.size() > 0) {
      for (String abbreviation : abbreviations) {
        totalWidth += metrics.stringWidth(abbreviation);
        totalWidth += 10;
      }
      totalWidth -= 5;

      int x = bounds.x + bounds.width - totalWidth;
      int fontHeight = (int)metrics.getMaxCharBounds(g).getHeight();

      Color c1 = new Color(206, 234, 176);
      Color c2 = new Color(126, 208, 82);

      g.translate(0, bounds.y - 1);

      for (String abbreviation : abbreviations) {
        int width = metrics.stringWidth(abbreviation);
        UIUtil.drawSearchMatch(g, x, x + width, bounds.height, c1, c2);
        g.setColor(Gray._50);
        g.drawString(abbreviation, x, fontHeight);

        x += width;
        x += 10;
      }
      g.translate(0, -bounds.y + 1);
    }

    config.restore();
  }
}
