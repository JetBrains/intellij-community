// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.ex.SortedConfigurableGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.*;
import com.intellij.ui.components.GradientViewport;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.tree.ui.Control;
import com.intellij.ui.tree.ui.DefaultControl;
import com.intellij.ui.treeStructure.CachingSimpleNode;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.*;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class SettingsTreeView extends JComponent implements Accessible, Disposable, OptionsEditorColleague {
  private static final int ICON_GAP = 5;
  private static final String NODE_ICON = "settings.tree.view.icon";
  private static final Color WRONG_CONTENT = JBColor.namedColor("Tree.errorForeground", JBColor.RED);
  private static final Color MODIFIED_CONTENT = JBColor.namedColor("Tree.modifiedItemForeground", JBColor.BLUE);

  final SimpleTree myTree;
  private final MyBuilder myBuilder;

  private final SettingsFilter myFilter;
  private final JScrollPane myScroller;
  private final Map<Configurable, MyNode> myConfigurableToNodeMap = new IdentityHashMap<>();
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("SettingsTreeView", 150, false, this, this, this)
    .setRestartTimerOnAdd(true);

  private final MyRoot myRoot;

  private Configurable myQueuedConfigurable;
  private MyControl myControl;

  public SettingsTreeView(@NotNull SettingsFilter filter, @NotNull List<? extends ConfigurableGroup> groups) {
    myFilter = filter;
    myTree = new MyTree();
    myTree.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true);
    myTree.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    myTree.getInputMap().clear();
    TreeUtil.installActions(myTree);

    myTree.setOpaque(true);

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    myTree.setCellRenderer(new MyRenderer());
    myTree.setRootVisible(false);
    myTree.setExpandableItemsEnabled(false);
    RelativeFont.BOLD.install(myTree);
    setComponentPopupMenuTo(myTree);

    myTree.setTransferHandler(new TransferHandler() {
      @Nullable
      @Override
      protected Transferable createTransferable(JComponent c) {
        return SettingsTreeView.createTransferable(myTree.getSelectionPath());
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
          myHeader.setForeground(UIUtil.getTreeForeground());
          myHeader.setIconTextGap(JBUIScale.scale(ICON_GAP));
          myHeader.setBorder(JBUI.Borders.empty(2, 10 + getLeftMargin(0), 0, 0));
        }
        myHeader.setFont(myTree.getFont());
        myHeader.setIcon(getIcon(null, false));
        int height = myHeader.getPreferredSize().height;
        String group = findGroupNameAt(0, height + 3);
        if (group == null || !group.equals(findGroupNameAt(0, 0))) {
          return null; // do not show separator over another group
        }
        myHeader.setText(group);
        return myHeader;
      }
    });
    myScroller.setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    myScroller.getViewport().setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    myScroller.getVerticalScrollBar().setBackground(UIUtil.SIDE_PANEL_BACKGROUND);
    myScroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
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

    myTree.getSelectionModel().addTreeSelectionListener(event -> {
      MyNode node = extractNode(event.getNewLeadSelectionPath());
      select(node == null ? null : node.myConfigurable);
    });
    myRoot = new MyRoot(groups);
    myBuilder = new MyBuilder(new SimpleTreeStructure.Impl(myRoot));
    myBuilder.setFilteringMerge(300, null);
    Disposer.register(this, myBuilder);

    myTree.getAccessibleContext().setAccessibleName(UIBundle.message("settings.tree.settings.categories.accessible.name"));
  }

  @Override
  public void updateUI() {
    super.updateUI();
    myControl = null;
  }

  private Icon getIcon(@Nullable DefaultMutableTreeNode node, boolean selected) {
    if (myControl == null) myControl = new MyControl();
    if (node == null || 0 == node.getChildCount()) return myControl.empty;
    if (selected
        && !ColorUtil.isDark(JBUI.CurrentTheme.Tree.BACKGROUND)
        && !ColorUtil.isDark(RenderingUtil.getSelectionBackground(myTree))) {
      selected = false; // do not use selected icon on light theme
    }
    return myTree.isExpanded(new TreePath(node.getPath())) ? myControl.expanded.apply(selected) : myControl.collapsed.apply(selected);
  }

  private static final class MyControl {
    private final Control control = new DefaultControl();
    private final Function<Boolean, Icon> collapsed = selected -> new MyIcon(false, () -> selected);
    private final Function<Boolean, Icon> expanded = selected -> new MyIcon(true, () -> selected);
    private final Icon empty = new MyIcon(null, () -> false);

    private final class MyIcon implements Icon {
      private final Boolean expanded;
      private final Supplier<Boolean> selected;

      private MyIcon(@Nullable Boolean expanded, Supplier<Boolean> selected) {
        this.expanded = expanded;
        this.selected = selected;
      }

      @Override
      public int getIconWidth() {
        return control.getWidth();
      }

      @Override
      public int getIconHeight() {
        return control.getHeight();
      }

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        if (expanded != null) control.paint(c, g, x, y, getIconWidth(), getIconHeight(), expanded, selected.get());
      }
    }
  }

  private static void setComponentPopupMenuTo(JTree tree) {
    tree.setComponentPopupMenu(new JPopupMenu() {
      @Nullable
      private Collection<String> names;

      @Override
      public void show(Component invoker, int x, int y) {
        names = null;

        if (invoker != tree) return;
        TreePath path = tree.getClosestPathForLocation(x, y);
        names = getPathNames(extractNode(path));
        if (names.isEmpty()) {
          return;
        }

        Rectangle bounds = tree.getPathBounds(path);
        if (bounds == null || bounds.y > y) return;
        bounds.y += bounds.height;
        if (bounds.y < y) return;
        super.show(invoker, x, bounds.y);
      }

      {
        for (Action action : CopySettingsPathAction.createSwingActions(() -> names)) {
          add(action);
        }
      }
    });
  }

  @Nullable
  Transferable createTransferable(@Nullable InputEvent event) {
    if (event instanceof MouseEvent) {
      MouseEvent mouse = (MouseEvent)event;
      Point location = mouse.getLocationOnScreen();
      SwingUtilities.convertPointFromScreen(location, myTree);
      return createTransferable(myTree.getClosestPathForLocation(location.x, location.y));
    }
    return createTransferable(myTree.getSelectionPath());
  }

  @Nullable
  private static Transferable createTransferable(@Nullable TreePath path) {
    return CopySettingsPathAction.createTransferable(getPathNames(extractNode(path)));
  }

  @NotNull
  Collection<@NlsContexts.ConfigurableName String> getPathNames(Configurable configurable) {
    return getPathNames(findNode(configurable));
  }

  @NotNull
  private static Collection<@NlsContexts.ConfigurableName String> getPathNames(@Nullable MyNode node) {
    if (node == null) {
      return Collections.emptyList();
    }

    ArrayDeque<String> path = new ArrayDeque<>();
    while (node != null) {
      path.push(node.myDisplayName);
      SimpleNode parent = node.getParent();
      node = parent instanceof MyNode ? (MyNode)parent : null;
    }
    return path;
  }

  static Configurable getConfigurable(SimpleNode node) {
    return node instanceof MyNode
           ? ((MyNode)node).myConfigurable
           : null;
  }

  @Nullable
  MyNode findNode(Configurable configurable) {
    return myConfigurableToNodeMap.get(configurable);
  }

  @Nullable
  Project findConfigurableProject(@Nullable Configurable configurable) {
    MyNode node = findNode(configurable);
    return node == null ? null : findConfigurableProject(node);
  }

  @Nullable
  private static Project findConfigurableProject(@NotNull MyNode node) {
    Configurable configurable = node.myConfigurable;
    Project project = node.getProject();
    Configurable.VariableProjectAppLevel wrapped = ConfigurableWrapper.cast(Configurable.VariableProjectAppLevel.class, configurable);
    if (wrapped != null) return wrapped.isProjectLevel() ? project : null;
    if (configurable instanceof ConfigurableWrapper) return project;
    if (configurable instanceof SortedConfigurableGroup) return project;

    SimpleNode parent = node.getParent();
    return parent instanceof MyNode
           ? findConfigurableProject((MyNode)parent)
           : null;
  }

  @ApiStatus.Internal
  public static @Nullable Project prepareProject(@Nullable CachingSimpleNode parent,
                                                 @Nullable Configurable configurable) {
    if (configurable instanceof ConfigurableWrapper) {
      ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
      return wrapper.getExtensionPoint().getProject();
    }
    if (configurable instanceof SortedConfigurableGroup) {
      SortedConfigurableGroup group = (SortedConfigurableGroup)configurable;
      Configurable[] configurables = group.getConfigurables();
      if (configurables.length != 0) {
        Project project = prepareProject(parent, configurables[0]);
        if (project != null) {
          for (int i = 1; i < configurables.length; i++) {
            if (project != prepareProject(parent, configurables[i])) {
              return null;
            }
          }
        }
        return project;
      }
    }
    if (configurable instanceof ConfigurableProjectProvider) {
      return ((ConfigurableProjectProvider)configurable).getProject();
    }
    return parent == null ? null : parent.getProject();
  }

  private static int getLeftMargin(int level) {
    return 3 + level * (11 + ICON_GAP);
  }

  @Nullable
  private @NlsContexts.ConfigurableName String findGroupNameAt(@SuppressWarnings("SameParameterValue") int x, int y) {
    TreePath path = myTree.getClosestPathForLocation(x - myTree.getX(), y - myTree.getY());
    while (path != null) {
      MyNode node = extractNode(path);
      if (node == null) {
        return null;
      }
      if (node.getParent() instanceof MyRoot) {
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

  @NotNull
  Promise<? super Object> select(@Nullable Configurable configurable) {
    if (myBuilder.isSelectionBeingAdjusted()) {
      return Promises.rejectedPromise();
    }

    AsyncPromise<? super Object> promise = new AsyncPromise<>();
    myQueuedConfigurable = configurable;
    myQueue.queue(new Update(this) {
      @Override
      public void run() {
        if (configurable != myQueuedConfigurable) {
          return;
        }

        if (configurable == null) {
          fireSelected(null)
            .processed(promise);
        }
        else {
          myBuilder.getReady(this).doWhenDone(() -> {
            if (configurable != myQueuedConfigurable) return;

            MyNode editorNode = findNode(configurable);
            FilteringTreeStructure.FilteringNode editorUiNode = myBuilder.getVisibleNodeFor(editorNode);
            if (editorUiNode == null) return;

            @SuppressWarnings("CodeBlock2Expr")
            Runnable handler = () -> {
              fireSelected(configurable)
                .processed(promise);
            };

            if (myBuilder.getSelectedElements().contains(editorUiNode)) {
              myBuilder.scrollSelectionToVisible(handler, false);
            }
            else {
              myBuilder.select(editorUiNode, handler);
            }
          });
        }
      }

      @Override
      public void setRejected() {
        super.setRejected();
        promise.cancel();
      }
    });
    return promise;
  }

  @NotNull
  private Promise<? super Object> fireSelected(Configurable configurable) {
    return myFilter.myContext.fireSelected(configurable, this);
  }

  @Override
  public void dispose() {
    myQueuedConfigurable = null;
    // help GC and avoid leak on dynamic plugin reload (if some configurable hold language or something plugin-specific)
    myConfigurableToNodeMap.clear();
  }

  @NotNull
  @Override
  public Promise<? super Object> onSelected(@Nullable Configurable configurable, Configurable oldConfigurable) {
    return select(configurable);
  }

  @NotNull
  @Override
  public Promise<? super Object> onModifiedAdded(Configurable configurable) {
    myTree.repaint();
    return Promises.resolvedPromise();
  }

  @NotNull
  @Override
  public Promise<? super Object> onModifiedRemoved(Configurable configurable) {
    myTree.repaint();
    return Promises.resolvedPromise();
  }

  @NotNull
  @Override
  public Promise<? super Object> onErrorsChanged() {
    return Promises.resolvedPromise();
  }

  private final class MyRoot extends CachingSimpleNode {
    private final List<? extends ConfigurableGroup> myGroups;

    private MyRoot(@NotNull List<? extends ConfigurableGroup> groups) {
      super(null);
      myGroups = groups;
    }

    @Override
    protected SimpleNode[] buildChildren() {
      if (myGroups == null || myGroups.isEmpty()) {
        return NO_CHILDREN;
      }
      ArrayList<MyNode> list = new ArrayList<>();
      for (ConfigurableGroup group : myGroups) {
        for (Configurable configurable : group.getConfigurables()) {
          list.add(new MyNode(this, configurable, 0));
        }
      }
      return list.toArray(new SimpleNode[0]);
    }
  }

  private final class MyNode extends CachingSimpleNode {
    private final Configurable.Composite myComposite;
    private final Configurable myConfigurable;
    private final @NlsContexts.ConfigurableName String myDisplayName;
    private final int myLevel;
    private ConfigurableTreeRenderer myRenderer;
    private boolean myPrepareRenderer = true;

    private MyNode(CachingSimpleNode parent, Configurable configurable, int level) {
      super(prepareProject(parent, configurable), parent);
      myComposite = configurable instanceof Configurable.Composite ? (Configurable.Composite)configurable : null;
      myConfigurable = configurable;
      String name = configurable.getDisplayName();
      myDisplayName = name != null ? name.replace("\n", " ") : "{ " + configurable.getClass().getSimpleName() + " }";  // NON-NLS (safe)
      myLevel = level;
    }

    @Nullable
    public ConfigurableTreeRenderer getRenderer() {
      if (myPrepareRenderer) {
        myPrepareRenderer = false;
        if (myConfigurable instanceof ConfigurableTreeRenderer) {
          myRenderer = (ConfigurableTreeRenderer)myConfigurable;
        }
        else if (myConfigurable instanceof ConfigurableWrapper) {
          ConfigurableWrapper wrapper = (ConfigurableWrapper)myConfigurable;
          UnnamedConfigurable configurable = wrapper.getRawConfigurable();
          if (configurable instanceof ConfigurableTreeRenderer) {
            myRenderer = (ConfigurableTreeRenderer)configurable;
          }
          else {
            myRenderer = wrapper.getExtensionPoint().createTreeRenderer();
          }
        }
      }
      return myRenderer;
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
      if (configurables.length == 0) {
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
    protected void update(@NotNull PresentationData presentation) {
      super.update(presentation);
      presentation.addText(myDisplayName, getPlainAttributes());
    }

    @Override
    public boolean isAlwaysLeaf() {
      return myComposite == null;
    }
  }

  private final class MyRenderer extends CellRendererPanel implements TreeCellRenderer {
    final SimpleColoredComponent myTextLabel = new SimpleColoredComponent();
    final JLabel myNodeIcon = new JLabel();
    final JLabel myProjectIcon = new JLabel();
    Pair<Component, ConfigurableTreeRenderer.Layout> myRenderInfo;

    MyRenderer() {
      setLayout(new BorderLayout(JBUIScale.scale(ICON_GAP - 1), 0));
      myNodeIcon.setName(NODE_ICON);
      myTextLabel.setOpaque(false);
      myTextLabel.setIpad(JBInsets.create(1, 0));
      add(BorderLayout.CENTER, myTextLabel);
      add(BorderLayout.WEST, myNodeIcon);
      add(BorderLayout.EAST, myProjectIcon);
      setBorder(JBUI.Borders.empty(1, 10, 3, 10));
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

    @Override
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
      boolean isGroup = node != null && node.getParent() instanceof MyRoot;
      String name = node != null ? node.myDisplayName : String.valueOf(value);
      myTextLabel.append(name, isGroup ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
      myTextLabel.setFont(isGroup ? myTree.getFont() : StartupUiUtil.getLabelFont());

      // update font color for modified configurables
      myTextLabel.setForeground(selected ? UIUtil.getTreeSelectionForeground(true) : UIUtil.getTreeForeground());
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
      Project project = node != null ? findConfigurableProject(node) : null;
      Configurable configurable = node != null ? node.myConfigurable : null;
      setProjectIcon(myProjectIcon, configurable, project, selected);
      prepareRenderer(node != null, node, configurable, selected);
      // configure node icon
      Icon nodeIcon = value instanceof DefaultMutableTreeNode ?
                      getIcon((DefaultMutableTreeNode)value, selected) :
                      null;
      myNodeIcon.setIcon(nodeIcon);

      if (configurable instanceof ConfigurableMarkerProvider) {
        String label = ((ConfigurableMarkerProvider)configurable).getMarkerText();
        if (label != null) {
          myTextLabel.append("   ", SimpleTextAttributes.REGULAR_ATTRIBUTES, false);
          myTextLabel.append(label, SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES, false);
        }
      }

      if (node != null && UISettings.getInstance().getShowInplaceCommentsInternal()) {
        ConfigurableWrapper wrapper = configurable instanceof ConfigurableWrapper ?
                                      ((ConfigurableWrapper)configurable) :
                                      null;

        @NonNls String id = wrapper != null ? wrapper.getId() :
                            configurable instanceof SearchableConfigurable ? ((SearchableConfigurable)configurable).getId() :
                            configurable.getClass().getSimpleName();
        PluginDescriptor plugin = wrapper != null ?
                                  wrapper.getExtensionPoint().getPluginDescriptor() :
                                  null;

        PluginId pluginId = plugin == null ? null : plugin.getPluginId();
        String pluginName = pluginId == null || PluginManagerCore.CORE_ID.equals(pluginId) ? null : plugin.getName();
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

    private void prepareRenderer(boolean visible, MyNode node, @Nullable UnnamedConfigurable configurable, boolean selected) {
      myRenderInfo = null;
      if (visible) {
        ConfigurableTreeRenderer renderer = node.getRenderer();
        if (renderer != null) {
          if (configurable instanceof ConfigurableWrapper) {
            configurable = ((ConfigurableWrapper)configurable).getRawConfigurable();
          }
          myRenderInfo = renderer.getDecorator(myTree, configurable, selected);
        }
      }
    }

    @Override
    protected void paintChildren(Graphics g) {
      super.paintChildren(g);

      if (myRenderInfo != null) {
        Rectangle bounds = new Rectangle(0, 0, getWidth(), getHeight());
        JBInsets.removeFrom(bounds, getInsets());

        Rectangle text = myTextLabel.getBounds();
        int baseline = myTextLabel.getBaseline(text.width, text.height);

        if (myProjectIcon.getIcon() == null) {
          myProjectIcon.setIcon(AllIcons.General.ProjectConfigurable);
        }
        Dimension icon = myProjectIcon.getPreferredSize();
        Rectangle right = new Rectangle(bounds.x + bounds.width - icon.width, bounds.y, icon.width, icon.height);

        myRenderInfo.second.layoutBeforePaint(myRenderInfo.first, bounds, text, right, baseline);

        Rectangle paintBounds = myRenderInfo.first.getBounds();
        Graphics2D g2 = (Graphics2D)g.create(paintBounds.x, paintBounds.y, paintBounds.width, paintBounds.height);
        myRenderInfo.first.paint(g2);
        g2.dispose();

        myRenderInfo = null;
      }
    }
  }

  @SuppressWarnings("unused")
  protected void setProjectIcon(JLabel projectIcon, Configurable configurable, @Nullable Project project, boolean selected) {
    if (project != null) {
      projectIcon.setIcon(AllIcons.General.ProjectConfigurable);
      projectIcon.setToolTipText(project.isDefault()
                                 ? IdeUICustomization.getInstance().projectMessage("configurable.default.project.tooltip")
                                 : IdeUICustomization.getInstance().projectMessage("configurable.current.project.tooltip"));
      projectIcon.setVisible(true);
    }
    else {
      projectIcon.setVisible(false);
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
      setRowHeight(UIManager.getInt("SettingsTree.rowHeight"));
    }

    @Override
    protected void configureUiHelper(TreeUIHelper helper) {
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
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
    @Override
    public Rectangle getPathBounds(JTree tree, TreePath path) {
      Rectangle bounds = super.getPathBounds(tree, path);
      if (bounds != null) {
        bounds.width = Math.max(bounds.width, tree.getWidth() - bounds.x);
      }
      return bounds;
    }

    boolean processMouseEvent(MouseEvent event) {
      if (tree instanceof SimpleTree) {
        SimpleTree tree = (SimpleTree)super.tree;

        boolean toggleNow = MouseEvent.MOUSE_RELEASED == event.getID()
                            && UIUtil.isActionClick(event, MouseEvent.MOUSE_RELEASED)
                            && !isToggleEvent(event);

        if (toggleNow || MouseEvent.MOUSE_PRESSED == event.getID()) {
          Component component = tree.getDeepestRendererComponentAt(event.getX(), event.getY());
          if (component instanceof JLabel && NODE_ICON.equals(component.getName())) {
            MyControl.MyIcon icon = ObjectUtils.tryCast(((JLabel)component).getIcon(), MyControl.MyIcon.class);
            if (icon == null || icon.expanded == null) return false; // do not consume the mouse event for a leaf node
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
        JViewport viewport = ComponentUtil.getViewport(tree);
        if (viewport != null) {
          bounds.width = viewport.getWidth() - viewport.getViewPosition().x - insets.right / 2;
        }
        bounds.width -= bounds.x;
      }
      super.paintRow(g, clipBounds, insets, bounds, path, row, isExpanded, hasBeenExpanded, isLeaf);
    }

    @Override
    protected int getRowX(int row, int depth) {
      return JBUIScale.scale(getLeftMargin(depth - 1));
    }
  }

  private final class MyBuilder extends FilteringTreeBuilder {

    List<Object> myToExpandOnResetFilter;
    boolean myRefilteringNow;
    boolean myWasHoldingFilter;

    MyBuilder(SimpleTreeStructure structure) {
      super(myTree, myFilter, structure, null);
      myTree.addTreeExpansionListener(new TreeExpansionListener() {
        @Override
        public void treeExpanded(TreeExpansionEvent event) {
          invalidateExpansions();
        }

        @Override
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

    @NotNull
    @Override
    protected Promise<?> refilterNow(Object preferredSelection, boolean adjustSelection) {
      final List<Object> toRestore = new ArrayList<>();
      if (myFilter.myContext.isHoldingFilter() && !myWasHoldingFilter && myToExpandOnResetFilter == null) {
        AbstractTreeUi ui = myBuilder.getUi();
        myToExpandOnResetFilter = ui == null ? null : ui.getExpandedElements();
      }
      else if (!myFilter.myContext.isHoldingFilter() && myWasHoldingFilter && myToExpandOnResetFilter != null) {
        toRestore.addAll(myToExpandOnResetFilter);
        myToExpandOnResetFilter = null;
      }

      myWasHoldingFilter = myFilter.myContext.isHoldingFilter();

      myRefilteringNow = true;
      return super.refilterNow(preferredSelection, adjustSelection)
        .onSuccess(o -> {
          myRefilteringNow = false;
          if (!myFilter.myContext.isHoldingFilter() && getSelectedElements().isEmpty()) {
            restoreExpandedState(toRestore);
          }
        });
    }

    private void restoreExpandedState(List<Object> toRestore) {
      List<TreePath> selected = TreeUtil.collectSelectedPaths(myTree);
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

  void reloadWithSelection(@Nullable Configurable toSelect) {
    myRoot.cleanUpCache();
    myQueuedConfigurable = null;
    myQueue.cancelAllUpdates();
    myConfigurableToNodeMap.clear();
    AbstractTreeUi ui = myBuilder.getUi();
    if (ui == null) return;

    //remove expansion and selection (to avoid stuck old elements) before cleanup
    myTree.getSelectionModel().clearSelection();
    myTree.collapsePath(new TreePath(myTree.getModel().getRoot()));

    myBuilder.cleanUp();
    ui.getUpdater().reset();
    AbstractTreeStructure structure = ui.getTreeStructure();
    if (structure instanceof FilteringTreeStructure) {
      ((FilteringTreeStructure)structure).rebuild();
    }
    MyNode node = findNode(toSelect);
    myBuilder.refilterNow(node, true);
  }
}
