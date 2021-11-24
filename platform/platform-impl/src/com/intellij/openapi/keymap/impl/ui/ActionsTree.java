// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.treetable.TreeTableModel;
import com.intellij.util.SmartList;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.PlatformColors;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.*;

public final class ActionsTree {
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;
  private static final Icon CLOSE_ICON = AllIcons.Nodes.Folder;
  private final SimpleTextAttributes GRAY_LINK = new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE, JBColor.gray);

  private final JTree myTree;
  private DefaultMutableTreeNode myRoot;
  private final JScrollPane myComponent;
  private Keymap myKeymap;
  private Group myMainGroup = new Group("", null, null);
  private final boolean myShowBoundActions = Registry.is("keymap.show.alias.actions");

  @NonNls
  private static final String ROOT = "ROOT";

  private String myFilter = null;
  private Condition<? super AnAction> myBaseFilter;

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
          visibleRect.width -= JBUIScale.scale(9);
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
          String path = ActionsTree.this.getPath((DefaultMutableTreeNode)value, true);
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
      @NlsActions.ActionDescription
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
    myComponent = ScrollPaneFactory.createScrollPane(myTree,
                                                     ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                                     ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
  }

  // silently replace current map
  void setKeymap(@NotNull Keymap keymap) {
    myKeymap = keymap;
  }

  public void setBaseFilter(@Nullable Condition<? super AnAction> baseFilter) { myBaseFilter = baseFilter; }

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

  public void reset(@NotNull Keymap keymap, QuickList @NotNull [] allQuickLists) {
    reset(keymap, allQuickLists, myFilter, null);
  }

  public void reset(@NotNull Keymap keymap, QuickList @NotNull [] allQuickLists, @Nullable Shortcut shortcut) {
    reset(keymap, allQuickLists, myFilter, shortcut);
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

  private @Nullable Condition<? super AnAction> combineWithBaseFilter(@Nullable Condition<? super AnAction> actionFilter) {
    if (actionFilter != null)
      return myBaseFilter != null ? Conditions.and(myBaseFilter, actionFilter) : actionFilter;
    return myBaseFilter;
  }

  private void reset(@NotNull Keymap keymap, QuickList @NotNull [] allQuickLists, String filter, @Nullable Shortcut shortcut) {
    myKeymap = keymap;

    final PathsKeeper pathsKeeper = new PathsKeeper();
    pathsKeeper.storePaths();

    myRoot.removeAllChildren();

    ActionManager actionManager = ActionManager.getInstance();
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myComponent));
    Condition<? super AnAction>
      condFilter = combineWithBaseFilter(ActionsTreeUtil.isActionFiltered(actionManager, keymap, shortcut, filter, true));
    Group mainGroup = ActionsTreeUtil.createMainGroup(project, keymap, allQuickLists, filter, true, condFilter);

    if ((filter != null && filter.length() > 0 || shortcut != null) && mainGroup.initIds().isEmpty()) {
      condFilter = combineWithBaseFilter(ActionsTreeUtil.isActionFiltered(actionManager, keymap, shortcut, filter, false));
      mainGroup = ActionsTreeUtil.createMainGroup(project, keymap, allQuickLists, filter, false, condFilter);
    }

    myRoot = ActionsTreeUtil.createNode(mainGroup);
    myMainGroup = mainGroup;
    MyModel model = (MyModel)myTree.getModel();
    model.setRoot(myRoot);
    model.nodeStructureChanged(myRoot);

    pathsKeeper.restorePaths();
    getComponent().repaint();
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

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int column) {
      switch (column) {
        case 0: return KeyMapBundle.message("action.column.name");
        case 1: return KeyMapBundle.message("shortcuts.column.name");
      }
      return "";
    }

    @Override
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

    @Override
    public Object getChild(Object parent, int index) {
      return ((TreeNode)parent).getChildAt(index);
    }

    @Override
    public int getChildCount(Object parent) {
      return ((TreeNode)parent).getChildCount();
    }

    @Override
    public Class getColumnClass(int column) {
      if (column == 0) {
        return TreeTableModel.class;
      }
      else {
        return Object.class;
      }
    }

    @Override
    public boolean isCellEditable(Object node, int column) {
      return column == 0;
    }

    @Override
    public void setValueAt(Object aValue, Object node, int column) {
    }
  }

  public static boolean isShortcutCustomized(@NotNull String actionId, @NotNull Keymap keymap) {
    if (!keymap.canModify()) return false; // keymap is not customized

    Keymap parent = keymap.getParent();
    return parent != null && !Arrays.equals(parent.getShortcuts(actionId), keymap.getShortcuts(actionId));
  }

  private static boolean areGroupShortcutsCustomized(@NotNull Group group, @NotNull Keymap keymap) {
    if (!keymap.canModify()) return false;

    ArrayList children = group.getChildren();
    for (Object child : children) {
      if (child instanceof Group) {
        if (areGroupShortcutsCustomized((Group)child, keymap)) {
          return true;
        }
      }
      else if (child instanceof String) {
        String actionId = (String)child;
        if (isShortcutCustomized(actionId, keymap)) {
          return true;
        }
      }
      else if (child instanceof QuickList) {
        String actionId = ((QuickList)child).getActionId();
        if (isShortcutCustomized(actionId, keymap)) {
          return true;
        }
      }
    }

    return group.getId() != null && isShortcutCustomized(group.getId(), keymap);
  }

  public void selectAction(String actionId) {
    String path = myMainGroup.getActionQualifiedPath(actionId, false);
    String boundId = path == null ? KeymapManagerEx.getInstanceEx().getActionBinding(actionId) : null;
    if (path == null && boundId != null) {
      path = myMainGroup.getActionQualifiedPath(boundId, false);
      if (path == null) {
        return;
      }
    }

    final DefaultMutableTreeNode node = getNodeForPath(path);
    if (node == null) {
      return;
    }

    TreeUtil.selectInTree(node, true, myTree);
  }

  @Nullable
  private DefaultMutableTreeNode getNodeForPath(String path) {
    Enumeration enumeration = ((DefaultMutableTreeNode)myTree.getModel().getRoot()).preorderEnumeration();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
      if (Objects.equals(getPath(node, false), path)) {
        return node;
      }
    }
    return null;
  }

  private List<DefaultMutableTreeNode> getNodesByPaths(List<String> paths) {
    List<DefaultMutableTreeNode> result = new SmartList<>();
    Enumeration enumeration = ((DefaultMutableTreeNode)myTree.getModel().getRoot()).preorderEnumeration();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
      final String path = getPath(node, false);
      if (paths.contains(path)) {
        result.add(node);
      }
    }
    return result;
  }

  @Nullable
  private String getPath(DefaultMutableTreeNode node, boolean presentable) {
    final Object userObject = node.getUserObject();
    if (userObject instanceof String) {
      String actionId = (String)userObject;

      final TreeNode parent = node.getParent();
      if (parent instanceof DefaultMutableTreeNode) {
        final Object object = ((DefaultMutableTreeNode)parent).getUserObject();
        if (object instanceof Group) {
          return ((Group)object).getActionQualifiedPath(actionId, presentable);
        }
      }

      return myMainGroup.getActionQualifiedPath(actionId, presentable);
    }
    if (userObject instanceof Group) {
      return ((Group)userObject).getQualifiedPath(presentable);
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

    private void addPathToList(DefaultMutableTreeNode root, ArrayList<? super String> list) {
      String path = getPath(root, false);
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
      for (DefaultMutableTreeNode node : getNodesByPaths(myPathsToExpand)) {
        myTree.expandPath(new TreePath(node.getPath()));
      }

      if (myTree.getSelectionModel().getSelectionCount() == 0) {
        List<DefaultMutableTreeNode> nodesToSelect = getNodesByPaths(mySelectionPaths);
        if (!nodesToSelect.isEmpty()) {
          for (DefaultMutableTreeNode node : nodesToSelect) {
            TreeUtil.selectNode(myTree, node);
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
      final boolean showIcons = UISettings.getInstance().getShowIconsInMenus();
      Icon icon = null;
      String text;
      @NlsSafe String actionId = null;
      String boundId = null;
      @NlsActions.ActionText
      String boundText = null;
      setToolTipText(null);

      if (value instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        Object userObject = node.getUserObject();
        boolean changed;
        if (userObject instanceof Group) {
          Group group = (Group)userObject;
          actionId = group.getId();
          text = group.getName();

          changed = myKeymap != null && areGroupShortcutsCustomized(group, myKeymap);
          icon = group.getIcon();
          if (icon == null){
            icon = CLOSE_ICON;
          }
        }
        else if (userObject instanceof String) {
          actionId = (String)userObject;
          boundId = ((KeymapImpl)myKeymap).hasShortcutDefined(actionId) ? null : KeymapManagerEx.getInstanceEx().getActionBinding(actionId);
          ActionManager manager = ActionManager.getInstance();
          AnAction action = manager.getAction(actionId);
          text = getActionText(action, actionId);
          if (action != null) {
            Icon actionIcon = action.getTemplatePresentation().getIcon();
            if (actionIcon != null) {
              icon = actionIcon;
            }
            setToolTipText(action.getTemplatePresentation().getDescription());
          }
          boundText = boundId == null ? null : getActionText(manager.getAction(boundId), boundId);
          changed = myKeymap != null && isShortcutCustomized(actionId, myKeymap);
        }
        else if (userObject instanceof QuickList) {
          QuickList list = (QuickList)userObject;
          icon = null; // AllIcons.Actions.QuickList;
          text = list.getName();

          changed = myKeymap != null && isShortcutCustomized(list.getActionId(), myKeymap);
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
          int rowX = TreeUtil.getNodeRowX(tree, row);
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
        }
        if (!myHaveLink) {
          Color background = UIUtil.getTreeBackground(selected, true);
          SearchUtil.appendFragments(myFilter, text, SimpleTextAttributes.STYLE_PLAIN, foreground, background, this);
          if (boundId != null) {
            append(" ");
            append(IdeBundle.message("uses.shortcut.of"), SimpleTextAttributes.GRAY_ATTRIBUTES);
            append(" ");
            ActionHyperlink link = new ActionHyperlink(boundId, boundText);
            append(link.getLinkText(), link.getTextAttributes(), link);
          }
          if (actionId != null && UISettings.getInstance().getShowInplaceCommentsInternal()) {
            @NlsSafe String pluginName = myPluginNames.get(actionId);
            if (pluginName != null) {
              Group parentGroup = (Group)((DefaultMutableTreeNode)node.getParent()).getUserObject();
              if (pluginName.equals(parentGroup.getName())) pluginName = null;
            }
            append("   ");
            append(pluginName != null ? actionId + " (" + pluginName + ")" : actionId, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
          }
        }
      }
      putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, myHaveLink);
    }

    @NlsActions.ActionText
    private String getActionText(@Nullable AnAction action, @NlsSafe String actionId) {
      String text = action == null ? null : action.getTemplatePresentation().getText();
      if (text == null || text.length() == 0) { //fill dynamic presentation gaps
        text = actionId;
      }
      return text;
    }

    private void setupLinkDimensions(Rectangle treeVisibleRect, int rowX) {
      Dimension linkSize = myLink.getPreferredSize();
      myLinkWidth = linkSize.width;
      myLinkOffset = Math.min(super.getPreferredSize().width - 1, treeVisibleRect.x + treeVisibleRect.width - myLinkWidth - rowX);
    }

    @Override
    public void append(@NotNull String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
      if (tag instanceof Hyperlink && !(tag instanceof ActionHyperlink)) {
        myHaveLink = true;
        myLink.append(fragment, attributes, tag);
      }
      else {
        super.append(fragment, attributes, tag);
      }
    }

    @Override
    protected void doPaint(Graphics2D g) {
      if (!myHaveLink) {
        super.doPaint(g);
      }

      UIUtil.useSafely(g.create(0, 0, myLinkOffset, g.getClipBounds().height),
                       textGraphics -> super.doPaint(textGraphics));
      g.translate(myLinkOffset, 0);
      myLink.setHeight(getHeight());
      myLink.doPaint(g);
      g.translate(-myLinkOffset, 0);
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
        @NlsSafe String shortcutName = null;
        TreePath path = myTree.getPathForRow(myRow);
        if (path == null) return KeyMapBundle.message("accessible.name.unknown");
        Object node = path.getLastPathComponent();
        if (node instanceof DefaultMutableTreeNode) {
          Object data = ((DefaultMutableTreeNode)node).getUserObject();
          if (!(data instanceof Hyperlink)) {
            RowData rowData = extractRowData(data);
            Shortcut[] shortcuts = rowData.shortcuts;
            if (shortcuts != null && shortcuts.length > 0) {
              StringBuilder sb = new StringBuilder();
              for (Shortcut shortcut : shortcuts) {
                if (sb.length() > 0)
                  sb.append(", ");
                sb.append(KeyMapBundle.message("accessible.name.shortcut"));
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

    private class ActionHyperlink extends Hyperlink {
      private final String myActionId;

      ActionHyperlink(String actionId, @NlsContexts.LinkLabel String actionText) {
        super(null, actionText, GRAY_LINK);
        myActionId = actionId;
      }

      @Override
      public void onClick(MouseEvent event) {
        selectAction(myActionId);
      }
    }
  }

  @NotNull
  private RowData extractRowData(Object data) {
    String actionId = null;
    if (data instanceof String) {
      actionId = (String)data;
    }
    else if (data instanceof QuickList) {
      actionId = ((QuickList)data).getActionId();
    }
    else if (data instanceof Group) {
      actionId = ((Group)data).getId();
    }
    if (actionId == null) return new RowData(null, null);
    Shortcut[] shortcuts = myKeymap.getShortcuts(actionId);
    Set<String> abbreviations = AbbreviationManager.getInstance().getAbbreviations(actionId);
    return new RowData(shortcuts, abbreviations);
  }

  private static final class RowData {
    public final Shortcut[] shortcuts;
    public final Set<String> abbreviations;

    private RowData(Shortcut[] shortcuts, Set<String> abbreviations) {
      this.shortcuts = shortcuts;
      this.abbreviations = abbreviations;
    }
  }

  @SuppressWarnings("UseJBColor")
  private void paintRowData(Tree tree, Object data, Rectangle bounds, Graphics2D g) {
    RowData rowData = extractRowData(data);
    Shortcut[] shortcuts = rowData.shortcuts;
    Set<String> abbreviations = rowData.abbreviations;

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
    if (abbreviations != null && abbreviations.size() > 0) {
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
