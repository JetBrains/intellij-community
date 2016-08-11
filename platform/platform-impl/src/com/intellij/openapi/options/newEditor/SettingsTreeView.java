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
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.ex.SortedConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.*;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * @author Sergey.Malenkov
 */
final class SettingsTreeView extends JComponent implements Accessible, Disposable, OptionsEditorColleague {
  private static final int ICON_GAP = 5;
  private static final String NODE_ICON = "settings.tree.view.icon";
  private static final Color WRONG_CONTENT = JBColor.RED;
  private static final Color MODIFIED_CONTENT = JBColor.BLUE;
  public static final Color FOREGROUND = new JBColor(Gray.x1A, Gray.xBB);

  final SimpleTree myTree;
  final FilteringTreeBuilder myBuilder;

  private final SettingsFilter myFilter;
  private final MyRoot myRoot;
  private final JScrollPane myScroller;
  private final IdentityHashMap<Configurable, MyNode> myConfigurableToNodeMap = new IdentityHashMap<>();
  private final IdentityHashMap<UnnamedConfigurable, ConfigurableWrapper> myConfigurableToWrapperMap
    = new IdentityHashMap<>();
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("SettingsTreeView", 150, false, this, this, this)
    .setRestartTimerOnAdd(true);

  private Configurable myQueuedConfigurable;
  private boolean myPaintInternalInfo;

  SettingsTreeView(SettingsFilter filter, ConfigurableGroup... groups) {
    myFilter = filter;
    myRoot = new MyRoot(groups);
    myTree = new MyTree();
    myTree.putClientProperty(WideSelectionTreeUI.TREE_TABLE_TREE_KEY, Boolean.TRUE);
    myTree.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    myTree.getInputMap().clear();
    TreeUtil.installActions(myTree);

    myTree.setOpaque(true);

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myTree.setCellRenderer(new MyRenderer());
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.setExpandableItemsEnabled(false);
    RelativeFont.BOLD.install(myTree);

    myTree.setTransferHandler(new TransferHandler() {
      @Nullable
      @Override
      protected Transferable createTransferable(JComponent c) {
        MyNode node = extractNode(myTree.getPathForRow(myTree.getLeadSelectionRow()));
        if (node != null) {
          StringBuilder sb = new StringBuilder("File | Settings");
          for (String name : getPathNames(node)) {
            sb.append(" | ").append(name);
          }
          return new TextTransferable(sb.toString());
        }
        return null;
      }

      @Override
      public int getSourceActions(JComponent c) {
        return COPY;
      }
    });

    myScroller = ScrollPaneFactory.createScrollPane(null, true);
    myScroller.setViewport(new GradientViewport(myTree, JBUI.insetsTop(5), true) {
      private JLabel myHeader;

      @Override
      protected Component getHeader() {
        if (0 == myTree.getY()) {
          return null; // separator is not needed without scrolling
        }
        if (myHeader == null) {
          myHeader = new JLabel();
          myHeader.setForeground(FOREGROUND);
          myHeader.setIconTextGap(ICON_GAP);
          myHeader.setBorder(BorderFactory.createEmptyBorder(1, 10 + getLeftMargin(0), 0, 0));
        }
        myHeader.setFont(myTree.getFont());
        myHeader.setIcon(myTree.getEmptyHandle());
        int height = myHeader.getPreferredSize().height;
        String group = findGroupNameAt(0, height + 3);
        if (group == null || !group.equals(findGroupNameAt(0, 0))) {
          return null; // do not show separator over another group
        }
        myHeader.setText(group);
        return myHeader;
      }
    });
    if (!Registry.is("ide.scroll.new.layout")) {
      myScroller.getVerticalScrollBar().setUI(ButtonlessScrollBarUI.createTransparent());
    }
    if (!Registry.is("ide.scroll.background.auto")) {
      myScroller.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
      myScroller.getViewport().setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
      myScroller.getVerticalScrollBar().setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    }
    add(myScroller);

    myTree.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent e) {
        myBuilder.revalidateTree();
      }

      @Override
      public void componentMoved(ComponentEvent e) {
        myBuilder.revalidateTree();
      }

      @Override
      public void componentShown(ComponentEvent e) {
        myBuilder.revalidateTree();
      }
    });

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent event) {
        MyNode node = extractNode(event.getNewLeadSelectionPath());
        select(node == null ? null : node.myConfigurable);
      }
    });
    if (ApplicationManager.getApplication().isInternal()) {
      new HeldDownKeyListener() {
        @Override
        protected void heldKeyTriggered(JComponent component, boolean pressed) {
          myPaintInternalInfo = pressed;
          SettingsTreeView.this.setMinimumSize(null);
          // an easy way to repaint the tree
          ((Tree)component).setCellRenderer(new MyRenderer());
        }
      }.installOn(myTree);
    }

    myBuilder = new MyBuilder(new SimpleTreeStructure.Impl(myRoot));
    myBuilder.setFilteringMerge(300, null);
    Disposer.register(this, myBuilder);
  }

  @NotNull
  String[] getPathNames(Configurable configurable) {
    return getPathNames(findNode(configurable));
  }

  private static String[] getPathNames(MyNode node) {
    ArrayDeque<String> path = new ArrayDeque<>();
    while (node != null) {
      path.push(node.myDisplayName);
      SimpleNode parent = node.getParent();
      node = parent instanceof MyNode
             ? (MyNode)parent
             : null;
    }
    return ArrayUtil.toStringArray(path);
  }

  static Configurable getConfigurable(SimpleNode node) {
    return node instanceof MyNode
           ? ((MyNode)node).myConfigurable
           : null;
  }

  @Nullable
  MyNode findNode(Configurable configurable) {
    ConfigurableWrapper wrapper = myConfigurableToWrapperMap.get(configurable);
    return myConfigurableToNodeMap.get(wrapper != null ? wrapper : configurable);
  }

  @Nullable
  SearchableConfigurable findConfigurableById(@NotNull String id) {
    for (Configurable configurable : myConfigurableToNodeMap.keySet()) {
      if (configurable instanceof SearchableConfigurable) {
        SearchableConfigurable searchable = (SearchableConfigurable)configurable;
        if (id.equals(searchable.getId())) {
          return searchable;
        }
      }
    }
    return null;
  }

  @Nullable
  <T extends UnnamedConfigurable> T findConfigurable(@NotNull Class<T> type) {
    for (UnnamedConfigurable configurable : myConfigurableToNodeMap.keySet()) {
      if (configurable instanceof ConfigurableWrapper) {
        ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
        configurable = wrapper.getConfigurable();
        myConfigurableToWrapperMap.put(configurable, wrapper);
      }
      if (type.isInstance(configurable)) {
        return type.cast(configurable);
      }
    }
    return null;
  }

  @Nullable
  Project findConfigurableProject(@Nullable Configurable configurable) {
    if (configurable instanceof ConfigurableWrapper) {
      return getProjectFromWrapper((ConfigurableWrapper)configurable);
    }
    return findConfigurableProject(findNode(configurable));
  }

  @Nullable
  private static Project findConfigurableProject(@Nullable MyNode node) {
    if (node != null) {
      Configurable configurable = node.myConfigurable;
      if (configurable instanceof ConfigurableWrapper) {
        return getProjectFromWrapper((ConfigurableWrapper)configurable);
      }
      SimpleNode parent = node.getParent();
      if (parent instanceof MyNode) {
        return findConfigurableProject((MyNode)parent);
      }
    }
    return null;
  }
  
  @Nullable
  private static Project getProjectFromWrapper(@NotNull ConfigurableWrapper wrapper) {
    Configurable.VariableProjectAppLevel wrapped = ConfigurableWrapper.cast(Configurable.VariableProjectAppLevel.class, wrapper);
    if (wrapped != null && !wrapped.isProjectLevel()) {
      return null;
    }
    return wrapper.getExtensionPoint().getProject();
  }

  private static int getLeftMargin(int level) {
    return 3 + level * (11 + ICON_GAP);
  }

  @Nullable
  private String findGroupNameAt(int x, int y) {
    TreePath path = myTree.getClosestPathForLocation(x - myTree.getX(), y - myTree.getY());
    while (path != null) {
      MyNode node = extractNode(path);
      if (node == null) {
        return null;
      }
      if (myRoot == node.getParent()) {
        return node.myDisplayName;
      }
      path = path.getParentPath();
    }
    return null;
  }

  @Nullable
  private static MyNode extractNode(@Nullable Object object) {
    if (object instanceof TreePath) {
      TreePath path = (TreePath)object;
      object = path.getLastPathComponent();
    }
    if (object instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)object;
      object = node.getUserObject();
    }
    if (object instanceof FilteringTreeStructure.FilteringNode) {
      FilteringTreeStructure.FilteringNode node = (FilteringTreeStructure.FilteringNode)object;
      object = node.getDelegate();
    }
    return object instanceof MyNode
           ? (MyNode)object
           : null;
  }

  @Override
  public void doLayout() {
    myScroller.setBounds(0, 0, getWidth(), getHeight());
  }

  void selectFirst() {
    for (ConfigurableGroup eachGroup : myRoot.myGroups) {
      Configurable[] kids = eachGroup.getConfigurables();
      if (kids.length > 0) {
        select(kids[0]);
        return;
      }
    }
  }

  ActionCallback select(@Nullable final Configurable configurable) {
    if (myBuilder.isSelectionBeingAdjusted()) {
      return ActionCallback.REJECTED;
    }
    final ActionCallback callback = new ActionCallback();
    myQueuedConfigurable = configurable;
    myQueue.queue(new Update(this) {
      public void run() {
        if (configurable == myQueuedConfigurable) {
          if (configurable == null) {
            fireSelected(null, callback);
          }
          else {
            myBuilder.getReady(this).doWhenDone(() -> {
              if (configurable != myQueuedConfigurable) return;

              MyNode editorNode = findNode(configurable);
              FilteringTreeStructure.FilteringNode editorUiNode = myBuilder.getVisibleNodeFor(editorNode);
              if (editorUiNode == null) return;

              if (!myBuilder.getSelectedElements().contains(editorUiNode)) {
                myBuilder.select(editorUiNode, () -> fireSelected(configurable, callback));
              }
              else {
                myBuilder.scrollSelectionToVisible(() -> fireSelected(configurable, callback), false);
              }
            });
          }
        }
      }

      @Override
      public void setRejected() {
        super.setRejected();
        callback.setRejected();
      }
    });
    return callback;
  }

  private void fireSelected(Configurable configurable, ActionCallback callback) {
    ConfigurableWrapper wrapper = myConfigurableToWrapperMap.get(configurable);
    myFilter.myContext.fireSelected(wrapper != null ? wrapper : configurable, this).doWhenProcessed(callback.createSetDoneRunnable());
  }

  @Override
  public void dispose() {
    myQueuedConfigurable = null;
  }

  @Override
  public ActionCallback onSelected(@Nullable Configurable configurable, Configurable oldConfigurable) {
    return select(configurable);
  }

  @Override
  public ActionCallback onModifiedAdded(Configurable configurable) {
    myTree.repaint();
    return ActionCallback.DONE;
  }

  @Override
  public ActionCallback onModifiedRemoved(Configurable configurable) {
    myTree.repaint();
    return ActionCallback.DONE;
  }

  @Override
  public ActionCallback onErrorsChanged() {
    return ActionCallback.DONE;
  }

  private final class MyRoot extends CachingSimpleNode {
    private final ConfigurableGroup[] myGroups;

    private MyRoot(ConfigurableGroup[] groups) {
      super(null);
      myGroups = groups;
    }

    @Override
    protected SimpleNode[] buildChildren() {
      if (myGroups == null || myGroups.length == 0) {
        return NO_CHILDREN;
      }
      ArrayList<MyNode> list = new ArrayList<>();
      for (ConfigurableGroup group : myGroups) {
        for (Configurable configurable : group.getConfigurables()) {
          list.add(new MyNode(this, configurable, 0));
        }
      }
      return list.toArray(new SimpleNode[list.size()]);
    }
  }

  private final class MyNode extends CachingSimpleNode {
    private final Configurable.Composite myComposite;
    private final Configurable myConfigurable;
    private final String myDisplayName;
    private final int myLevel;

    private MyNode(CachingSimpleNode parent, Configurable configurable, int level) {
      super(parent);
      myComposite = configurable instanceof Configurable.Composite ? (Configurable.Composite)configurable : null;
      myConfigurable = configurable;
      String name = configurable.getDisplayName();
      myDisplayName = name != null ? name.replace("\n", " ") : "{ " + configurable.getClass().getSimpleName() + " }";
      myLevel = level;
    }

    @Override
    protected SimpleNode[] buildChildren() {
      if (myConfigurable != null) {
        myConfigurableToNodeMap.put(myConfigurable, this);
      }
      if (myComposite == null) {
        return NO_CHILDREN;
      }
      Configurable[] configurables = myComposite.getConfigurables();
      if (configurables == null || configurables.length == 0) {
        return NO_CHILDREN;
      }
      SimpleNode[] result = new SimpleNode[configurables.length];
      for (int i = 0; i < configurables.length; i++) {
        result[i] = new MyNode(this, configurables[i], myLevel + 1);
        if (myConfigurable != null) {
          myFilter.myContext.registerKid(myConfigurable, configurables[i]);
        }
      }
      return result;
    }

    @Override
    public boolean isAlwaysLeaf() {
      return myComposite == null;
    }
  }

  private final class MyRenderer extends JPanel implements TreeCellRenderer {
    private final SimpleColoredComponent myTextLabel = new SimpleColoredComponent();
    private final JLabel myNodeIcon = new JLabel();
    private final JLabel myProjectIcon = new JLabel();

    public MyRenderer() {
      super(new BorderLayout(ICON_GAP, 0));
      myNodeIcon.setName(NODE_ICON);
      myTextLabel.setOpaque(false);
      add(BorderLayout.CENTER, myTextLabel);
      add(BorderLayout.WEST, myNodeIcon);
      add(BorderLayout.EAST, myProjectIcon);
      setBorder(BorderFactory.createEmptyBorder(1, 10, 3, 10));
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new MyAccessibleContext();
      }
      return accessibleContext;
    }

    // TODO: consider making MyRenderer a subclass of SimpleColoredComponent.
    // This should eliminate the need to add this accessibility stuff.
    private class MyAccessibleContext extends JPanel.AccessibleJPanel {
      @Override
      public String getAccessibleName() {
        return myTextLabel.getCharSequence(true).toString();
      }
    }

    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean focused) {
      myTextLabel.clear();
      setPreferredSize(null);

      MyNode node = extractNode(value);
      boolean isGroup = node != null && myRoot == node.getParent();
      String name = node != null ? node.myDisplayName : String.valueOf(value);
      myTextLabel.append(name, isGroup ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myTextLabel.setFont(isGroup ? myTree.getFont() : UIUtil.getLabelFont());

      // update font color for modified configurables
      myTextLabel.setForeground(selected ? UIUtil.getTreeSelectionForeground() : FOREGROUND);
      if (!selected && node != null) {
        Configurable configurable = node.myConfigurable;
        if (configurable != null) {
          if (myFilter.myContext.getErrors().containsKey(configurable)) {
            myTextLabel.setForeground(WRONG_CONTENT);
          }
          else if (myFilter.myContext.getModified().contains(configurable)) {
            myTextLabel.setForeground(MODIFIED_CONTENT);
          }
        }
      }
      // configure project icon
      Project project = null;
      if (node != null) {
        SimpleNode parent = node.getParent();
        if (parent instanceof MyNode) {
          if (myRoot == parent.getParent()) {
            project = findConfigurableProject(node); // show icon for top-level nodes
            if (node.myConfigurable instanceof SortedConfigurableGroup) { // special case for custom subgroups (build.tools)
              Configurable[] configurables = ((SortedConfigurableGroup)node.myConfigurable).getConfigurables();
              if (configurables != null) { // assume that all configurables have the same project
                project = findConfigurableProject(configurables[0]);
              }
            }
          }
          else if (((MyNode)parent).myConfigurable instanceof SortedConfigurableGroup) {
            if (((MyNode)node.getParent()).myConfigurable instanceof SortedConfigurableGroup) {
              project = findConfigurableProject(node); // special case for custom subgroups
            }
          }
        }
      }
      if (project != null) {
        myProjectIcon.setIcon(selected
                              ? AllIcons.General.ProjectConfigurableSelected
                              : AllIcons.General.ProjectConfigurable);
        myProjectIcon.setToolTipText(OptionsBundle.message(project.isDefault()
                                                           ? "configurable.default.project.tooltip"
                                                           : "configurable.current.project.tooltip"));
        myProjectIcon.setVisible(true);
      }
      else {
        myProjectIcon.setVisible(false);
      }
      // configure node icon
      Icon nodeIcon = null;
      if (value instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
        if (0 == treeNode.getChildCount()) {
          nodeIcon = myTree.getEmptyHandle();
        }
        else {
          nodeIcon = myTree.isExpanded(new TreePath(treeNode.getPath()))
                     ? myTree.getExpandedHandle()
                     : myTree.getCollapsedHandle();
        }
      }
      myNodeIcon.setIcon(nodeIcon);
      if (node != null && myPaintInternalInfo) {
        String id = node.myConfigurable instanceof ConfigurableWrapper ? ((ConfigurableWrapper)node.myConfigurable).getId() :
                    node.myConfigurable instanceof SearchableConfigurable ? ((SearchableConfigurable)node.myConfigurable).getId() :
                    node.myConfigurable.getClass().getSimpleName();
        PluginDescriptor plugin = node.myConfigurable instanceof ConfigurableWrapper ? ((ConfigurableWrapper)node.myConfigurable).getExtensionPoint().getPluginDescriptor() : null;
        String pluginId = plugin == null ? null : plugin.getPluginId().getIdString();
        String pluginName = pluginId == null || PluginManagerCore.CORE_PLUGIN_ID.equals(pluginId)? null :
                            plugin instanceof IdeaPluginDescriptor ? ((IdeaPluginDescriptor)plugin).getName() : pluginId;
        myTextLabel.append("   ", SimpleTextAttributes.REGULAR_ATTRIBUTES, false);
        myTextLabel.append(pluginName == null ? id : id + " (" + pluginName + ")", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES, false);
      }
      // calculate minimum size
      if (node != null && tree.isVisible()) {
        int width = getLeftMargin(node.myLevel) + getPreferredSize().width;
        Insets insets = tree.getInsets();
        if (insets != null) {
          width += insets.left + insets.right;
        }
        JScrollBar bar = myScroller.getVerticalScrollBar();
        if (bar != null && bar.isVisible()) {
          width += bar.getWidth();
        }
        width = Math.min(width, 300); // maximal width for minimum size
        JComponent view = SettingsTreeView.this;
        Dimension size = view.getMinimumSize();
        if (size.width < width) {
          size.width = width;
          view.setMinimumSize(size);
          view.revalidate();
          view.repaint();
        }
      }
      return this;
    }
  }

  private final class MyTree extends SimpleTree {
    @Override
    public String getToolTipText(MouseEvent event) {
      if (event != null) {
        Component component = getDeepestRendererComponentAt(event.getX(), event.getY());
        if (component instanceof JLabel) {
          JLabel label = (JLabel)component;
          if (label.getIcon() != null) {
            String text = label.getToolTipText();
            if (text != null) {
              return text;
            }
          }
        }
      }
      return super.getToolTipText(event);
    }

    @Override
    protected boolean paintNodes() {
      return false;
    }

    @Override
    protected boolean highlightSingleNode() {
      return false;
    }

    @Override
    public void setUI(TreeUI ui) {
      super.setUI(ui instanceof MyTreeUi ? ui : new MyTreeUi());
    }

    @Override
    protected boolean isCustomUI() {
      return true;
    }

    @Override
    protected void configureUiHelper(TreeUIHelper helper) {
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }


    @Override
    public void processKeyEvent(KeyEvent e) {
      TreePath path = myTree.getSelectionPath();
      if (path != null) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
          if (isExpanded(path)) {
            collapsePath(path);
            return;
          }
        }
        else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          if (isCollapsed(path)) {
            expandPath(path);
            return;
          }
        }
      }
      super.processKeyEvent(e);
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
      MyTreeUi ui = (MyTreeUi)myTree.getUI();
      if (!ui.processMouseEvent(event)) {
        super.processMouseEvent(event);
      }
    }
  }

  private static final class MyTreeUi extends WideSelectionTreeUI {
    boolean processMouseEvent(MouseEvent event) {
      if (super.tree instanceof SimpleTree) {
        SimpleTree tree = (SimpleTree)super.tree;

        boolean toggleNow = MouseEvent.MOUSE_RELEASED == event.getID()
                            && UIUtil.isActionClick(event, MouseEvent.MOUSE_RELEASED)
                            && !isToggleEvent(event);

        if (toggleNow || MouseEvent.MOUSE_PRESSED == event.getID()) {
          Component component = tree.getDeepestRendererComponentAt(event.getX(), event.getY());
          if (component != null && NODE_ICON.equals(component.getName())) {
            if (toggleNow) {
              toggleExpandState(tree.getPathForLocation(event.getX(), event.getY()));
            }
            event.consume();
            return true;
          }
        }
      }
      return false;
    }

    @Override
    protected boolean shouldPaintExpandControl(TreePath path,
                                               int row,
                                               boolean isExpanded,
                                               boolean hasBeenExpanded,
                                               boolean isLeaf) {
      return false;
    }

    @Override
    protected void paintHorizontalPartOfLeg(Graphics g,
                                            Rectangle clipBounds,
                                            Insets insets,
                                            Rectangle bounds,
                                            TreePath path,
                                            int row,
                                            boolean isExpanded,
                                            boolean hasBeenExpanded,
                                            boolean isLeaf) {

    }

    @Override
    protected void paintVerticalPartOfLeg(Graphics g, Rectangle clipBounds, Insets insets, TreePath path) {
    }

    @Override
    public void paint(Graphics g, JComponent c) {
      GraphicsUtil.setupAntialiasing(g);
      super.paint(g, c);
    }

    @Override
    protected void paintRow(Graphics g,
                            Rectangle clipBounds,
                            Insets insets,
                            Rectangle bounds,
                            TreePath path,
                            int row,
                            boolean isExpanded,
                            boolean hasBeenExpanded,
                            boolean isLeaf) {
      if (tree != null) {
        bounds.width = tree.getWidth();
        Container parent = tree.getParent();
        if (parent instanceof JViewport) {
          JViewport viewport = (JViewport)parent;
          bounds.width = viewport.getWidth() - viewport.getViewPosition().x - insets.right / 2;
        }
        bounds.width -= bounds.x;
      }
      super.paintRow(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
    }

    @Override
    protected int getRowX(int row, int depth) {
      return getLeftMargin(depth - 1);
    }
  }

  private final class MyBuilder extends FilteringTreeBuilder {

    List<Object> myToExpandOnResetFilter;
    boolean myRefilteringNow;
    boolean myWasHoldingFilter;

    public MyBuilder(SimpleTreeStructure structure) {
      super(myTree, myFilter, structure, null);
      myTree.addTreeExpansionListener(new TreeExpansionListener() {
        public void treeExpanded(TreeExpansionEvent event) {
          invalidateExpansions();
        }

        public void treeCollapsed(TreeExpansionEvent event) {
          invalidateExpansions();
        }
      });
    }

    private void invalidateExpansions() {
      if (!myRefilteringNow) {
        myToExpandOnResetFilter = null;
      }
    }

    @Override
    protected boolean isSelectable(Object object) {
      return object instanceof MyNode;
    }

    @Override
    public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
      return myFilter.myContext.isHoldingFilter();
    }

    @Override
    public boolean isToEnsureSelectionOnFocusGained() {
      return false;
    }

    @Override
    protected ActionCallback refilterNow(Object preferredSelection, boolean adjustSelection) {
      final List<Object> toRestore = new ArrayList<>();
      if (myFilter.myContext.isHoldingFilter() && !myWasHoldingFilter && myToExpandOnResetFilter == null) {
        myToExpandOnResetFilter = myBuilder.getUi().getExpandedElements();
      }
      else if (!myFilter.myContext.isHoldingFilter() && myWasHoldingFilter && myToExpandOnResetFilter != null) {
        toRestore.addAll(myToExpandOnResetFilter);
        myToExpandOnResetFilter = null;
      }

      myWasHoldingFilter = myFilter.myContext.isHoldingFilter();

      ActionCallback result = super.refilterNow(preferredSelection, adjustSelection);
      myRefilteringNow = true;
      return result.doWhenDone(() -> {
        myRefilteringNow = false;
        if (!myFilter.myContext.isHoldingFilter() && getSelectedElements().isEmpty()) {
          restoreExpandedState(toRestore);
        }
      });
    }

    private void restoreExpandedState(List<Object> toRestore) {
      TreePath[] selected = myTree.getSelectionPaths();
      if (selected == null) {
        selected = new TreePath[0];
      }

      List<TreePath> toCollapse = new ArrayList<>();

      for (int eachRow = 0; eachRow < myTree.getRowCount(); eachRow++) {
        if (!myTree.isExpanded(eachRow)) continue;

        TreePath eachVisiblePath = myTree.getPathForRow(eachRow);
        if (eachVisiblePath == null) continue;

        Object eachElement = myBuilder.getElementFor(eachVisiblePath.getLastPathComponent());
        if (toRestore.contains(eachElement)) continue;


        for (TreePath eachSelected : selected) {
          if (!eachVisiblePath.isDescendant(eachSelected)) {
            toCollapse.add(eachVisiblePath);
          }
        }
      }

      for (TreePath each : toCollapse) {
        myTree.collapsePath(each);
      }
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleSettingsTreeView();
    }
    return accessibleContext;
  }

  protected class AccessibleSettingsTreeView extends AccessibleJComponent {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PANEL;
    }
  }
}
