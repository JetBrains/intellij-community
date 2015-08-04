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
package com.intellij.openapi.options.newEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.treeStructure.*;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.TreeUI;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

public class OptionsTree extends JPanel implements Disposable, OptionsEditorColleague {
  private final SettingsFilter myFilter;
  final SimpleTree myTree;
  List<ConfigurableGroup> myGroups;
  FilteringTreeBuilder myBuilder;
  Root myRoot;

  Map<Configurable, EditorNode> myConfigurable2Node = new HashMap<Configurable, EditorNode>();

  MergingUpdateQueue mySelection;
  private final OptionsTree.Renderer myRenderer;

  public OptionsTree(SettingsFilter filter, ConfigurableGroup... groups) {
    myFilter = filter;
    myGroups = Arrays.asList(groups);

    myRoot = new Root();
    final SimpleTreeStructure structure = new SimpleTreeStructure() {
      public Object getRootElement() {
        return myRoot;
      }
    };

    myTree = new MyTree();
    TreeUtil.installActions(myTree);
    myTree.setBorder(new EmptyBorder(0, 1, 0, 0));

    myTree.setRowHeight(-1);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myRenderer = new Renderer();
    myTree.setCellRenderer(myRenderer);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myBuilder = new MyBuilder(structure);
    myBuilder.setFilteringMerge(300, null);
    Disposer.register(this, myBuilder);

    setLayout(new BorderLayout());

    myTree.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        myBuilder.revalidateTree();
      }

      @Override
      public void componentMoved(final ComponentEvent e) {
        myBuilder.revalidateTree();
      }

      @Override
      public void componentShown(final ComponentEvent e) {
        myBuilder.revalidateTree();
      }
    });

    add(new StickySeparator(myTree), BorderLayout.CENTER);

    mySelection = new MergingUpdateQueue("OptionsTree", 150, false, this, this, this).setRestartTimerOnAdd(true);
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        final TreePath path = e.getNewLeadSelectionPath();
        if (path == null) {
          queueSelection(null);
        }
        else {
          final Base base = extractNode(path.getLastPathComponent());
          queueSelection(base != null ? base.getConfigurable() : null);
        }
      }
    });
  }

  ActionCallback select(@Nullable Configurable configurable) {
    return queueSelection(configurable);
  }

  public void selectFirst() {
    for (ConfigurableGroup eachGroup : myGroups) {
      final Configurable[] kids = eachGroup.getConfigurables();
      if (kids.length > 0) {
        queueSelection(kids[0]);
        return;
      }
    }
  }

  private Configurable myQueuedConfigurable;

  ActionCallback queueSelection(final Configurable configurable) {
    if (myBuilder.isSelectionBeingAdjusted()) {
      return ActionCallback.REJECTED;
    }

    final ActionCallback callback = new ActionCallback();

    myQueuedConfigurable = configurable;
    final Update update = new Update(this) {
      public void run() {
        if (configurable != myQueuedConfigurable) return;

        if (configurable == null) {
          myTree.getSelectionModel().clearSelection();
          myFilter.myContext.fireSelected(null, OptionsTree.this);
        }
        else {
          myBuilder.getReady(this).doWhenDone(new Runnable() {
            @Override
            public void run() {
              if (configurable != myQueuedConfigurable) return;

              final EditorNode editorNode = myConfigurable2Node.get(configurable);
              FilteringTreeStructure.FilteringNode editorUiNode = myBuilder.getVisibleNodeFor(editorNode);
              if (editorUiNode == null) return;

              if (!myBuilder.getSelectedElements().contains(editorUiNode)) {
                myBuilder.select(editorUiNode, new Runnable() {
                  public void run() {
                    fireSelected(configurable, callback);
                  }
                });
              } else {
                myBuilder.scrollSelectionToVisible(new Runnable() {
                  public void run() {
                    fireSelected(configurable, callback);
                  }
                }, false);
              }
            }
          });
        }
      }

      @Override
      public void setRejected() {
        super.setRejected();
        callback.setRejected();
      }
    };
    mySelection.queue(update);
    return callback;
  }

  private void fireSelected(Configurable configurable, final ActionCallback callback) {
    myFilter.myContext.fireSelected(configurable, this).doWhenProcessed(callback.createSetDoneRunnable());
  }


  public JTree getTree() {
    return myTree;
  }

  public List<Configurable> getPathToRoot(final Configurable configurable) {
    final ArrayList<Configurable> path = new ArrayList<Configurable>();

    EditorNode eachNode = myConfigurable2Node.get(configurable);
    if (eachNode == null) return path;

    while (eachNode != null) {
      path.add(eachNode.getConfigurable());
      final SimpleNode parent = eachNode.getParent();
      if (parent instanceof EditorNode) {
        eachNode = (EditorNode)parent;
      }
      else {
        break;
      }
    }

    return path;
  }

  public SimpleNode findNodeFor(final Configurable toSelect) {
    return myConfigurable2Node.get(toSelect);
  }

  @Nullable
  public <T extends Configurable> T findConfigurable(Class<T> configurableClass) {
    for (Configurable configurable : myConfigurable2Node.keySet()) {
      if (configurableClass.isInstance(configurable)) {
        return configurableClass.cast(configurable);
      }
    }
    return null;
  }

  @Nullable
  public SearchableConfigurable findConfigurableById(@NotNull String configurableId) {
    for (Configurable configurable : myConfigurable2Node.keySet()) {
      if (configurable instanceof SearchableConfigurable) {
        SearchableConfigurable searchableConfigurable = (SearchableConfigurable) configurable;
        if (configurableId.equals(searchableConfigurable.getId())) {
          return searchableConfigurable;
        }
      }
    }
    return null;
  }

  class Renderer extends GroupedElementsRenderer.Tree {
    private GroupSeparator mySeparator;
    private JLabel myProjectIcon;
    private JLabel myHandle;

    @Override
    protected void layout() {
      myRendererComponent.setOpaqueActive(false);

      mySeparator = new GroupSeparator();
      myRendererComponent.add(Registry.is("ide.new.settings.dialog") ? mySeparator : mySeparatorComponent, BorderLayout.NORTH);

      final NonOpaquePanel content = new NonOpaquePanel(new BorderLayout());
      myHandle = new JLabel("", SwingConstants.CENTER);
      if (!SystemInfo.isMac) {
        myHandle.setBorder(new EmptyBorder(0, 2, 0, 2));
      }
      myHandle.setOpaque(false);
      content.add(myHandle, BorderLayout.WEST);
      content.add(myComponent, BorderLayout.CENTER);
      myProjectIcon = new JLabel(" ", SwingConstants.LEFT);
      myProjectIcon.setOpaque(true);
      content.add(myProjectIcon, BorderLayout.EAST);
      myRendererComponent.add(content, BorderLayout.CENTER);
    }

    public Component getTreeCellRendererComponent(final JTree tree,
                                                  final Object value,
                                                  final boolean selected,
                                                  final boolean expanded,
                                                  final boolean leaf,
                                                  final int row,
                                                  final boolean hasFocus) {


      JComponent result;
      Color fg = UIUtil.getTreeTextForeground();
      mySeparator.configure(null, false);
      final Base base = extractNode(value);
      if (base instanceof EditorNode) {

        final EditorNode editor = (EditorNode)base;
        ConfigurableGroup group = null;
        if (editor.getParent() == myRoot) {
          final DefaultMutableTreeNode prevValue = ((DefaultMutableTreeNode)value).getPreviousSibling();
          if (prevValue == null || prevValue instanceof LoadingNode) {
            group = editor.getGroup();
            mySeparator.configure(group, false);
          }
          else {
            final Base prevBase = extractNode(prevValue);
            if (prevBase instanceof EditorNode) {
              final EditorNode prevEditor = (EditorNode)prevBase;
              if (prevEditor.getGroup() != editor.getGroup()) {
                group = editor.getGroup();
                mySeparator.configure(group, true);
              }
            }
          }
        }

        TreePath path = tree.getPathForRow(row);
        if (path == null) {
          if (value instanceof DefaultMutableTreeNode) {
            path = new TreePath(((DefaultMutableTreeNode)value).getPath());
          }
        }

        final boolean toStretch = tree.isVisible() && path != null;

        int forcedWidth = 2000;
        if (toStretch) {
          final Rectangle visibleRect = tree.getVisibleRect();

          int nestingLevel = tree.isRootVisible() ? path.getPathCount() - 1 : path.getPathCount() - 2;

          final int left = UIUtil.getTreeLeftChildIndent();
          final int right = UIUtil.getTreeRightChildIndent();

          final Insets treeInsets = tree.getInsets();

          int indent = (left + right) * nestingLevel + (treeInsets != null ? treeInsets.left + treeInsets.right : 0);

          forcedWidth = visibleRect.width > 0 ? visibleRect.width - indent : forcedWidth;
        }

        result = configureComponent(base.getText(), base.getText(), null, null, row == -1 || selected, group != null,
                                    group != null ? group.getDisplayName() : null, forcedWidth - 4);


        if (base.isError()) {
          fg = JBColor.red;
        }
        else if (base.isModified()) {
          fg = JBColor.blue;
        }

      }
      else {
        result = configureComponent(value.toString(), null, null, null, selected, false, null, -1);
      }

      if (value instanceof DefaultMutableTreeNode) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        TreePath nodePath = new TreePath(node.getPath());
        myHandle.setIcon(((SimpleTree)tree).getHandleIcon(node, nodePath));
      } else {
        myHandle.setIcon(null);
      }


      myTextLabel.setForeground(selected ? UIUtil.getTreeSelectionForeground() : fg);

      myTextLabel.setOpaque(selected);
      if (Registry.is("ide.new.settings.dialog")) {
        myTextLabel.setBorder(new EmptyBorder(1,2,1,0));
      }

      Project project = null;
      if (base != null && Registry.is("ide.new.settings.dialog")) {
        SimpleNode parent = base.getParent();
        if (parent == myRoot) {
          project = getConfigurableProject(base); // show icon for top-level nodes
        }
      }
      if (project != null) {
        myProjectIcon.setBackground(selected ? getSelectionBackground() : getBackground());
        myProjectIcon.setIcon(selected ? AllIcons.General.ProjectConfigurableSelected : AllIcons.General.ProjectConfigurable);
        myProjectIcon.setVisible(true);
        myProjectIcon.setToolTipText(OptionsBundle.message(project.isDefault()
                                                  ? "configurable.default.project.tooltip"
                                                  : "configurable.current.project.tooltip"));
      } else {
        myProjectIcon.setVisible(false);
      }
      if (Registry.is("ide.new.settings.dialog")) {
        result.setBackground(selected ? UIUtil.getTreeSelectionBackground() : UIUtil.SIDE_PANEL_BACKGROUND);
      }
      return result;
    }

    protected JComponent createItemComponent() {
      myTextLabel = new ErrorLabel();
      return myTextLabel;
    }

    public boolean isUnderHandle(final Point point) {
      final Point handlePoint = SwingUtilities.convertPoint(myRendererComponent, point, myHandle);
      final Rectangle bounds = myHandle.getBounds();
      return bounds.x < handlePoint.x && bounds.getMaxX() >= handlePoint.x;
    }
  }

  @Nullable
  private Base extractNode(Object object) {
    if (object instanceof DefaultMutableTreeNode) {
      final DefaultMutableTreeNode uiNode = (DefaultMutableTreeNode)object;
      final Object o = uiNode.getUserObject();
      if (o instanceof FilteringTreeStructure.FilteringNode) {
        return (Base)((FilteringTreeStructure.FilteringNode)o).getDelegate();
      }
    }

    return null;
  }

  abstract static class Base extends CachingSimpleNode {

    protected Base(final SimpleNode aParent) {
      super(aParent);
    }

    String getText() {
      return null;
    }

    boolean isModified() {
      return false;
    }

    boolean isError() {
      return false;
    }

    Configurable getConfigurable() {
      return null;
    }
  }

  class Root extends Base {

    Root() {
      super(null);
    }

    protected SimpleNode[] buildChildren() {
      List<SimpleNode> result = new ArrayList<SimpleNode>();
      for (ConfigurableGroup eachGroup : myGroups) {
        result.addAll(buildGroup(eachGroup));
      }

      return result.isEmpty() ? NO_CHILDREN : result.toArray(new SimpleNode[result.size()]);
    }

    private List<EditorNode> buildGroup(final ConfigurableGroup eachGroup) {
      List<EditorNode> result = new ArrayList<EditorNode>();
      final Configurable[] kids = eachGroup.getConfigurables();
      if (kids.length > 0) {
        for (Configurable eachKid : kids) {
          if (!isInvisibleNode(eachKid)) {
            result.add(new EditorNode(this, eachKid, eachGroup));
          }
        }
      }
      return sort(result);
    }
  }

  private static boolean isInvisibleNode(final Configurable child) {
    return child instanceof SearchableConfigurable.Parent && !((SearchableConfigurable.Parent)child).isVisible();
  }

  private static List<EditorNode> sort(List<EditorNode> c) {
    List<EditorNode> cc = new ArrayList<EditorNode>(c);
    Collections.sort(cc, new Comparator<EditorNode>() {
      public int compare(final EditorNode o1, final EditorNode o2) {
        return getConfigurableDisplayName(o1.getConfigurable()).compareToIgnoreCase(getConfigurableDisplayName(o2.getConfigurable()));
      }
    });
    return cc;
  }

  private static String getConfigurableDisplayName(final Configurable c) {
    final String name = c.getDisplayName();
    return name != null ? name : "{ Unnamed Page:" + c.getClass().getSimpleName() + " }";
  }

  private List<EditorNode> buildChildren(final Configurable configurable, SimpleNode parent, final ConfigurableGroup group) {
    if (configurable instanceof Configurable.Composite) {
      final Configurable[] kids = ((Configurable.Composite)configurable).getConfigurables();
      final List<EditorNode> result = new ArrayList<EditorNode>(kids.length);
      for (Configurable child : kids) {
        result.add(new EditorNode(parent, child, group));
        myFilter.myContext.registerKid(configurable, child);
      }
      return result; // TODO: DECIDE IF INNERS SHOULD BE SORTED: sort(result);
    }
    else {
      return Collections.emptyList();
    }
  }

  private static final EditorNode[] EMPTY_EN_ARRAY = new EditorNode[0];
  class EditorNode extends Base {
    Configurable myConfigurable;
    ConfigurableGroup myGroup;

    EditorNode(SimpleNode parent, Configurable configurable, @Nullable ConfigurableGroup group) {
      super(parent);
      myConfigurable = configurable;
      myGroup = group;
      myConfigurable2Node.put(configurable, this);
      addPlainText(getConfigurableDisplayName(configurable));
    }

    protected EditorNode[] buildChildren() {
      List<EditorNode> list = OptionsTree.this.buildChildren(myConfigurable, this, null);
      return list.isEmpty() ? EMPTY_EN_ARRAY : list.toArray(new EditorNode[list.size()]);
    }


    @Override
    public boolean isAlwaysLeaf() {
      return !(myConfigurable instanceof Configurable.Composite);
    }

    @Override
    public boolean isContentHighlighted() {
      return getParent() == myRoot;
    }

    @Override
    Configurable getConfigurable() {
      return myConfigurable;
    }

    @Override
    public int getWeight() {
      if (getParent() == myRoot) {
        return Integer.MAX_VALUE - myGroups.indexOf(myGroup);
      }
      else {
        return WeightBasedComparator.UNDEFINED_WEIGHT;
      }
    }

    public ConfigurableGroup getGroup() {
      return myGroup;
    }

    @Override
    String getText() {
      return getConfigurableDisplayName(myConfigurable).replace("\n", " ");
    }

    @Override
    boolean isModified() {
      return myFilter.myContext.getModified().contains(myConfigurable);
    }

    @Override
    boolean isError() {
      return myFilter.myContext.getErrors().containsKey(myConfigurable);
    }
  }

  public void dispose() {
    myQueuedConfigurable = null;
  }

  public ActionCallback onSelected(final Configurable configurable, final Configurable oldConfigurable) {
    return queueSelection(configurable);
  }

  public ActionCallback onModifiedAdded(final Configurable colleague) {
    myTree.repaint();
    return ActionCallback.DONE;
  }

  public ActionCallback onModifiedRemoved(final Configurable configurable) {
    myTree.repaint();
    return ActionCallback.DONE;
  }

  public ActionCallback onErrorsChanged() {
    return ActionCallback.DONE;
  }

  public void processTextEvent(KeyEvent e) {
    myTree.processKeyEvent(e);
  }

  private class MyTree extends SimpleTree {

    private MyTree() {
      getInputMap().clear();
      setOpaque(true);
    }

    @Override
    public final String getToolTipText(MouseEvent event) {
      if (event != null) {
        Point point = event.getPoint();
        Component component = getDeepestRendererComponentAt(point.x, point.y);
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
    public void setUI(final TreeUI ui) {
      TreeUI actualUI = ui;
      if (!(ui instanceof MyTreeUi)) {
        actualUI = new MyTreeUi();
      }
      super.setUI(actualUI);
    }

    @Override
    protected boolean isCustomUI() {
      return true;
    }

    @Override
    protected void configureUiHelper(final TreeUIHelper helper) {
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return true;
    }


    @Override
    public void processKeyEvent(final KeyEvent e) {
      TreePath path = myTree.getSelectionPath();
      if (path != null) {
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
          if (isExpanded(path)) {
            collapsePath(path);
            return;
          }
        } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
          if (isCollapsed(path)) {
            expandPath(path);
            return;
          }
        }
      }

      super.processKeyEvent(e);
    }

    @Override
    protected void processMouseEvent(final MouseEvent e) {
      final MyTreeUi ui = (MyTreeUi)myTree.getUI();
      final boolean toggleNow =
        e.getID() == MouseEvent.MOUSE_RELEASED && UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED) && !ui.isToggleEvent(e);

      final boolean toggleLater =
        e.getID() == MouseEvent.MOUSE_PRESSED;

      if (toggleNow || toggleLater) {
        final TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          final Rectangle bounds = getPathBounds(path);
          if (bounds != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
            final boolean selected = isPathSelected(path);
            final boolean expanded = isExpanded(path);
            final Component comp =
              myRenderer.getTreeCellRendererComponent(this, node, selected, expanded, node.isLeaf(), getRowForPath(path), isFocusOwner());

            comp.setBounds(bounds);
            comp.validate();

            Point point = new Point(e.getX() - bounds.x, e.getY() - bounds.y);
            if (myRenderer.isUnderHandle(point)) {
              if (toggleNow) {
                ui.toggleExpandState(path);
                e.consume();
                return;
              } else if (toggleLater) {
                e.consume();
                return;
              }
            }
          }
        }
      }

      super.processMouseEvent(e);
    }

    private class MyTreeUi extends BasicTreeUI {

      @Override
      public void toggleExpandState(final TreePath path) {
        super.toggleExpandState(path);
      }

      @Override
      public boolean isToggleEvent(final MouseEvent event) {
        return super.isToggleEvent(event);
      }

      @Override
      protected boolean shouldPaintExpandControl(final TreePath path,
                                                 final int row,
                                                 final boolean isExpanded,
                                                 final boolean hasBeenExpanded,
                                                 final boolean isLeaf) {
        return false;
      }

      @Override
      protected void paintHorizontalPartOfLeg(final Graphics g,
                                              final Rectangle clipBounds,
                                              final Insets insets,
                                              final Rectangle bounds,
                                              final TreePath path,
                                              final int row,
                                              final boolean isExpanded,
                                              final boolean hasBeenExpanded,
                                              final boolean isLeaf) {

      }

      @Override
      protected void paintVerticalPartOfLeg(final Graphics g, final Rectangle clipBounds, final Insets insets, final TreePath path) {
      }

      @Override
      public void paint(Graphics g, JComponent c) {
        GraphicsUtil.setupAntialiasing(g);
        super.paint(g, c);
      }
    }
  }

  private class MyBuilder extends FilteringTreeBuilder {

    List<Object> myToExpandOnResetFilter;
    boolean myRefilteringNow;
    boolean myWasHoldingFilter;

    public MyBuilder(SimpleTreeStructure structure) {
      super(myTree, myFilter, structure, new WeightBasedComparator(false));
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
    protected boolean isSelectable(final Object nodeObject) {
      return nodeObject instanceof EditorNode;
    }

    @Override
    public boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
      return myFilter.myContext.isHoldingFilter();
    }

    @Override
    public boolean isToEnsureSelectionOnFocusGained() {
      return false;
    }

    @Override
    protected ActionCallback refilterNow(Object preferredSelection, boolean adjustSelection) {
      final List<Object> toRestore = new ArrayList<Object>();
      if (myFilter.myContext.isHoldingFilter() && !myWasHoldingFilter && myToExpandOnResetFilter == null) {
        myToExpandOnResetFilter = myBuilder.getUi().getExpandedElements();
      } else if (!myFilter.myContext.isHoldingFilter() && myWasHoldingFilter && myToExpandOnResetFilter != null) {
        toRestore.addAll(myToExpandOnResetFilter);
        myToExpandOnResetFilter = null;
      }

      myWasHoldingFilter = myFilter.myContext.isHoldingFilter();

      ActionCallback result = super.refilterNow(preferredSelection, adjustSelection);
      myRefilteringNow = true;
      return result.doWhenDone(new Runnable() {
        public void run() {
          myRefilteringNow = false;
          if (!myFilter.myContext.isHoldingFilter() && getSelectedElements().isEmpty()) {
            restoreExpandedState(toRestore);
          }
        }
      });
    }

    private void restoreExpandedState(List<Object> toRestore) {
      TreePath[] selected = myTree.getSelectionPaths();
      if (selected == null) {
        selected = new TreePath[0];
      }

      List<TreePath> toCollapse = new ArrayList<TreePath>();

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

  Project getConfigurableProject(Configurable configurable) {
    if (configurable instanceof ConfigurableWrapper) {
      ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
      return wrapper.getExtensionPoint().getProject();
    }
    return getConfigurableProject(myConfigurable2Node.get(configurable));
  }

  private static Project getConfigurableProject(SimpleNode node) {
    if (node == null) {
      return null;
    }
    if (node instanceof EditorNode) {
      EditorNode editor = (EditorNode)node;
      Configurable configurable = editor.getConfigurable();
      if (configurable instanceof ConfigurableWrapper) {
        ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
        return wrapper.getExtensionPoint().getProject();
      }
    }
    return getConfigurableProject(node.getParent());
  }

  private static final class GroupSeparator extends JLabel {
    public static final int SPACE = 10;

    public GroupSeparator() {
      setFont(UIUtil.getLabelFont());
      setFont(getFont().deriveFont(Font.BOLD));
    }

    public void configure(ConfigurableGroup group, boolean isSpaceNeeded) {
      if (group == null) {
        setVisible(false);
      }
      else {
        setVisible(true);
        int bottom = UIUtil.isUnderNativeMacLookAndFeel() ? 1 : 3;
        int top = isSpaceNeeded
                  ? bottom + SPACE
                  : bottom;
        setBorder(BorderFactory.createEmptyBorder(top, 3, bottom, 3));
        setText(group.getDisplayName());
      }
    }
  }

  private static final class StickySeparator extends JComponent {
    private final SimpleTree myTree;
    private final JScrollPane myScroller;
    private final GroupSeparator mySeparator;

    public StickySeparator(SimpleTree tree) {
      myTree = tree;
      myScroller = ScrollPaneFactory.createScrollPane(myTree);
      myScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      mySeparator = new GroupSeparator();
      add(myScroller);
    }

    @Override
    public void doLayout() {
      myScroller.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    public void paint(Graphics g) {
      super.paint(g);

      if (Registry.is("ide.new.settings.dialog")) {
        ConfigurableGroup group = getGroup(GroupSeparator.SPACE + mySeparator.getFont().getSize());
        if (group != null && group == getGroup(-GroupSeparator.SPACE)) {
          mySeparator.configure(group, false);

          Rectangle bounds = myScroller.getViewport().getBounds();
          int height = mySeparator.getPreferredSize().height;
          if (bounds.height > height) {
            bounds.height = height;
          }
          g.setColor(myTree.getBackground());
          if (g instanceof Graphics2D) {
            int h = bounds.height / 3;
            int y = bounds.y + bounds.height - h;
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height - h);
            ((Graphics2D)g).setPaint(UIUtil.getGradientPaint(
              0, y, g.getColor(),
              0, y + h, ColorUtil.toAlpha(g.getColor(), 0)));
            g.fillRect(bounds.x, y, bounds.width, h);
          }
          else {
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
          }
          mySeparator.setSize(bounds.width - 1, bounds.height);
          mySeparator.paint(g.create(bounds.x + 1, bounds.y, bounds.width - 1, bounds.height));
        }
      }
    }

    private ConfigurableGroup getGroup(int offset) {
      TreePath path = myTree.getClosestPathForLocation(-myTree.getX(), -myTree.getY() + offset);
      SimpleNode node = myTree.getNodeFor(path);
      if (node instanceof FilteringTreeStructure.FilteringNode) {
        Object delegate = ((FilteringTreeStructure.FilteringNode)node).getDelegate();
        while (delegate instanceof EditorNode) {
          EditorNode editor = (EditorNode)delegate;
          ConfigurableGroup group = editor.getGroup();
          if (group != null) {
            return group;
          }
          delegate = editor.getParent();
        }
      }
      return null;
    }
  }
}
