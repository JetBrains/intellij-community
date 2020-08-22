// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.InclusionListener;
import com.intellij.openapi.vcs.changes.InclusionModel;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl;
import com.intellij.ui.ClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Processor;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.*;

import static com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.DIRECTORY_GROUPING;
import static com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.MODULE_GROUPING;
import static com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*;
import static com.intellij.ui.tree.TreePathUtil.toTreePathArray;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.ar;
import static com.intellij.util.containers.ContainerUtil.set;
import static com.intellij.util.ui.ThreeStateCheckBox.State;
import static java.util.stream.Collectors.toList;

public abstract class ChangesTree extends Tree implements DataProvider {
  @NotNull protected final Project myProject;
  private boolean myShowCheckboxes;
  @Nullable private ClickListener myCheckBoxClickHandler;
  private final int myCheckboxWidth;
  @NotNull private final ChangesGroupingSupport myGroupingSupport;
  private boolean myIsModelFlat;

  @NotNull private InclusionModel myInclusionModel = new DefaultInclusionModel();
  @NotNull private final InclusionListener myInclusionModelListener = () -> {
    notifyInclusionListener();
    repaint();
  };
  @Nullable private Runnable myTreeInclusionListener;

  @NotNull private final ChangesTreeHandlers myHandlers;
  private boolean myKeepTreeState = false;

  @Deprecated @NonNls private final static String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";
  @NonNls private static final String GROUPING_KEYS = "ChangesTree.GroupingKeys";

  public static final String[] DEFAULT_GROUPING_KEYS = ar(DIRECTORY_GROUPING, MODULE_GROUPING);

  @NonNls public static final String GROUP_BY_ACTION_GROUP = "ChangesView.GroupBy";

  @NotNull private final CopyProvider myTreeCopyProvider;
  @NotNull private TreeExpander myTreeExpander = new MyTreeExpander();

  private boolean myModelUpdateInProgress;

  public ChangesTree(@NotNull Project project, boolean showCheckboxes, boolean highlightProblems) {
    this(project, showCheckboxes, highlightProblems, false);
  }

  protected ChangesTree(@NotNull Project project, boolean showCheckboxes, boolean highlightProblems, boolean expandInSpeedSearch) {
    super(ChangesBrowserNode.createRoot());
    myProject = project;
    myShowCheckboxes = showCheckboxes;
    myCheckboxWidth = new JCheckBox().getPreferredSize().width;
    myInclusionModel.addInclusionListener(myInclusionModelListener);
    myHandlers = new ChangesTreeHandlers(this);

    setRootVisible(false);
    setShowsRootHandles(true);
    setOpaque(false);
    new TreeSpeedSearch(this, ChangesBrowserNode.TO_TEXT_CONVERTER, expandInSpeedSearch);

    final ChangesBrowserNodeRenderer nodeRenderer = new ChangesBrowserNodeRenderer(myProject, this::isShowFlatten, highlightProblems);
    setCellRenderer(new CheckboxTreeCellRenderer(nodeRenderer));

    new MyToggleSelectionAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), this);
    showCheckboxesChanged();

    installTreeLinkHandler(nodeRenderer);
    SmartExpander.installOn(this);

    myGroupingSupport = installGroupingSupport();

    setEmptyText(DiffBundle.message("diff.count.differences.status.text", 0));

    myTreeCopyProvider = new ChangesBrowserNodeCopyProvider(this);
  }

  /**
   * There is special logic for {@link DnDAware} components in
   * {@link IdeGlassPaneImpl#dispatch(AWTEvent)} that doesn't call
   * {@link Component#processMouseEvent(MouseEvent)} in case of mouse clicks over selection.
   *
   * So we add "checkbox mouse clicks" handling as a listener.
   */
  private ClickListener installCheckBoxClickHandler() {
    ClickListener handler = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        TreePath path = getPathIfCheckBoxClicked(event.getPoint());
        if (path != null) {
          setSelectionPath(path);
          toggleChanges(getIncludableUserObjects(selected(ChangesTree.this)));
        }
        return false;
      }
    };
    handler.installOn(this);

    return handler;
  }

  @Nullable
  TreePath getPathIfCheckBoxClicked(@NotNull Point p) {
    if (!myShowCheckboxes || !isEnabled()) return null;

    TreePath path = getPathIfInsideComponent(p);
    if (path != null && isIncludable(path)) return path;
    return null;
  }

  public @Nullable TreePath getPathIfInsideComponent(@NotNull Point p) {
    TreePath path = getPathForLocation(p.x, p.y);
    if (path == null) return null;

    Rectangle pathBounds = getPathBounds(path);
    if (pathBounds == null) return null;

    Rectangle checkBoxBounds = pathBounds.getBounds();
    checkBoxBounds.setSize(getComponentWidth(path), checkBoxBounds.height);
    if (!checkBoxBounds.contains(p)) return null;

    return path;
  }

  protected void installTreeLinkHandler(@NotNull ChangesBrowserNodeRenderer nodeRenderer) {
    new TreeLinkMouseListener(nodeRenderer) {
      @Override
      protected int getRendererRelativeX(@NotNull MouseEvent e, @NotNull JTree tree, @NotNull TreePath path) {
        int x = super.getRendererRelativeX(e, tree, path);

        return x - getComponentWidth(path);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        if (!isEmpty()) { // apply only if tree is not empty - otherwise "getEmptyText()" should handle the case
          super.mouseMoved(e);
        }
      }
    }.installOn(this);
  }

  protected int getComponentWidth(@NotNull TreePath path) {
    if (!myShowCheckboxes) return 0;
    return myCheckboxWidth;
  }

  @NotNull
  protected ChangesGroupingSupport installGroupingSupport() {
    ChangesGroupingSupport result = new ChangesGroupingSupport(myProject, this, false);

    migrateShowFlattenSetting();
    result.setGroupingKeysOrSkip(set(notNull(PropertiesComponent.getInstance(myProject).getValues(GROUPING_KEYS), DEFAULT_GROUPING_KEYS)));
    result.addPropertyChangeListener(e -> changeGrouping());

    return result;
  }

  private void migrateShowFlattenSetting() {
    PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    if (properties.isValueSet(FLATTEN_OPTION_KEY)) {
      properties.setValues(GROUPING_KEYS, properties.isTrueValue(FLATTEN_OPTION_KEY) ? ArrayUtilRt.EMPTY_STRING_ARRAY
                                                                                     : DEFAULT_GROUPING_KEYS);
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

  /**
   * @deprecated Use {@link #setDoubleClickAndEnterKeyHandler(Runnable)}
   */
  @Deprecated
  public void setDoubleClickHandler(@NotNull Runnable doubleClickHandler) {
    setDoubleClickAndEnterKeyHandler(doubleClickHandler);
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

  @Nullable
  public Processor<MouseEvent> getDoubleClickHandler() {
    return myHandlers.getDoubleClickHandler();
  }

  public void setDoubleClickHandler(@Nullable Processor<MouseEvent> handler) {
    myHandlers.setDoubleClickHandler(handler);
  }

  @Nullable
  public Processor<KeyEvent> getEnterKeyHandler() {
    return myHandlers.getEnterKeyHandler();
  }

  public void setEnterKeyHandler(@Nullable Processor<KeyEvent> handler) {
    myHandlers.setEnterKeyHandler(handler);
  }

  public void installPopupHandler(ActionGroup group) {
    PopupHandler.installUnknownPopupHandler(this, group, ActionManager.getInstance());
  }

  public JComponent getPreferredFocusedComponent() {
    return this;
  }

  public void addGroupingChangeListener(@NotNull PropertyChangeListener listener) {
    myGroupingSupport.addPropertyChangeListener(listener);
  }

  public void removeGroupingChangeListener(@NotNull PropertyChangeListener listener) {
    myGroupingSupport.removePropertyChangeListener(listener);
  }

  @NotNull
  public ChangesGroupingSupport getGroupingSupport() {
    return myGroupingSupport;
  }

  @NotNull
  public ChangesGroupingPolicyFactory getGrouping() {
    return getGroupingSupport().getGrouping();
  }

  @NotNull
  public Project getProject() {
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
      showCheckboxesChanged();
    }
  }

  private void showCheckboxesChanged() {
    if (isShowCheckboxes()) {
      myCheckBoxClickHandler = installCheckBoxClickHandler();
    }
    else if (myCheckBoxClickHandler != null) {
      myCheckBoxClickHandler.uninstall(this);
      myCheckBoxClickHandler = null;
    }
    repaint();
  }

  private void changeGrouping() {
    PropertiesComponent.getInstance(myProject).setValues(GROUPING_KEYS, ArrayUtilRt.toStringArray(getGroupingSupport().getGroupingKeys()));

    List<Object> oldSelection = selected(this).userObjects();
    rebuildTree();
    setSelectedChanges(oldSelection);
  }

  private boolean isCurrentModelFlat() {
    boolean isFlat = true;
    Enumeration enumeration = getRoot().depthFirstEnumeration();

    while (isFlat && enumeration.hasMoreElements()) {
      isFlat = ((ChangesBrowserNode)enumeration.nextElement()).getLevel() <= 1;
    }

    return isFlat;
  }

  public abstract void rebuildTree();

  protected void updateTreeModel(@NotNull DefaultTreeModel model) {
    myModelUpdateInProgress = true;
    try {
      ApplicationManager.getApplication().assertIsDispatchThread();

      TreeState state = null;
      if (myKeepTreeState) {
        state = TreeState.createOn(this, getRoot());
      }

      setModel(model);
      myIsModelFlat = isCurrentModelFlat();
      setShowsRootHandles(!myGroupingSupport.isNone() || !myIsModelFlat);

      if (myKeepTreeState) {
        //noinspection ConstantConditions
        state.applyTo(this, getRoot());
      }
      else {
        resetTreeState();
      }
    }
    finally {
      myModelUpdateInProgress = false;
    }
  }

  public boolean isModelUpdateInProgress() {
    return myModelUpdateInProgress;
  }

  protected void resetTreeState() {
    // expanding lots of nodes is a slow operation (and result is not very useful)
    if (hasAtLeastNodes(this, 30000)) {
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

  private static boolean hasAtLeastNodes(@NotNull Tree tree, int nodeNumber) {
    return TreeUtil.treeTraverser(tree).traverse().take(nodeNumber).size() >= nodeNumber;
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

  @NotNull
  public ChangesBrowserNode<?> getRoot() {
    return (ChangesBrowserNode<?>)getModel().getRoot();
  }

  @NotNull
  public InclusionModel getInclusionModel() {
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

  protected void toggleChanges(@NotNull Collection<?> changes) {
    boolean hasExcluded = false;
    for (Object item : changes) {
      if (getInclusionModel().getInclusionState(item) != State.SELECTED) {
        hasExcluded = true;
        break;
      }
    }

    if (hasExcluded) {
      includeChanges(changes);
    }
    else {
      excludeChanges(changes);
    }
  }

  public boolean isIncluded(@NotNull Object change) {
    return getInclusionModel().getInclusionState(change) != State.NOT_SELECTED;
  }

  @NotNull
  public Set<Object> getIncludedSet() {
    return getInclusionModel().getInclusion();
  }

  public void expandAll() {
    TreeUtil.expandAll(this);
  }

  public void expandDefaults() {
    TreeUtil.promiseExpand(this, path -> {
      Object node = path.getLastPathComponent();
      if (node instanceof ChangesBrowserNode && !((ChangesBrowserNode<?>)node).shouldExpandByDefault()) {
        return TreeVisitor.Action.SKIP_CHILDREN;
      }
      return TreeVisitor.Action.CONTINUE;
    });
  }

  @NotNull
  public TreeExpander getTreeExpander() {
    return myTreeExpander;
  }

  public void setTreeExpander(@NotNull TreeExpander expander) {
    myTreeExpander = expander;
  }

  /**
   * @deprecated See {@link ChangesTree#GROUP_BY_ACTION_GROUP}, {@link TreeActionsToolbarPanel}
   */
  @Deprecated
  public AnAction[] getTreeActions() {
    return new AnAction[]{
      ActionManager.getInstance().getAction(GROUP_BY_ACTION_GROUP),
      createExpandAllAction(false),
      createCollapseAllAction(false)
    };
  }

  @NotNull
  public AnAction createExpandAllAction(boolean headerAction) {
    if (headerAction) {
      return CommonActionsManager.getInstance().createExpandAllHeaderAction(myTreeExpander, this);
    }
    else {
      return CommonActionsManager.getInstance().createExpandAllAction(myTreeExpander, this);
    }
  }

  @NotNull
  public AnAction createCollapseAllAction(boolean headerAction) {
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
    public boolean isVisible(@NotNull AnActionEvent event) {
      return !myGroupingSupport.isNone() || !myIsModelFlat;
    }
  }


  public void setSelectionMode(@JdkConstants.TreeSelectionMode int mode) {
    getSelectionModel().setSelectionMode(mode);
  }

  @NotNull
  State getNodeStatus(@NotNull ChangesBrowserNode<?> node) {
    boolean hasIncluded = false;
    boolean hasExcluded = false;

    for (Object item : children(node).userObjects()) {
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

  private boolean isIncludable(@NotNull ChangesBrowserNode<?> node) {
    return isInclusionVisible(node) && isInclusionEnabled(node);
  }

  @NotNull
  private List<Object> getIncludableUserObjects(@NotNull VcsTreeModelData treeModelData) {
    return treeModelData
      .nodesStream()
      .filter(node -> isIncludable(node))
      .map(node -> node.getUserObject())
      .collect(toList());
  }

  private class MyToggleSelectionAction extends AnAction implements DumbAware {
    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(isShowCheckboxes());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<Object> changes = getIncludableUserObjects(!isSelectionEmpty() ? selected(ChangesTree.this) : all(ChangesTree.this));
      if (!changes.isEmpty()) toggleChanges(changes);
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

  public boolean isKeepTreeState() {
    return myKeepTreeState;
  }

  public void setKeepTreeState(boolean keepTreeState) {
    myKeepTreeState = keepTreeState;
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myTreeCopyProvider;
    }
    if (ChangesGroupingSupport.KEY.is(dataId)) {
      return myGroupingSupport;
    }
    if (PlatformDataKeys.TREE_EXPANDER.is(dataId)) {
      return myTreeExpander;
    }
    return null;
  }

  @Override
  public boolean isFileColorsEnabled() {
    return ProjectViewTree.isFileColorsEnabledFor(this);
  }

  @Nullable
  @Override
  public Color getFileColorForPath(@NotNull TreePath path) {
    Object component = path.getLastPathComponent();
    if (component instanceof ChangesBrowserNode<?>) {
      return ((ChangesBrowserNode)component).getBackgroundColor(myProject);
    }
    return null;
  }

  @Override
  public int getToggleClickCount() {
    return -1;
  }
}
