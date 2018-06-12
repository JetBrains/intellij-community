// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.projectView.impl.ProjectViewTree;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SmartExpander;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.DIRECTORY_GROUPING;
import static com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.MODULE_GROUPING;
import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;
import static com.intellij.util.ArrayUtil.toStringArray;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.ar;
import static com.intellij.util.containers.ContainerUtil.set;
import static com.intellij.util.ui.ThreeStateCheckBox.State;

public abstract class ChangesTree extends Tree implements DataProvider {
  @NotNull protected final Project myProject;
  private final boolean myShowCheckboxes;
  private final int myCheckboxWidth;
  @NotNull private final ChangesGroupingSupport myGroupingSupport;
  private boolean myIsModelFlat;

  @NotNull private TObjectHashingStrategy<Object> myInclusionHashingStrategy = ContainerUtil.canonicalStrategy();
  @NotNull private Set<Object> myIncludedChanges = new THashSet<>(myInclusionHashingStrategy);
  @NotNull private Runnable myDoubleClickHandler = EmptyRunnable.getInstance();
  private boolean myKeepTreeState = false;

  @Deprecated @NonNls private final static String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";
  @NonNls private static final String GROUPING_KEYS = "ChangesTree.GroupingKeys";

  public static final String[] DEFAULT_GROUPING_KEYS = ar(DIRECTORY_GROUPING, MODULE_GROUPING);

  @NonNls public static final String GROUP_BY_ACTION_GROUP = "ChangesView.GroupBy";

  @Nullable private Runnable myInclusionListener;
  @NotNull private final CopyProvider myTreeCopyProvider;

  private boolean myModelUpdateInProgress;

  public ChangesTree(@NotNull Project project,
                     boolean showCheckboxes,
                     boolean highlightProblems) {
    super(ChangesBrowserNode.createRoot(project));
    myProject = project;
    myGroupingSupport = new ChangesGroupingSupport(myProject, this, highlightProblems);
    myShowCheckboxes = showCheckboxes;
    myCheckboxWidth = new JCheckBox().getPreferredSize().width;

    setRootVisible(false);
    setShowsRootHandles(true);
    setOpaque(false);
    new TreeSpeedSearch(this, ChangesBrowserNode.TO_TEXT_CONVERTER);

    final ChangesBrowserNodeRenderer nodeRenderer = new ChangesBrowserNodeRenderer(myProject, this::isShowFlatten, highlightProblems);
    setCellRenderer(new MyTreeCellRenderer(nodeRenderer));

    new MyToggleSelectionAction().registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), this);
    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDoubleClickHandler.run();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode() && e.getModifiers() == 0) {
          if (getSelectionCount() <= 1) {
            Object lastPathComponent = getLastSelectedPathComponent();
            if (!(lastPathComponent instanceof DefaultMutableTreeNode)) {
              return;
            }
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)lastPathComponent;
            if (!node.isLeaf()) {
              return;
            }
          }
          myDoubleClickHandler.run();
          e.consume();
        }
      }
    });

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        TreePath clickPath =
          getUI() instanceof WideSelectionTreeUI ? getClosestPathForLocation(e.getX(), e.getY()) : getPathForLocation(e.getX(), e.getY());
        if (clickPath == null) return false;

        final int row = getRowForLocation(e.getPoint().x, e.getPoint().y);
        if (row >= 0) {
          if (myShowCheckboxes) {
            final Rectangle baseRect = getRowBounds(row);
            baseRect.setSize(myCheckboxWidth, baseRect.height);
            if (baseRect.contains(e.getPoint())) return false;
          }
        }

        myDoubleClickHandler.run();
        return true;
      }
    }.installOn(this);

    new TreeLinkMouseListener(nodeRenderer) {
      @Override
      protected int getRendererRelativeX(@NotNull MouseEvent e, @NotNull JTree tree, @NotNull TreePath path) {
        int x = super.getRendererRelativeX(e, tree, path);

        return !myShowCheckboxes ? x : x - myCheckboxWidth;
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        if (!isEmpty()) { // apply only if tree is not empty - otherwise "getEmptyText()" should handle the case
          super.mouseMoved(e);
        }
      }
    }.installOn(this);
    SmartExpander.installOn(this);

    migrateShowFlattenSetting();
    myGroupingSupport
      .setGroupingKeysOrSkip(set(notNull(PropertiesComponent.getInstance(myProject).getValues(GROUPING_KEYS), DEFAULT_GROUPING_KEYS)));
    myGroupingSupport.addPropertyChangeListener(e -> changeGrouping());

    String emptyText = StringUtil.capitalize(DiffBundle.message("diff.count.differences.status.text", 0));
    setEmptyText(emptyText);

    myTreeCopyProvider = new ChangesBrowserNodeCopyProvider(this);
  }

  private void migrateShowFlattenSetting() {
    PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    if (properties.isValueSet(FLATTEN_OPTION_KEY)) {
      properties.setValues(GROUPING_KEYS, properties.isTrueValue(FLATTEN_OPTION_KEY) ? EMPTY_STRING_ARRAY : DEFAULT_GROUPING_KEYS);
      properties.unsetValue(FLATTEN_OPTION_KEY);
    }
  }

  public void setEmptyText(@NotNull String emptyText) {
    getEmptyText().setText(emptyText);
  }

  public void addSelectionListener(@NotNull Runnable runnable) {
    addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        runnable.run();
      }
    });
  }

  public void setInclusionListener(@Nullable Runnable runnable) {
    myInclusionListener = runnable;
  }

  public void setDoubleClickHandler(@NotNull final Runnable doubleClickHandler) {
    myDoubleClickHandler = doubleClickHandler;
  }

  public void installPopupHandler(ActionGroup group) {
    PopupHandler.installUnknownPopupHandler(this, group, ActionManager.getInstance());
  }

  public JComponent getPreferredFocusedComponent() {
    return this;
  }

  @NotNull
  public ChangesGroupingSupport getGroupingSupport() {
    return myGroupingSupport;
  }

  @NotNull
  public ChangesGroupingPolicyFactory getGrouping() {
    return getGroupingSupport().getGrouping();
  }

  public boolean isShowFlatten() {
    return !myGroupingSupport.isDirectory();
  }

  public boolean isShowCheckboxes() {
    return myShowCheckboxes;
  }

  private void changeGrouping() {
    PropertiesComponent.getInstance(myProject).setValues(GROUPING_KEYS, toStringArray(getGroupingSupport().getGroupingKeys()));

    List<Object> oldSelection = getSelectedUserObjects();
    rebuildTree();
    setSelectedChanges(oldSelection);
  }

  private void setChildIndent(boolean isFlat) {
    BasicTreeUI treeUI = (BasicTreeUI)getUI();

    treeUI.setLeftChildIndent(!isFlat ? UIUtil.getTreeLeftChildIndent() : 0);
    treeUI.setRightChildIndent(!isFlat ? UIUtil.getTreeRightChildIndent() : 0);
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
      setChildIndent(myGroupingSupport.isNone() && myIsModelFlat);

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

  private void resetTreeState() {
    TreeUtil.expandAll(this);

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
    if (toSelect != null) {
      int rowInTree = findRowContainingFile(getRoot(), toSelect);
      if (rowInTree == -1) return;

      setSelectionRow(rowInTree);
      TreeUtil.showRowCentered(this, rowInTree, false);
    }
  }

  private int findRowContainingFile(@NotNull TreeNode root, @NotNull final VirtualFile toSelect) {
    final Ref<Integer> row = Ref.create(-1);
    TreeUtil.traverse(root, node -> {
      if (node instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)node).getUserObject();
        if (userObject instanceof Change) {
          if (matches((Change)userObject, toSelect)) {
            TreeNode[] path = ((DefaultMutableTreeNode)node).getPath();
            row.set(getRowForPath(new TreePath(path)));
          }
        }
      }

      return row.get() == -1;
    });
    return row.get();
  }

  private static boolean matches(@NotNull Change change, @NotNull VirtualFile file) {
    VirtualFile virtualFile = change.getVirtualFile();
    return virtualFile != null && virtualFile.equals(file) || seemsToBeMoved(change, file);
  }

  private static boolean seemsToBeMoved(Change change, VirtualFile toSelect) {
    ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision == null) return false;
    FilePath file = afterRevision.getFile();
    return FileUtil.pathsEqual(file.getPath(), toSelect.getPath());
  }


  @NotNull
  private List<Object> getAllUserObjects() {
    return VcsTreeModelData.all(this).userObjects();
  }

  @NotNull
  private List<Object> getUserObjectsUnder(@NotNull ChangesBrowserNode<?> node) {
    return VcsTreeModelData.children(node).userObjects();
  }

  @NotNull
  private List<Object> getSelectedUserObjects() {
    return VcsTreeModelData.selected(this).userObjects();
  }


  @NotNull
  ChangesBrowserNode<?> getRoot() {
    return (ChangesBrowserNode<?>)getModel().getRoot();
  }

  protected void notifyInclusionListener() {
    if (myInclusionListener != null) {
      myInclusionListener.run();
    }
  }


  public void setInclusionHashingStrategy(@NotNull TObjectHashingStrategy<Object> strategy) {
    myInclusionHashingStrategy = strategy;
    Set<Object> oldInclusion = myIncludedChanges;
    myIncludedChanges = new THashSet<>(strategy);
    myIncludedChanges.addAll(oldInclusion);
  }

  /**
   * Usually, this method should be called before tree is initialized via `rebuildTree`
   * to set nodes, that are included "by default".
   * This will allow to preselect first included node via `resetTreeState`.
   * <p>
   * No listener supposed to be called
   */
  public void setIncludedChanges(@NotNull Collection<?> changes) {
    myIncludedChanges.clear();
    myIncludedChanges.addAll(changes);
    repaint();
  }

  public void includeChange(final Object change) {
    includeChanges(Collections.singleton(change));
  }

  public void includeChanges(final Collection<?> changes) {
    myIncludedChanges.addAll(changes);
    notifyInclusionListener();
    repaint();
  }

  public void excludeChange(final Object change) {
    excludeChanges(Collections.singleton(change));
  }

  public void excludeChanges(final Collection<?> changes) {
    myIncludedChanges.removeAll(changes);
    notifyInclusionListener();
    repaint();
  }

  protected void toggleChanges(final Collection<?> changes) {
    boolean hasExcluded = false;
    for (Object value : changes) {
      if (!myIncludedChanges.contains(value)) {
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

  public boolean isIncluded(final Object change) {
    return myIncludedChanges.contains(change);
  }

  @NotNull
  public Set<Object> getIncludedSet() {
    return new THashSet<>(myIncludedChanges, myInclusionHashingStrategy);
  }

  public void expandAll() {
    TreeUtil.expandAll(this);
  }


  /**
   * @deprecated See {@link ChangesTree#GROUP_BY_ACTION_GROUP}, {@link TreeActionsToolbarPanel}
   */
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
      return CommonActionsManager.getInstance().createExpandAllHeaderAction(new MyTreeExpander(), this);
    }
    else {
      return CommonActionsManager.getInstance().createExpandAllAction(new MyTreeExpander(), this);
    }
  }

  @NotNull
  public AnAction createCollapseAllAction(boolean headerAction) {
    if (headerAction) {
      return CommonActionsManager.getInstance().createCollapseAllHeaderAction(new MyTreeExpander(), this);
    }
    else {
      return CommonActionsManager.getInstance().createCollapseAllAction(new MyTreeExpander(), this);
    }
  }

  private class MyTreeExpander extends DefaultTreeExpander {
    public MyTreeExpander() {
      super(ChangesTree.this);
    }

    @Override
    public boolean isVisible(AnActionEvent event) {
      return !myGroupingSupport.isNone() || !myIsModelFlat;
    }
  }


  public void setSelectionMode(@JdkConstants.TreeSelectionMode int mode) {
    getSelectionModel().setSelectionMode(mode);
  }

  private class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final ChangesBrowserNodeRenderer myTextRenderer;
    private final ThreeStateCheckBox myCheckBox;


    public MyTreeCellRenderer(@NotNull ChangesBrowserNodeRenderer textRenderer) {
      super(new BorderLayout());
      myCheckBox = new ThreeStateCheckBox();
      myTextRenderer = textRenderer;

      if (myShowCheckboxes) {
        add(myCheckBox, BorderLayout.WEST);
      }

      add(myTextRenderer, BorderLayout.CENTER);
      setOpaque(false);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {

      if (UIUtil.isUnderGTKLookAndFeel()) {
        NonOpaquePanel.setTransparent(this);
        NonOpaquePanel.setTransparent(myCheckBox);
      } else {
        setBackground(null);
        myCheckBox.setBackground(null);
        myCheckBox.setOpaque(false);
      }

      myTextRenderer.setOpaque(false);
      myTextRenderer.setTransparentIconBackground(true);
      myTextRenderer.setToolTipText(null);
      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (myShowCheckboxes) {
        @SuppressWarnings("unchecked")
        State state = getNodeStatus((ChangesBrowserNode)value);
        myCheckBox.setState(state);

        myCheckBox.setEnabled(tree.isEnabled() && isNodeEnabled((ChangesBrowserNode)value));
        revalidate();

        return this;
      }
      else {
        return myTextRenderer;
      }
    }

    @Override
    public String getToolTipText() {
      return myTextRenderer.getToolTipText();
    }
  }


  protected State getNodeStatus(@NotNull ChangesBrowserNode<?> node) {
    boolean hasIncluded = false;
    boolean hasExcluded = false;

    for (Object change : getUserObjectsUnder(node)) {
      if (myIncludedChanges.contains(change)) {
        hasIncluded = true;
      }
      else {
        hasExcluded = true;
      }
    }

    if (hasIncluded && hasExcluded) return State.DONT_CARE;
    if (hasIncluded) return State.SELECTED;
    return State.NOT_SELECTED;
  }

  protected boolean isNodeEnabled(ChangesBrowserNode<?> node) {
    return true;
  }

  private class MyToggleSelectionAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(AnActionEvent e) {
      List<Object> changes = getSelectedUserObjects();
      if (changes.isEmpty()) changes = getAllUserObjects();
      toggleChanges(changes);
    }
  }

  public void setSelectedChanges(@NotNull Collection<?> changes) {
    HashSet<Object> changesSet = new HashSet<>(changes);
    final List<TreePath> treeSelection = new ArrayList<>(changes.size());
    TreeUtil.traverse(getRoot(), node -> {
      DefaultMutableTreeNode mutableNode = (DefaultMutableTreeNode)node;
      //noinspection SuspiciousMethodCalls
      if (changesSet.contains(mutableNode.getUserObject())) {
        treeSelection.add(new TreePath(mutableNode.getPath()));
      }
      return true;
    });
    setSelectionPaths(treeSelection.toArray(new TreePath[0]));
    if (treeSelection.size() == 1) scrollPathToVisible(treeSelection.get(0));
  }

  public void setKeepTreeState(boolean keepTreeState) {
    myKeepTreeState = keepTreeState;
  }

  @Nullable
  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return myTreeCopyProvider;
    }
    if (ChangesGroupingSupport.KEY.is(dataId)) {
      return myGroupingSupport;
    }
    return null;
  }

  @Override
  public boolean isFileColorsEnabled() {
    return ProjectViewTree.isFileColorsEnabledFor(this);
  }

  @Override
  public Color getFileColorFor(Object object) {
    VirtualFile file;
    if (object instanceof FilePath) {
      file = ((FilePath)object).getVirtualFile();
    }
    else if (object instanceof Change) {
      file = ((Change)object).getVirtualFile();
    }
    else {
      file = ObjectUtils.tryCast(object, VirtualFile.class);
    }

    if (file != null) {
      return VfsPresentationUtil.getFileBackgroundColor(myProject, file);
    }
    return super.getFileColorFor(object);
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    if (e.getID() == MouseEvent.MOUSE_PRESSED) {
      if (!isEnabled()) return;
      int row = getRowForLocation(e.getX(), e.getY());
      if (row >= 0) {
        final Rectangle baseRect = getRowBounds(row);
        baseRect.setSize(myCheckboxWidth, baseRect.height);
        if (baseRect.contains(e.getPoint())) {
          setSelectionRow(row);
          toggleChanges(getSelectedUserObjects());
        }
      }
    }
    super.processMouseEvent(e);
  }

  @Override
  public int getToggleClickCount() {
    return -1;
  }
}
