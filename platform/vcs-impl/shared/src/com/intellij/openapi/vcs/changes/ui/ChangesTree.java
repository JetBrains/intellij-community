// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.dnd.DnDAware;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider;
import com.intellij.openapi.vcs.changes.InclusionListener;
import com.intellij.openapi.vcs.changes.InclusionModel;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.platform.vcs.VcsUtil;
import com.intellij.platform.vcs.changes.ChangesUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.tree.ui.DefaultTreeUI;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UpdateScaleHelper;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.TreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.*;
import static com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*;
import static com.intellij.ui.tree.TreePathUtil.toTreePathArray;
import static com.intellij.util.ui.ThreeStateCheckBox.State;

/**
 * Consider implementing {@link AsyncChangesTree} instead.
 */
public abstract class ChangesTree extends Tree implements UiCompatibleDataProvider {
  private static final Logger LOG = Logger.getInstance(ChangesTree.class);

  @ApiStatus.Internal public static final @NonNls String LOG_COMMIT_SESSION_EVENTS = "LogCommitSessionEvents";

  public static final int EXPAND_NODES_THRESHOLD = 30000;

  public static final @NotNull TreeStateStrategy<?> DO_NOTHING = new DoNothingTreeStateStrategy();
  public static final @NotNull TreeStateStrategy<?> ALWAYS_RESET = new AlwaysResetTreeStateStrategy();
  public static final @NotNull TreeStateStrategy<?> ALWAYS_KEEP = new AlwaysKeepTreeStateStrategy();
  public static final @NotNull TreeStateStrategy<?> KEEP_NON_EMPTY = new KeepNonEmptyTreeStateStrategy();
  public static final @NotNull TreeStateStrategy<?> KEEP_SELECTED_OBJECTS = new KeepSelectedObjectsStrategy();

  protected final @NotNull Project myProject;
  protected final boolean myShowConflictsNode;
  private boolean myShowCheckboxes;
  private final boolean myHighlightProblems;
  private final int myCheckboxWidth;
  private final @NotNull ChangesGroupingSupport myGroupingSupport;
  private boolean myIsModelFlat;

  private @NotNull InclusionModel myInclusionModel = new DefaultInclusionModel();
  private final @NotNull InclusionListener myInclusionModelListener = () -> {
    notifyInclusionListener();
    repaint();
  };
  private @Nullable Runnable myTreeInclusionListener;

  private final @NotNull ChangesTreeHandlers myHandlers;
  private @NotNull TreeStateStrategy<?> myTreeStateStrategy = ALWAYS_RESET;
  private boolean myScrollToSelection = true;

  @Deprecated private static final @NonNls String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";
  protected static final @NonNls String GROUPING_KEYS = "ChangesTree.GroupingKeys";

  public static final List<String> DEFAULT_GROUPING_KEYS = List.of(DIRECTORY_GROUPING, MODULE_GROUPING, REPOSITORY_GROUPING);

  @Language("devkit-action-id")
  public static final @NonNls String GROUP_BY_ACTION_GROUP = "ChangesView.GroupBy";

  private final @NotNull CopyProvider myTreeCopyProvider;
  private @NotNull TreeExpander myTreeExpander = new MyTreeExpander();

  private boolean myModelUpdateInProgress;
  private AWTEvent myEventProcessingInProgress;

  private final UpdateScaleHelper scaleHelper = new UpdateScaleHelper();

  public ChangesTree(@NotNull Project project, boolean showCheckboxes, boolean highlightProblems) {
    this(project, showCheckboxes, highlightProblems, true, false);
  }

  protected ChangesTree(@NotNull Project project,
                        boolean showCheckboxes,
                        boolean highlightProblems,
                        boolean withSpeedSearch,
                        boolean showConflictsNode) {
    super(ChangesBrowserNode.createRoot());
    myProject = project;
    myShowCheckboxes = showCheckboxes;
    myHighlightProblems = highlightProblems;
    myShowConflictsNode = showConflictsNode;
    myCheckboxWidth = new JCheckBox().getPreferredSize().width;
    myInclusionModel.addInclusionListener(myInclusionModelListener);
    myHandlers = new ChangesTreeHandlers(this);

    setRootVisible(false);
    setShowsRootHandles(true);
    setOpaque(false);
    if (withSpeedSearch) {
      TreeSpeedSearch.installOn(this, false, ChangesBrowserNode.TO_TEXT_CONVERTER);
    }

    final ChangesBrowserNodeRenderer nodeRenderer = new ChangesBrowserNodeRenderer(myProject, this::isShowFlatten, highlightProblems);
    setCellRenderer(new ChangesTreeCellRenderer(nodeRenderer));

    new MyToggleSelectionAction().registerCustomShortcutSet(this, null);
    installCheckBoxClickHandler();

    installTreeLinkHandler(nodeRenderer);
    SmartExpander.installOn(this);

    myGroupingSupport = installGroupingSupport();

    setEmptyText(DiffBundle.message("diff.count.differences.status.text", 0));

    myTreeCopyProvider = new ChangesBrowserNodeCopyProvider(this);

    installCommitSessionEventsListeners();

    if (Registry.is("vcs.changes.tree.use.fixed.height.renderer")) {
      putClientProperty(DefaultTreeUI.LARGE_MODEL_ALLOWED, true);
      setLargeModel(true);

      updateFixedRowHeight();
    }

    getAccessibleContext().setAccessibleName(VcsBundle.message("changes.tree.accessible.name"));
  }

  private void updateFixedRowHeight() {
    if (!isLargeModel()) return;

    int fixedRowHeight = UIManager.getInt(JBUI.CurrentTheme.Tree.rowHeightKey());
    if (fixedRowHeight > 0) return; // leave hardcoded value from BasicTreeUI.installDefaults

    TreeCellRenderer renderer = getCellRenderer();
    if (renderer == null) return;

    ChangesBrowserNode<?> sampleNode = new FixedHeightSampleChangesBrowserNode();
    Component component = renderer.getTreeCellRendererComponent(this, sampleNode, true, true, true, 0, true);
    scaleHelper.saveScaleAndRunIfChanged(() -> {
      if (component instanceof JComponent) scaleHelper.updateUIForAll((JComponent)component);
    });
    int rendererHeight = component.getPreferredSize().height;
    if (rendererHeight <= 0) return;

    setRowHeight(rendererHeight);
  }

  @Override
  public void setUI(TreeUI ui) {
    super.setUI(ui);
    updateFixedRowHeight();
  }

  /**
   * There is special logic for {@link DnDAware} components in
   * {@link IdeGlassPaneImpl#dispatch(AWTEvent)} that doesn't call
   * {@link Component#processMouseEvent(MouseEvent)} in case of mouse clicks over selection.
   * <p>
   * So we add "checkbox mouse clicks" handling as a listener.
   */
  private void installCheckBoxClickHandler() {
    ClickListener handler = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        TreePath path = getPathIfCheckBoxClicked(event.getPoint());
        if (path == null) return false;

        setSelectionPath(path);
        List<Object> selected = getIncludableUserObjects(selected(ChangesTree.this));
        boolean exclude = toggleChanges(selected);
        logInclusionToggleEvents(exclude, event);
        return true;
      }
    };
    handler.installOn(this);
  }

  @Nullable
  TreePath getPathIfCheckBoxClicked(@NotNull Point p) {
    if (!myShowCheckboxes || !isEnabled()) return null;

    TreePath path = getPathForLocation(p.x, p.y);
    if (path == null) return null;
    if (!isIncludable(path)) return null;

    Rectangle pathBounds = getPathBounds(path);
    if (pathBounds == null) return null;

    Rectangle checkBoxBounds = pathBounds.getBounds();
    checkBoxBounds.setSize(myCheckboxWidth, checkBoxBounds.height);
    if (!checkBoxBounds.contains(p)) return null;

    return path;
  }

  protected void installTreeLinkHandler(@NotNull ChangesBrowserNodeRenderer nodeRenderer) {
    new TreeLinkMouseListener(nodeRenderer) {
      @Override
      protected int getRendererRelativeX(@NotNull MouseEvent e, @NotNull JTree tree, @NotNull TreePath path) {
        int x = super.getRendererRelativeX(e, tree, path);
        if (myShowCheckboxes && isIncludable(path)) {
          x -= myCheckboxWidth;
        }
        return x;
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        if (!isEmpty()) { // apply only if tree is not empty - otherwise "getEmptyText()" should handle the case
          super.mouseMoved(e);
        }
      }
    }.installOn(this);
  }

  /**
   * @see #installGroupingSupport(ChangesTree, ChangesGroupingSupport, Supplier, Consumer)
   */
  protected @NotNull ChangesGroupingSupport installGroupingSupport() {
    ChangesGroupingSupport result = new ChangesGroupingSupport(myProject, this, myShowConflictsNode);

    migrateShowFlattenSetting();
    installGroupingSupport(this, result, GROUPING_KEYS, DEFAULT_GROUPING_KEYS);

    return result;
  }

  protected static void installGroupingSupport(@NotNull ChangesTree tree,
                                               @NotNull ChangesGroupingSupport groupingSupport,
                                               @NotNull @NonNls String propertyName,
                                               @NonNls List<String> defaultGroupingKeys) {
    installGroupingSupport(tree, groupingSupport,
                           () -> {
                             List<String> storedList = PropertiesComponent.getInstance(tree.getProject()).getList(propertyName);
                             return Objects.requireNonNullElse(storedList, defaultGroupingKeys);
                           },
                           newValue -> PropertiesComponent.getInstance(tree.getProject()).setList(propertyName, newValue));
  }

  protected static void installGroupingSupport(@NotNull ChangesTree tree,
                                               @NotNull ChangesGroupingSupport groupingSupport,
                                               @NotNull Supplier<? extends Collection<String>> settingsGetter,
                                               @NotNull Consumer<? super Collection<String>> settingsSetter) {
    installGroupingSupport(groupingSupport, settingsGetter, settingsSetter, () -> tree.onGroupingChanged());
  }

  public static void installGroupingSupport(@NotNull ChangesGroupingSupport groupingSupport,
                                            @NotNull Supplier<? extends Collection<String>> settingsGetter,
                                            @NotNull Consumer<? super Collection<String>> settingsSetter,
                                            @NotNull Runnable refresh) {
    groupingSupport.setGroupingKeysOrSkip(settingsGetter.get());
    groupingSupport.addPropertyChangeListener(e -> {
      settingsSetter.accept(ContainerUtil.sorted(groupingSupport.getGroupingKeys()));
      refresh.run();
    });
  }

  private void migrateShowFlattenSetting() {
    PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    if (properties.isValueSet(FLATTEN_OPTION_KEY)) {
      properties.setList(GROUPING_KEYS, properties.isTrueValue(FLATTEN_OPTION_KEY) ? Collections.emptyList() : DEFAULT_GROUPING_KEYS);
      properties.unsetValue(FLATTEN_OPTION_KEY);
    }
  }

  public void setEmptyText(@Nls @NotNull String emptyText) {
    getEmptyText().setText(emptyText);
  }

  public void addSelectionListener(@NotNull Runnable runnable) {
    addSelectionListener(runnable, null);
  }

  public void addSelectionListener(@NotNull Runnable runnable, @Nullable Disposable parent) {
    TreeSelectionListener listener = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        runnable.run();
      }
    };

    addTreeSelectionListener(listener);
    if (parent != null) Disposer.register(parent, () -> removeTreeSelectionListener(listener));
  }

  public void setDoubleClickAndEnterKeyHandler(@NotNull Runnable handler) {
    setDoubleClickHandler(e -> {
      handler.run();
      return true;
    });
    setEnterKeyHandler(e -> {
      handler.run();
      return true;
    });
  }

  public @Nullable Processor<? super MouseEvent> getDoubleClickHandler() {
    return myHandlers.getDoubleClickHandler();
  }

  public void setDoubleClickHandler(@Nullable Processor<? super MouseEvent> handler) {
    myHandlers.setDoubleClickHandler(handler);
  }

  public @Nullable Processor<? super KeyEvent> getEnterKeyHandler() {
    return myHandlers.getEnterKeyHandler();
  }

  public void setEnterKeyHandler(@Nullable Processor<? super KeyEvent> handler) {
    myHandlers.setEnterKeyHandler(handler);
  }

  public void installPopupHandler(ActionGroup group) {
    PopupHandler.installPopupMenu(this, group, "ChangesTreePopup");
  }

  public JComponent getPreferredFocusedComponent() {
    return this;
  }

  /**
   * @see #installGroupingSupport()
   */
  public void addGroupingChangeListener(@NotNull PropertyChangeListener listener) {
    myGroupingSupport.addPropertyChangeListener(listener);
  }

  public void removeGroupingChangeListener(@NotNull PropertyChangeListener listener) {
    myGroupingSupport.removePropertyChangeListener(listener);
  }

  public @NotNull ChangesGroupingSupport getGroupingSupport() {
    return myGroupingSupport;
  }

  public @NotNull ChangesGroupingPolicyFactory getGrouping() {
    return getGroupingSupport().getGrouping();
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public boolean isShowFlatten() {
    return !myGroupingSupport.isDirectory();
  }

  public boolean isShowCheckboxes() {
    return myShowCheckboxes;
  }

  public void setShowCheckboxes(boolean value) {
    boolean oldValue = myShowCheckboxes;
    myShowCheckboxes = value;

    if (oldValue != value) {
      updateFixedRowHeight();
      repaint();
    }
  }

  public boolean isHighlightProblems() {
    return myHighlightProblems;
  }

  private boolean isCurrentModelFlat() {
    boolean isFlat = true;
    Enumeration enumeration = getRoot().depthFirstEnumeration();

    while (isFlat && enumeration.hasMoreElements()) {
      isFlat = ((ChangesBrowserNode<?>)enumeration.nextElement()).getLevel() <= 1;
    }

    return isFlat;
  }

  public void onGroupingChanged() {
    rebuildTree(KEEP_SELECTED_OBJECTS);
  }

  public abstract void rebuildTree();

  public void rebuildTree(@NotNull TreeStateStrategy<?> treeStateStrategy) {
    TreeStateStrategy<?> oldTreeStateStrategy = myTreeStateStrategy;
    myTreeStateStrategy = treeStateStrategy;
    try {
      rebuildTree();
    }
    finally {
      myTreeStateStrategy = oldTreeStateStrategy;
    }
  }

  protected void updateTreeModel(@NotNull DefaultTreeModel model) {
    updateTreeModel(model, myTreeStateStrategy);
  }

  protected void updateTreeModel(@NotNull DefaultTreeModel model,
                                 @SuppressWarnings("rawtypes") @NotNull TreeStateStrategy treeStateStrategy) {
    ThreadingAssertions.assertEventDispatchThread();

    myModelUpdateInProgress = true;
    try {
      Object state = treeStateStrategy.saveState(this);

      setModel(model);
      myIsModelFlat = isCurrentModelFlat();
      setShowsRootHandles(!myGroupingSupport.isNone() || !myIsModelFlat);

      //noinspection unchecked
      treeStateStrategy.restoreState(this, state, myScrollToSelection);
    }
    finally {
      myModelUpdateInProgress = false;
    }
  }

  public boolean isModelUpdateInProgress() {
    return myModelUpdateInProgress;
  }

  public void resetTreeState() {
    // expanding lots of nodes is a slow operation (and result is not very useful)
    if (TreeUtil.hasManyNodes(this, EXPAND_NODES_THRESHOLD)) {
      TreeUtil.collapseAll(this, 1);
      return;
    }

    expandDefaults();

    int selectedTreeRow = -1;

    if (myShowCheckboxes) {
      if (!getIncludedSet().isEmpty()) {
        ChangesBrowserNode root = getRoot();
        Enumeration enumeration = root.depthFirstEnumeration();

        while (enumeration.hasMoreElements()) {
          ChangesBrowserNode node = (ChangesBrowserNode)enumeration.nextElement();
          if (node != root && getNodeStatus(node) == State.NOT_SELECTED) {
            collapsePath(new TreePath(node.getPath()));
          }
        }

        enumeration = root.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
          ChangesBrowserNode node = (ChangesBrowserNode)enumeration.nextElement();
          if (node.isLeaf() && getNodeStatus(node) == State.SELECTED) {
            selectedTreeRow = getRowForPath(new TreePath(node.getPath()));
            break;
          }
        }
      }
    }

    if (selectedTreeRow >= 0) {
      setSelectionRow(selectedTreeRow);
    }
    TreeUtil.showRowCentered(this, selectedTreeRow, false);
  }

  public void selectFile(@Nullable VirtualFile toSelect) {
    if (toSelect == null) return;
    selectFile(VcsUtil.getFilePath(toSelect));
  }

  public void selectFile(@Nullable FilePath toSelect) {
    if (toSelect == null) return;

    int rowInTree = findRowContainingFile(getRoot(), toSelect);
    if (rowInTree == -1) return;

    setSelectionRow(rowInTree);
    TreeUtil.showRowCentered(this, rowInTree, false);
  }

  private int findRowContainingFile(@NotNull TreeNode root, @NotNull FilePath toSelect) {
    TreeNode targetNode = TreeUtil.treeNodeTraverser(root).traverse(TreeTraversal.POST_ORDER_DFS).find(node -> {
      if (node instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
        if (userObject instanceof Change) {
          return matches((Change)userObject, toSelect);
        }
      }

      return false;
    });
    if (targetNode != null) {
      return TreeUtil.getRowForNode(this, (DefaultMutableTreeNode)targetNode);
    }
    else {
      return -1;
    }
  }

  private static boolean matches(@NotNull Change change, @NotNull FilePath toSelect) {
    return toSelect.equals(ChangesUtil.getAfterPath(change)) || toSelect.equals(ChangesUtil.getBeforePath(change));
  }

  public @NotNull ChangesBrowserNode<?> getRoot() {
    return (ChangesBrowserNode<?>)getModel().getRoot();
  }

  public @NotNull InclusionModel getInclusionModel() {
    return myInclusionModel;
  }

  public void setInclusionModel(@Nullable InclusionModel inclusionModel) {
    myInclusionModel.removeInclusionListener(myInclusionModelListener);
    myInclusionModel = inclusionModel != null ? inclusionModel : NullInclusionModel.INSTANCE;
    myInclusionModel.addInclusionListener(myInclusionModelListener);
  }

  public void setInclusionListener(@Nullable Runnable runnable) {
    myTreeInclusionListener = runnable;
  }

  private void notifyInclusionListener() {
    if (myTreeInclusionListener != null) myTreeInclusionListener.run();
  }

  /**
   * If called during component initialization, should be followed by {@link #resetTreeState()} or {@link #rebuildTree()}
   * to update initial selection.
   */
  public void setIncludedChanges(@NotNull Collection<?> changes) {
    getInclusionModel().setInclusion(changes);
  }

  public void includeChange(@NotNull Object change) {
    includeChanges(Collections.singleton(change));
  }

  public void includeChanges(@NotNull Collection<?> changes) {
    getInclusionModel().addInclusion(changes);
  }

  public void excludeChange(@NotNull Object change) {
    excludeChanges(Collections.singleton(change));
  }

  public void excludeChanges(@NotNull Collection<?> changes) {
    getInclusionModel().removeInclusion(changes);
  }

  protected boolean toggleChanges(@NotNull Collection<?> changes) {
    boolean hasExcluded = false;
    for (Object item : changes) {
      if (getInclusionModel().getInclusionState(item) != State.SELECTED) {
        hasExcluded = true;
        break;
      }
    }

    if (hasExcluded) {
      includeChanges(changes);
      return false;
    }
    else {
      excludeChanges(changes);
      return true;
    }
  }

  public boolean isIncluded(@NotNull Object change) {
    return getInclusionModel().getInclusionState(change) != State.NOT_SELECTED;
  }

  public @NotNull Set<Object> getIncludedSet() {
    return getInclusionModel().getInclusion();
  }

  public void expandAll() {
    TreeUtil.expandAll(this);
  }

  public void expandDefaults() {
    // expanding lots of nodes is a slow operation (and result is not very useful)
    if (TreeUtil.hasManyNodes(this, 30000)) {
      return;
    }
    TreeUtil.promiseExpand(this, path -> {
      Object node = path.getLastPathComponent();
      if (node instanceof ChangesBrowserNode && !((ChangesBrowserNode<?>)node).shouldExpandByDefault()) {
        return TreeVisitor.Action.SKIP_CHILDREN;
      }
      return TreeVisitor.Action.CONTINUE;
    });
  }

  public @NotNull TreeExpander getTreeExpander() {
    return myTreeExpander;
  }

  public void setTreeExpander(@NotNull TreeExpander expander) {
    myTreeExpander = expander;
  }

  /**
   * @deprecated Prefer using {@link IdeActions#ACTION_EXPAND_ALL}
   */
  @Deprecated
  public @NotNull AnAction createExpandAllAction(boolean headerAction) {
    if (headerAction) {
      return CommonActionsManager.getInstance().createExpandAllHeaderAction(myTreeExpander, this);
    }
    else {
      return CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this);
    }
  }

  /**
   * @deprecated Prefer using {@link IdeActions#ACTION_COLLAPSE_ALL}
   */
  @Deprecated
  public @NotNull AnAction createCollapseAllAction(boolean headerAction) {
    if (headerAction) {
      return CommonActionsManager.getInstance().createCollapseAllHeaderAction(myTreeExpander, this);
    }
    else {
      return CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, this);
    }
  }

  private class MyTreeExpander extends DefaultTreeExpander {
    MyTreeExpander() {
      super(ChangesTree.this);
    }

    @Override
    public boolean isExpandAllVisible() {
      return !myGroupingSupport.isNone() || !myIsModelFlat;
    }

    @Override
    public boolean isCollapseAllVisible() {
      return isExpandAllVisible();
    }
  }


  public void setSelectionMode(@JdkConstants.TreeSelectionMode int mode) {
    getSelectionModel().setSelectionMode(mode);
  }

  protected @NotNull State getNodeStatus(@NotNull ChangesBrowserNode<?> node) {
    if (getInclusionModel().isInclusionEmpty()) return State.NOT_SELECTED;

    boolean hasIncluded = false;
    boolean hasExcluded = false;

    for (Object item : allUnder(node).userObjects()) {
      State state = getInclusionModel().getInclusionState(item);

      if (state == State.SELECTED) {
        hasIncluded = true;
      }
      else if (state == State.NOT_SELECTED) {
        hasExcluded = true;
      }
      else {
        hasIncluded = true;
        hasExcluded = true;
      }
      if (hasIncluded && hasExcluded) break;
    }

    if (hasIncluded && hasExcluded) return State.DONT_CARE;
    if (hasIncluded) return State.SELECTED;
    return State.NOT_SELECTED;
  }

  protected boolean isInclusionEnabled(@NotNull ChangesBrowserNode<?> node) {
    return true;
  }

  protected boolean isInclusionVisible(@NotNull ChangesBrowserNode<?> node) {
    return true;
  }

  private boolean isIncludable(@NotNull TreePath path) {
    Object lastComponent = path.getLastPathComponent();
    if (!(lastComponent instanceof ChangesBrowserNode<?>)) return false;
    return isIncludable((ChangesBrowserNode<?>)lastComponent);
  }

  protected boolean isIncludable(@NotNull ChangesBrowserNode<?> node) {
    return isInclusionVisible(node) && isInclusionEnabled(node);
  }

  protected @NotNull List<Object> getIncludableUserObjects(@NotNull VcsTreeModelData treeModelData) {
    return treeModelData
      .iterateNodes()
      .filter(node -> isIncludable(node))
      .map(node -> (Object)node.getUserObject())
      .toList();
  }

  private class MyToggleSelectionAction extends AnAction implements DumbAware {
    private MyToggleSelectionAction() {
      setShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(isShowCheckboxes());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<Object> changes = getIncludableUserObjects(!isSelectionEmpty() ? selected(ChangesTree.this) : all(ChangesTree.this));
      if (changes.isEmpty()) return;
      boolean exclude = toggleChanges(changes);
      logInclusionToggleEvents(exclude, e);
    }
  }

  public void setSelectedChanges(@NotNull Collection<?> changes) {
    HashSet<Object> changesSet = new HashSet<>(changes);
    final List<TreePath> treeSelection = new ArrayList<>(changes.size());
    TreeUtil.treeNodeTraverser(getRoot()).forEach(node -> {
      DefaultMutableTreeNode mutableNode = (DefaultMutableTreeNode)node;
      if (changesSet.contains(mutableNode.getUserObject())) {
        treeSelection.add(new TreePath(mutableNode.getPath()));
      }
    });
    setSelectionPaths(toTreePathArray(treeSelection));
    if (treeSelection.size() == 1) scrollPathToVisible(treeSelection.get(0));
  }

  public @NotNull TreeStateStrategy<?> getTreeStateStrategy() {
    return myTreeStateStrategy;
  }

  public void setTreeStateStrategy(@NotNull TreeStateStrategy<?> keepTreeState) {
    myTreeStateStrategy = keepTreeState;
  }

  public boolean isKeepTreeState() {
    return myTreeStateStrategy != DO_NOTHING &&
           myTreeStateStrategy != ALWAYS_RESET;
  }

  public void setKeepTreeState(boolean keepTreeState) {
    setTreeStateStrategy(keepTreeState ? ALWAYS_KEEP : ALWAYS_RESET);
  }

  public boolean isScrollToSelection() {
    return myScrollToSelection;
  }

  public void setScrollToSelection(boolean scrollToSelection) {
    myScrollToSelection = scrollToSelection;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(CommonDataKeys.PROJECT, myProject);
    sink.set(PlatformDataKeys.COPY_PROVIDER, myTreeCopyProvider);
    sink.set(ChangesGroupingSupport.KEY, myGroupingSupport);
    sink.set(PlatformDataKeys.TREE_EXPANDER, myTreeExpander);
  }

  @Override
  public boolean isFileColorsEnabled() {
    return ProjectViewTree.isFileColorsEnabledFor(this);
  }

  @Override
  public @Nullable Color getFileColorForPath(@NotNull TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof ChangesBrowserNode<?> node) {
      node.cacheBackgroundColor(myProject); // use AsyncChangesTree to move this on pooled thread
      return node.getBackgroundColorCached();
    }
    return null;
  }

  @Override
  public int getToggleClickCount() {
    return -1;
  }

  @Override
  protected void processEvent(AWTEvent e) {
    myEventProcessingInProgress = e;
    try {
      super.processEvent(e);
    }
    finally {
      myEventProcessingInProgress = null;
    }
  }

  private void installCommitSessionEventsListeners() {
    addSelectionListener(() -> {
      if (myEventProcessingInProgress instanceof MouseEvent && shouldLogCommitSessionEvents()) {
        ChangesTreeCompatibilityProvider.getInstance().logFileSelected(myProject, (MouseEvent)myEventProcessingInProgress);
      }
    });
  }

  @ApiStatus.Internal
  public void logInclusionToggleEvents(boolean exclude, @NonNls MouseEvent event) {
    if (shouldLogCommitSessionEvents()) {
      ChangesTreeCompatibilityProvider.getInstance().logInclusionToggle(myProject, exclude, event);
    }
  }

  @ApiStatus.Internal
  public void logInclusionToggleEvents(boolean exclude, @NotNull AnActionEvent event) {
    if (shouldLogCommitSessionEvents()) {
      ChangesTreeCompatibilityProvider.getInstance().logInclusionToggle(myProject, exclude, event);
    }
  }

  private boolean shouldLogCommitSessionEvents() {
    return Boolean.TRUE.equals(getClientProperty(LOG_COMMIT_SESSION_EVENTS));
  }

  public interface TreeStateStrategy<T> {
    T saveState(@NotNull ChangesTree tree);

    void restoreState(@NotNull ChangesTree tree, T state, boolean scrollToSelection);
  }

  private static class DoNothingTreeStateStrategy implements TreeStateStrategy<Object> {
    @Override
    public Object saveState(@NotNull ChangesTree tree) {
      return null;
    }

    @Override
    public void restoreState(@NotNull ChangesTree tree, Object state, boolean scrollToSelection) {
    }
  }

  private static class AlwaysResetTreeStateStrategy implements TreeStateStrategy<Object> {
    @Override
    public Object saveState(@NotNull ChangesTree tree) {
      return null;
    }

    @Override
    public void restoreState(@NotNull ChangesTree tree, Object state, boolean scrollToSelection) {
      tree.resetTreeState();
    }
  }

  private static class AlwaysKeepTreeStateStrategy implements TreeStateStrategy<TreeState> {
    @Override
    public TreeState saveState(@NotNull ChangesTree tree) {
      return TreeState.createOn(tree, true, true);
    }

    @Override
    public void restoreState(@NotNull ChangesTree tree, TreeState state, boolean scrollToSelection) {
      if (state != null) {
        state.setScrollToSelection(scrollToSelection);
        state.applyTo(tree);
      }
    }
  }

  private static class KeepNonEmptyTreeStateStrategy implements TreeStateStrategy<TreeState> {
    @Override
    public TreeState saveState(@NotNull ChangesTree tree) {
      return TreeState.createOn(tree, true, true);
    }

    @Override
    public void restoreState(@NotNull ChangesTree tree, TreeState state, boolean scrollToSelection) {
      if (state != null && !state.isEmpty()) {
        state.setScrollToSelection(scrollToSelection);
        state.applyTo(tree);
      }
      else {
        tree.resetTreeState();
      }
    }
  }

  private static class KeepSelectedObjectsStrategy implements TreeStateStrategy<List<Object>> {
    @Override
    public List<Object> saveState(@NotNull ChangesTree tree) {
      return selected(tree).userObjects();
    }

    @Override
    public void restoreState(@NotNull ChangesTree tree, List<Object> state, boolean scrollToSelection) {
      if (state != null) {
        tree.setSelectedChanges(state);
      }
    }
  }
}
