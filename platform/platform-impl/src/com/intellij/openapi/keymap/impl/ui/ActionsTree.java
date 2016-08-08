/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.ui.*;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;

public class ActionsTree {
  private static final Logger LOG = Logger.getInstance(ActionsTree.class);
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;
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

  private boolean myPaintInternalInfo;
  private final Map<String, String> myPluginNames = ActionsTreeUtil.createPluginActionsMap();

  public ActionsTree() {
    myRoot = new DefaultMutableTreeNode(ROOT);

    myTree = new Tree(new MyModel(myRoot)) {
      @Override
      public void paint(Graphics g) {
        super.paint(g);
        Rectangle visibleRect = getVisibleRect();
        Insets insets = getInsets();
        if (insets != null && insets.right > 0) {
          visibleRect.width -= JBUI.scale(9);
        }
        Rectangle clip = g.getClipBounds();
        for (int row = 0; row < getRowCount(); row++) {
          Rectangle rowBounds = getRowBounds(row);
          rowBounds.x = 0;
          rowBounds.width = Integer.MAX_VALUE;

          if (rowBounds.intersects(clip)) {
            Object node = getPathForRow(row).getLastPathComponent();
            if (node instanceof DefaultMutableTreeNode) {
              Object data = ((DefaultMutableTreeNode)node).getUserObject();
              if (!(data instanceof Hyperlink)) {
                Rectangle fullRowRect = new Rectangle(visibleRect.x, rowBounds.y, visibleRect.width, rowBounds.height);
                paintRowData(this, data, fullRowRect, (Graphics2D)g);
              }
            }
          }
        }
      }

      @Override
      public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          String path = ActionsTree.this.getPath((DefaultMutableTreeNode)value);
          return StringUtil.notNullize(path);
        }
        return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
      }
    };
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    myTree.putClientProperty(WideSelectionTreeUI.STRIPED_CLIENT_PROPERTY, Boolean.TRUE);
    myTree.setCellRenderer(new KeymapsRenderer());
    new TreeLinkMouseListener(new KeymapsRenderer()) {
      @Override
      protected boolean doCacheLastNode() {
        return false;
      }

      @Override
      protected void handleTagClick(@Nullable Object tag, @NotNull MouseEvent event) {
        if (tag instanceof Hyperlink) {
          ((Hyperlink)tag).onClick(event);
        }
      }
    }.installOn(myTree);

    myTree.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        String description = getDescription(e);
        ActionMenu.showDescriptionInStatusBar(description != null, myTree, description);
      }

      @Nullable
      private String getDescription(@NotNull MouseEvent e) {
        TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
        DefaultMutableTreeNode node = path == null ? null : (DefaultMutableTreeNode)path.getLastPathComponent();
        Object userObject = node == null ? null : node.getUserObject();
        if (!(userObject instanceof String)) {
          return null;
        }

        AnAction action = ActionManager.getInstance().getActionOrStub((String)userObject);
        return action == null ? null : action.getTemplatePresentation().getDescription();
      }
    });

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    if (ApplicationManager.getApplication().isInternal()) {
      new HeldDownKeyListener() {
        @Override
        protected void heldKeyTriggered(JComponent component, boolean pressed) {
          myPaintInternalInfo = pressed;
          // an easy way to repaint the tree
          ((Tree)component).setCellRenderer(new KeymapsRenderer());
        }
      }.installOn(myTree);
    }

    myComponent = ScrollPaneFactory.createScrollPane(myTree,
                                                     ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
  }

  // silently replace current map
  void setKeymap(@NotNull Keymap keymap) {
    myKeymap = keymap;
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
    if (userObject instanceof Group) return ((Group)userObject).getId();
    return null;
  }

  @Nullable
  public QuickList getSelectedQuickList() {
    Object userObject = getSelectedObject();
    if (!(userObject instanceof QuickList)) return null;
    return (QuickList)userObject;
  }

  public void reset(@NotNull Keymap keymap, @NotNull QuickList[] allQuickLists) {
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

  private void reset(@NotNull Keymap keymap, @NotNull QuickList[] allQuickLists, String filter, @Nullable Shortcut shortcut) {
    myKeymap = keymap;

    final PathsKeeper pathsKeeper = new PathsKeeper();
    pathsKeeper.storePaths();

    myRoot.removeAllChildren();

    ActionManager actionManager = ActionManager.getInstance();
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myComponent));
    Group mainGroup = ActionsTreeUtil.createMainGroup(project, keymap, allQuickLists, filter, true,
                                                      ActionsTreeUtil.isActionFiltered(actionManager, keymap, shortcut, filter, true));
    if ((filter != null && filter.length() > 0 || shortcut != null) && mainGroup.initIds().isEmpty()){
      mainGroup = ActionsTreeUtil.createMainGroup(project, keymap, allQuickLists, filter, false,
                                                  ActionsTreeUtil.isActionFiltered(actionManager, keymap, shortcut, filter, false));
    }
    myRoot = ActionsTreeUtil.createNode(mainGroup);
    myMainGroup = mainGroup;
    MyModel model = (MyModel)myTree.getModel();
    model.setRoot(myRoot);
    model.nodeStructureChanged(myRoot);

    pathsKeeper.restorePaths();
  }

  public void filterTree(Shortcut shortcut, QuickList[] currentQuickListIds) {
    reset(myKeymap, currentQuickListIds, myFilter, shortcut);
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
        return userObject instanceof String ? KeymapUtil.getShortcutsText(myKeymap.getShortcuts((String)userObject)) : "";
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

    return isActionChanged(group.getId(), oldKeymap, newKeymap);
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
    final ArrayList<DefaultMutableTreeNode> result = new ArrayList<>();
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
      return ((QuickList)userObject).getName();
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
      myPathsToExpand = new ArrayList<>();
      mySelectionPaths = new ArrayList<>();

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
      ArrayList<TreeNode> arrayList = new ArrayList<>();
      for(int i = 0; i < node.getChildCount(); i++){
        arrayList.add(node.getChildAt(i));
      }
      return arrayList;
    }
  }

  private class KeymapsRenderer extends ColoredTreeCellRenderer {

    private final MyColoredTreeCellRenderer myLink = new MyColoredTreeCellRenderer();
    private boolean myHaveLink;
    private int myLinkOffset;
    private int myLinkWidth;
    private int myRow;

    // Make sure that the text rendered by this method is 'searchable' via com.intellij.openapi.keymap.impl.ui.ActionsTree.filter method.
    @Override
    public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      myRow = row;
      myHaveLink = false;
      myLink.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      final boolean showIcons = UISettings.getInstance().SHOW_ICONS_IN_MENUS;
      Keymap originalKeymap = myKeymap != null ? myKeymap.getParent() : null;
      Icon icon = null;
      String text;
      String actionId = null;
      boolean bound = false;
      setToolTipText(null);

      if (value instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        Object userObject = node.getUserObject();
        boolean changed;
        if (userObject instanceof Group) {
          Group group = (Group)userObject;
          actionId = group.getId();
          text = group.getName();

          changed = originalKeymap != null && isGroupChanged(group, originalKeymap, myKeymap);
          icon = group.getIcon();
          if (icon == null){
            icon = CLOSE_ICON;
          }
        }
        else if (userObject instanceof String) {
          actionId = (String)userObject;
          bound = myShowBoundActions && ((KeymapImpl)myKeymap).isActionBound(actionId);
          AnAction action = ActionManager.getInstance().getAction(actionId);
          if (action != null) {
            text = action.getTemplatePresentation().getText();
            if (text == null || text.length() == 0) { //fill dynamic presentation gaps
              text = actionId;
            }
            Icon actionIcon = action.getTemplatePresentation().getIcon();
            if (actionIcon != null) {
              icon = actionIcon;
            }
            setToolTipText(action.getTemplatePresentation().getDescription());
          }
          else {
            text = actionId;
          }
          changed = originalKeymap != null && isActionChanged(actionId, originalKeymap, myKeymap);
        }
        else if (userObject instanceof QuickList) {
          QuickList list = (QuickList)userObject;
          icon = AllIcons.Actions.QuickList;
          text = list.getName();

          changed = originalKeymap != null && isActionChanged(list.getActionId(), originalKeymap, myKeymap);
        }
        else if (userObject instanceof Separator) {
          // TODO[vova,anton]: beautify
          changed = false;
          text = "-------------";
        }
        else if (userObject instanceof Hyperlink) {
          getIpad().right = 0;
          myLink.getIpad().left = 0;
          myHaveLink = true;
          Hyperlink link = (Hyperlink)userObject;
          changed = false;
          text = "";
          append(link.getLinkText(), link.getTextAttributes(), link);
          icon = link.getIcon();
          setIcon(getEvenIcon(link.getIcon()));
          Rectangle treeVisibleRect = tree.getVisibleRect();
          TreePath path = tree.getPathForRow(row);
          int rowX = path != null ? getRowX((BasicTreeUI)tree.getUI(), row, path.getPathCount() - 1) : 0;
          setupLinkDimensions(treeVisibleRect, rowX);
        }
        else {
          throw new IllegalArgumentException("unknown userObject: " + userObject);
        }

        if (showIcons) {
          setIcon(getEvenIcon(icon));
        }

        Color foreground;
        if (selected) {
          foreground = UIUtil.getTreeForeground(true, hasFocus);
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
        if (!myHaveLink) {
          Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
          SearchUtil.appendFragments(myFilter, text, SimpleTextAttributes.STYLE_PLAIN, foreground, background, this);
          if (actionId != null && myPaintInternalInfo) {
            String pluginName = myPluginNames.get(actionId);
            if (pluginName != null) {
              Group parentGroup = (Group)((DefaultMutableTreeNode)node.getParent()).getUserObject();
              if (pluginName.equals(parentGroup.getName())) pluginName = null;
            }
            append("   ");
            append(pluginName != null ? actionId +" (" + pluginName + ")" : actionId, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
          }
        }
      }
      putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, myHaveLink);
    }

    private void setupLinkDimensions(Rectangle treeVisibleRect, int rowX) {
      Dimension linkSize = myLink.getPreferredSize();
      myLinkWidth = linkSize.width;
      myLinkOffset = Math.min(super.getPreferredSize().width - 1, treeVisibleRect.x + treeVisibleRect.width - myLinkWidth - rowX);
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
      if (tag instanceof Hyperlink) {
        myHaveLink = true;
        myLink.append(fragment, attributes, tag);
      }
      else {
        super.append(fragment, attributes, tag);
      }
    }

    @Override
    protected void doPaint(Graphics2D g) {
      if (myHaveLink) {
        Graphics2D textGraphics = (Graphics2D)g.create(0, 0, myLinkOffset, g.getClipBounds().height);
        try {
          super.doPaint(textGraphics);
        }
        finally {
          textGraphics.dispose();
        }
        g.translate(myLinkOffset, 0);
        myLink.setHeight(getHeight());
        myLink.doPaint(g);
        g.translate(-myLinkOffset, 0);
      }
      else {
        super.doPaint(g);
      }
    }

    @NotNull
    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      if (myHaveLink) {
        size.width += myLinkWidth;
      }
      return size;
    }

    @Nullable
    @Override
    public Object getFragmentTagAt(int x) {
      if (myHaveLink) {
        return myLink.getFragmentTagAt(x - myLinkOffset);
      }
      return super.getFragmentTagAt(x);
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleKeymapsRenderer();
      }
      return accessibleContext;
    }

    protected class AccessibleKeymapsRenderer extends AccessibleColoredTreeCellRenderer {
      @Override
      public String getAccessibleName() {
        String name = super.getAccessibleName();

        // Add shortcuts labels if available
        String shortcutName = null;
        TreePath path = myTree.getPathForRow(myRow);
        if (path == null) return "unknown";
        Object node = path.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)node).getUserObject();
          if (!(data instanceof Hyperlink)) {
            Pair<Shortcut[], Set<String>>  rowData = extractRowData(data);
            Shortcut[] shortcuts = rowData.first;
            if (shortcuts != null) {
              StringBuilder sb = new StringBuilder();
              for (Shortcut shortcut : shortcuts) {
                if (sb.length() > 0)
                  sb.append(", ");
                sb.append("shortcut: ");
                sb.append(KeymapUtil.getShortcutText(shortcut));
              }
              if (sb.length() > 0) {
                shortcutName = sb.toString();
              }
            }
          }
        }

        return AccessibleContextUtil.combineAccessibleStrings(name, ", ", shortcutName);
      }
    }
  }

  private Pair<Shortcut[], Set<String>> extractRowData(Object data) {
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
    else if (data instanceof Group) {
      shortcuts = myKeymap.getShortcuts(((Group)data).getId());
    }

    return new Pair<>(shortcuts, abbreviations);
  }

  @SuppressWarnings("UseJBColor")
  private void paintRowData(Tree tree, Object data, Rectangle bounds, Graphics2D g) {
    Pair<Shortcut[], Set<String>> rowData = extractRowData(data);
    Shortcut[] shortcuts = rowData.first;
    Set<String> abbreviations = rowData.second;

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

  private static Method ourGetRowXMethod = null;

  private static int getRowX(BasicTreeUI ui, int row, int depth) {
    if (ourGetRowXMethod == null) {
      try {
        ourGetRowXMethod = BasicTreeUI.class.getDeclaredMethod("getRowX", int.class, int.class);
        ourGetRowXMethod.setAccessible(true);
      }
      catch (NoSuchMethodException e) {
        LOG.error(e);
      }
    }
    if (ourGetRowXMethod != null) {
      try {
        return (Integer)ourGetRowXMethod.invoke(ui, row, depth);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return 0;
  }

  private static class MyColoredTreeCellRenderer extends ColoredTreeCellRenderer {
    private int myHeight;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
    }

    @Override
    protected void doPaint(Graphics2D g) {
      super.doPaint(g);
    }

    public void setHeight(int height) {
      myHeight = height;
    }

    @Override
    public int getHeight() {
      return myHeight;
    }
  }
}
