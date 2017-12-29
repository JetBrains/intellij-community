// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.actions.CollapseAllAction;
import com.intellij.ui.treeStructure.actions.ExpandAllAction;
import com.intellij.util.ObjectUtils;
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

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.util.ui.ThreeStateCheckBox.State;

public abstract class ChangesTree extends Tree implements DataProvider {
  @NotNull protected final Project myProject;
  private final boolean myShowCheckboxes;
  private final int myCheckboxWidth;
  private boolean myShowFlatten;
  private boolean myIsModelFlat;

  @NotNull private Set<Object> myIncludedChanges = new THashSet<>();
  @NotNull private Runnable myDoubleClickHandler = EmptyRunnable.getInstance();
  private boolean myKeepTreeState = false;

  @NonNls private final static String FLATTEN_OPTION_KEY = "ChangesBrowser.SHOW_FLATTEN";

  @Nullable private Runnable myInclusionListener;
  @NotNull private final CopyProvider myTreeCopyProvider;
  private TreeState myNonFlatTreeState;

  public ChangesTree(@NotNull Project project,
                     boolean showCheckboxes,
                     boolean highlightProblems) {
    super(ChangesBrowserNode.createRoot(project));
    myProject = project;
    myShowCheckboxes = showCheckboxes;
    myCheckboxWidth = new JCheckBox().getPreferredSize().width;

    setHorizontalAutoScrollingEnabled(false);
    setRootVisible(false);
    setShowsRootHandles(true);
    setOpaque(false);
    new TreeSpeedSearch(this, ChangesBrowserNode.TO_TEXT_CONVERTER);

    final ChangesBrowserNodeRenderer nodeRenderer = new ChangesBrowserNodeRenderer(myProject, () -> myShowFlatten, highlightProblems);
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

    myShowFlatten = PropertiesComponent.getInstance(myProject).isTrueValue(FLATTEN_OPTION_KEY);

    String emptyText = StringUtil.capitalize(DiffBundle.message("diff.count.differences.status.text", 0));
    setEmptyText(emptyText);

    myTreeCopyProvider = new ChangesBrowserNodeCopyProvider(this);
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

  public boolean isShowFlatten() {
    return myShowFlatten;
  }

  public boolean isShowCheckboxes() {
    return myShowCheckboxes;
  }

  public void setShowFlatten(final boolean showFlatten) {
    if (myShowFlatten == showFlatten) return;

    final List<Object> oldSelection = getSelectedUserObjects();
    if (myKeepTreeState && showFlatten) {
      myNonFlatTreeState = TreeState.createOn(this, getRoot());
    }

    myShowFlatten = showFlatten;
    rebuildTree();

    if (myKeepTreeState && !showFlatten && myNonFlatTreeState != null) {
      myNonFlatTreeState.applyTo(this, getRoot());
    }
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
    ApplicationManager.getApplication().assertIsDispatchThread();

    TreeState state = null;
    if (myKeepTreeState) {
      state = TreeState.createOn(this, getRoot());
    }

    setModel(model);
    myIsModelFlat = isCurrentModelFlat();
    setChildIndent(myShowFlatten && myIsModelFlat);

    if (myKeepTreeState) {
      //noinspection ConstantConditions
      state.applyTo(this, getRoot());
    }
    else {
      resetTreeState();
    }
  }

  private void resetTreeState() {
    TreeUtil.expandAll(this);

    int selectedTreeRow = -1;

    if (myShowCheckboxes) {
      if (myIncludedChanges.size() > 0) {
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

  private void notifyInclusionListener() {
    if (myInclusionListener != null) {
      myInclusionListener.run();
    }
  }


  public void setInclusionHashingStrategy(@NotNull TObjectHashingStrategy<Object> strategy) {
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
  public void setIncludedChanges(final Collection<?> changes) {
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
    return Collections.unmodifiableSet(myIncludedChanges);
  }

  public void expandAll() {
    TreeUtil.expandAll(this);
  }

  public AnAction[] getTreeActions() {
    final ToggleShowDirectoriesAction directoriesAction = new ToggleShowDirectoriesAction();
    final ExpandAllAction expandAllAction = new ExpandAllAction(this) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(!myShowFlatten || !myIsModelFlat);
      }
    };
    final CollapseAllAction collapseAllAction = new CollapseAllAction(this) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(!myShowFlatten || !myIsModelFlat);
      }
    };
    final AnAction[] actions = new AnAction[]{directoriesAction, expandAllAction, collapseAllAction};
    directoriesAction.registerCustomShortcutSet(
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK)),
      this);
    expandAllAction.registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_EXPAND_ALL), this);
    collapseAllAction.registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_COLLAPSE_ALL), this);
    return actions;
  }

  public void setSelectionMode(@JdkConstants.TreeSelectionMode int mode) {
    getSelectionModel().setSelectionMode(mode);
  }

  private class MyTreeCellRenderer extends JPanel implements TreeCellRenderer {
    private final ChangesBrowserNodeRenderer myTextRenderer;
    private final JCheckBox myCheckBox;


    public MyTreeCellRenderer(@NotNull ChangesBrowserNodeRenderer textRenderer) {
      super(new BorderLayout());
      myCheckBox = new JCheckBox();
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
        myCheckBox.setSelected(state != State.NOT_SELECTED);

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


  private State getNodeStatus(ChangesBrowserNode<?> node) {
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
    return getNodeStatus(node) != State.DONT_CARE;
  }

  private class MyToggleSelectionAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(AnActionEvent e) {
      List<Object> changes = getSelectedUserObjects();
      if (changes.isEmpty()) changes = getAllUserObjects();
      toggleChanges(changes);
    }
  }

  public class ToggleShowDirectoriesAction extends ToggleAction implements DumbAware {
    public ToggleShowDirectoriesAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            AllIcons.Actions.GroupByPackage);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return !isShowFlatten();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      PropertiesComponent.getInstance(myProject).setValue(FLATTEN_OPTION_KEY, String.valueOf(!state));
      setShowFlatten(!state);
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
    setSelectionPaths(treeSelection.toArray(new TreePath[treeSelection.size()]));
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
      return FileColorManager.getInstance(myProject).getFileColor(file);
    }
    return super.getFileColorFor(object);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    Dimension size = super.getPreferredScrollableViewportSize();
    size = new Dimension(size.width + 10, size.height);
    return size;
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
