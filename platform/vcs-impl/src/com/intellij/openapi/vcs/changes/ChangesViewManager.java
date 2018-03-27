/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.openapi.vcs.changes;

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.actions.IgnoredSettingsAction;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.FunctionUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.unshelveSilentlyWithDnd;
import static java.util.stream.Collectors.toList;

@State(
  name = "ChangesViewManager",
  storages = @Storage(file = StoragePathMacros.WORKSPACE_FILE)
)
public class ChangesViewManager implements ChangesViewI, ProjectComponent, PersistentStateComponent<ChangesViewManager.State> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangesViewManager");
  private static final String CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION = "ChangesViewManager.DETAILS_SPLITTER_PROPORTION";

  @NotNull private final ChangesListView myView;
  private final VcsConfiguration myVcsConfiguration;
  private JPanel myProgressLabel;

  private final Alarm myRepaintAlarm;

  private boolean myDisposed = false;

  @NotNull private final Project myProject;
  @NotNull private final ChangesViewContentManager myContentManager;

  @NotNull private ChangesViewManager.State myState = new ChangesViewManager.State();

  private PreviewDiffSplitterComponent mySplitterComponent;

  @NotNull private final TreeSelectionListener myTsl;
  private MyChangeViewContent myContent;

  @NotNull
  public static ChangesViewI getInstance(@NotNull Project project) {
    return project.getComponent(ChangesViewI.class);
  }

  public ChangesViewManager(@NotNull Project project, @NotNull ChangesViewContentManager contentManager) {
    myProject = project;
    myContentManager = contentManager;
    myVcsConfiguration = VcsConfiguration.getInstance(myProject);
    myView = new ChangesListView(project);
    myRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    myTsl = new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (LOG.isDebugEnabled()) {
          TreePath[] paths = myView.getSelectionPaths();
          String joinedPaths = paths != null ? StringUtil.join(paths, FunctionUtil.string(), ", ") : null;
          String message = "selection changed. selected:  " + joinedPaths;

          if (LOG.isTraceEnabled()) {
            LOG.trace(message + " from: " + DebugUtil.currentStackTrace());
          }
          else {
            LOG.debug(message);
          }
        }
        ApplicationManager.getApplication().invokeLater(() -> updatePreview());
      }
    };
  }

  @Override
  public void projectOpened() {
    ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListListener(), myProject);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    myContent = new MyChangeViewContent(createChangeViewComponent(), ChangesViewContentManager.LOCAL_CHANGES, false);
    myContent.setHelpId(ChangesListView.HELP_ID);
    myContent.setCloseable(false);
    myContentManager.addContent(myContent);

    scheduleRefresh();
    myProject.getMessageBus().connect().subscribe(RemoteRevisionsCache.REMOTE_VERSION_CHANGED,
                                                  () -> ApplicationManager.getApplication().invokeLater(() -> refreshView(), ModalityState.NON_MODAL, myProject.getDisposed()));
    updatePreview();
  }

  @Override
  public void projectClosed() {
    myView.removeTreeSelectionListener(myTsl);
    myDisposed = true;
    myRepaintAlarm.cancelAllRequests();
  }

  @Override
  @NonNls @NotNull
  public String getComponentName() {
    return "ChangesViewManager";
  }

  private JComponent createChangeViewComponent() {
    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true);

    EmptyAction.registerWithShortcutSet("ChangesView.Refresh", CommonShortcuts.getRerun(), panel);
    EmptyAction.registerWithShortcutSet("ChangesView.NewChangeList", CommonShortcuts.getNew(), panel);
    EmptyAction.registerWithShortcutSet("ChangesView.RemoveChangeList", CommonShortcuts.getDelete(), panel);
    EmptyAction.registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), panel);
    EmptyAction.registerWithShortcutSet("ChangesView.Rename",CommonShortcuts.getRename() , panel);
    EmptyAction.registerWithShortcutSet("ChangesView.SetDefault", new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.ALT_DOWN_MASK | ctrlMask())), panel);
    EmptyAction.registerWithShortcutSet(IdeActions.ACTION_SHOW_DIFF_COMMON, CommonShortcuts.getDiff(), panel);

    DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("ChangesViewToolbar");
    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, group, false);
    toolbar.setTargetComponent(myView);
    JComponent toolbarComponent = toolbar.getComponent();
    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(toolbarComponent, BorderLayout.WEST);

    DefaultActionGroup visualActionsGroup = new DefaultActionGroup();
    final Expander expander = new Expander();
    visualActionsGroup.add(CommonActionsManager.getInstance().createExpandAllAction(expander, panel));
    visualActionsGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(expander, panel));

    ToggleShowFlattenAction showFlattenAction = new ToggleShowFlattenAction();
    showFlattenAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, ctrlMask())), panel);
    visualActionsGroup.add(showFlattenAction);
    visualActionsGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));
    visualActionsGroup.add(new ToggleShowIgnoredAction());
    visualActionsGroup.add(new IgnoredSettingsAction());
    visualActionsGroup.add(new ToggleDetailsAction());
    toolbarPanel.add(
      ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, visualActionsGroup, false).getComponent(), BorderLayout.CENTER);


    myView.setMenuActions((DefaultActionGroup)ActionManager.getInstance().getAction("ChangesViewPopupMenu"));

    myView.setShowFlatten(myState.myShowFlatten);

    myProgressLabel = new JPanel(new BorderLayout());

    panel.setToolbar(toolbarPanel);

    final JPanel content = new JPanel(new BorderLayout());
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myView);
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(scrollPane, BorderLayout.CENTER);
    MyChangeProcessor changeProcessor = new MyChangeProcessor(myProject);
    mySplitterComponent =
      new PreviewDiffSplitterComponent(wrapper, changeProcessor, CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION,
                                       myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN);

    content.add(mySplitterComponent, BorderLayout.CENTER);
    content.add(myProgressLabel, BorderLayout.SOUTH);
    panel.setContent(content);

    ChangesDnDSupport.install(myProject, myView);
    myView.addTreeSelectionListener(myTsl);
    return panel;
  }

  @JdkConstants.InputEventMask
  private static int ctrlMask() {
    return SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
  }

  private void updateProgressComponent(@NotNull final Factory<JComponent> progress) {
    //noinspection SSBasedInspection
    SwingUtilities.invokeLater(() -> {
      if (myProgressLabel != null) {
        myProgressLabel.removeAll();
        myProgressLabel.add(progress.create());
        myProgressLabel.setMinimumSize(JBUI.emptySize());
      }
    });
  }

  @Override
  public void updateProgressText(String text, boolean isError) {
    updateProgressComponent(createTextStatusFactory(text, isError));
  }

  @Override
  public void setBusy(final boolean b) {
    UIUtil.invokeLaterIfNeeded(() -> myView.setPaintBusy(b));
  }

  @NotNull
  public static Factory<JComponent> createTextStatusFactory(final String text, final boolean isError) {
    return () -> {
      JLabel label = new JLabel(text);
      label.setForeground(isError ? JBColor.RED : UIUtil.getLabelForeground());
      return label;
    };
  }

  @Override
  public void scheduleRefresh() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    if (myProject.isDisposed()) return;
    int was = myRepaintAlarm.cancelAllRequests();
    if (LOG.isDebugEnabled()) {
      LOG.debug("schedule refresh, was " + was);
    }
    if (!myRepaintAlarm.isDisposed()) {
      myRepaintAlarm.addRequest(() -> refreshView(), 100, ModalityState.NON_MODAL);
    }
  }

  private void refreshView() {
    if (myDisposed || !myProject.isInitialized() || ApplicationManager.getApplication().isUnitTestMode()) return;
    if (!ProjectLevelVcsManager.getInstance(myProject).hasActiveVcss()) return;

    ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);

    TreeModelBuilder treeModelBuilder = new TreeModelBuilder(myProject, myView.isShowFlatten())
      .setChangeLists(changeListManager.getChangeListsCopy())
      .setLocallyDeletedPaths(changeListManager.getDeletedFiles())
      .setModifiedWithoutEditing(changeListManager.getModifiedWithoutEditing())
      .setSwitchedFiles(changeListManager.getSwitchedFilesMap())
      .setSwitchedRoots(changeListManager.getSwitchedRoots())
      .setLockedFolders(changeListManager.getLockedFolders())
      .setLogicallyLockedFiles(changeListManager.getLogicallyLockedFolders())
      .setUnversioned(changeListManager.getUnversionedFiles());
    if (myState.myShowIgnored) {
      treeModelBuilder.setIgnored(changeListManager.getIgnoredFiles(), changeListManager.isIgnoredInUpdateMode());
    }
    myView.updateModel(
      treeModelBuilder.build()
    );

    updatePreview();
  }

  private void updatePreview() {
    if (mySplitterComponent != null) {
      mySplitterComponent.updatePreview();
    }
  }

  @NotNull
  @Override
  public ChangesViewManager.State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull ChangesViewManager.State state) {
    myState = state;
  }

  @Override
  public void setShowFlattenMode(boolean state) {
    myState.myShowFlatten = state;
    myView.setShowFlatten(state);
    refreshView();
  }

  @Override
  public void selectFile(@Nullable VirtualFile vFile) {
    if (vFile == null) return;
    Change change = ChangeListManager.getInstance(myProject).getChange(vFile);
    Object objectToFind = change != null ? change : vFile;

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
    DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, objectToFind);
    if (node != null) {
      TreeUtil.selectNode(myView, node);
    }
  }

  @Override
  public void refreshChangesViewNodeAsync(@NotNull final VirtualFile file) {
    ApplicationManager.getApplication().invokeLater(() -> refreshChangesViewNode(file), myProject.getDisposed());
  }

  private void refreshChangesViewNode(@NotNull VirtualFile file) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    Object userObject = changeListManager.isUnversioned(file) ? file : changeListManager.getChange(file);

    if (userObject != null) {
      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
      DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, userObject);

      if (node != null) {
        myView.getModel().nodeChanged(node);
      }
    }
  }

  @Nullable
  public static ChangesBrowserNode getDropRootNode(@NotNull Tree tree, @NotNull DnDEvent event) {
    RelativePoint dropPoint = event.getRelativePoint();
    Point onTree = dropPoint.getPoint(tree);
    final TreePath dropPath = tree.getPathForLocation(onTree.x, onTree.y);

    if (dropPath == null) return null;

    ChangesBrowserNode dropNode = (ChangesBrowserNode)dropPath.getLastPathComponent();
    while (!((ChangesBrowserNode)dropNode.getParent()).isRoot()) {
      dropNode = (ChangesBrowserNode)dropNode.getParent();
    }
    return dropNode;
  }

  public static class State {

    @Attribute("flattened_view")
    public boolean myShowFlatten = true;

    @Attribute("show_ignored")
    public boolean myShowIgnored;
  }

  private class MyChangeListListener extends ChangeListAdapter {
    @Override
    public void changeListsChanged() {
      scheduleRefresh();
    }

    @Override
    public void changeListUpdateDone() {
      scheduleRefresh();
      ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      VcsException updateException = changeListManager.getUpdateException();
      setBusy(false);
      if (updateException == null) {
        Factory<JComponent> additionalUpdateInfo = changeListManager.getAdditionalUpdateInfo();

        if (additionalUpdateInfo != null) {
          updateProgressComponent(additionalUpdateInfo);
        }
        else {
          updateProgressText("", false);
        }
      }
      else {
        updateProgressText(VcsBundle.message("error.updating.changes", updateException.getMessage()), true);
      }
    }
  }

  private class Expander implements TreeExpander {
    @Override
    public void expandAll() {
      TreeUtil.expandAll(myView);
    }

    @Override
    public boolean canExpand() {
      return true;
    }

    @Override
    public void collapseAll() {
      TreeUtil.collapseAll(myView, 2);
      TreeUtil.expand(myView, 1);
    }

    @Override
    public boolean canCollapse() {
      return true;
    }
  }

  private class ToggleShowFlattenAction extends ToggleAction implements DumbAware {
    public ToggleShowFlattenAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            AllIcons.Actions.GroupByPackage);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return !myState.myShowFlatten;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      setShowFlattenMode(!state);
    }
  }

  private class ToggleShowIgnoredAction extends ToggleAction implements DumbAware {
    public ToggleShowIgnoredAction() {
      super(VcsBundle.message("changes.action.show.ignored.text"),
            VcsBundle.message("changes.action.show.ignored.description"),
            AllIcons.Actions.ShowHiddens);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myState.myShowIgnored;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myState.myShowIgnored = state;
      refreshView();
    }
  }

  private class ToggleDetailsAction extends ShowDiffPreviewAction {
    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      mySplitterComponent.setDetailsOn(state);
      myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = state;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN;
    }
  }

  private class MyChangeProcessor extends ChangeViewDiffRequestProcessor {
    public MyChangeProcessor(@NotNull Project project) {
      super(project, DiffPlaces.CHANGES_VIEW);
      Disposer.register(project, this);
    }

    @Override
    public boolean isWindowFocused() {
      return DiffUtil.isFocusedComponent(myProject, myContent.getComponent());
    }

    @NotNull
    @Override
    protected List<Wrapper> getSelectedChanges() {
      List<Wrapper> result = wrap(myView.getSelectedChanges(), myView.getSelectedUnversionedFiles());
      if (result.isEmpty()) result = getAllChanges();
      return result;
    }

    @NotNull
    @Override
    protected List<Wrapper> getAllChanges() {
      return wrap(myView.getChanges(), myView.getUnversionedFiles());
    }

    @Override
    protected void selectChange(@NotNull Wrapper change) {
      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
      DefaultMutableTreeNode node = findChangeInTree(root, change);
      if (node != null) {
        TreePath path = TreeUtil.getPathFromRoot(node);
        TreeUtil.selectPath(myView, path, false);
      }
    }

    private DefaultMutableTreeNode findChangeInTree(@NotNull DefaultMutableTreeNode root, @NotNull Wrapper change) {
      Object userObject = change.getUserObject();
      if (userObject instanceof ChangeListChange) {
        return TreeUtil.findNode(root, node -> ChangeListChange.HASHING_STRATEGY.equals(node.getUserObject(), userObject));
      }
      return TreeUtil.findNodeWithObject(root, userObject);
    }

    @NotNull
    private List<Wrapper> wrap(@NotNull Stream<Change> changes, @NotNull Stream<VirtualFile> unversioned) {
      return Stream.concat(changes.map(ChangeWrapper::new), unversioned.map(UnversionedFileWrapper::new)).collect(toList());
    }
  }

  private class MyChangeViewContent extends DnDActivateOnHoldTargetContent {
  
    private MyChangeViewContent(JComponent component, String displayName, boolean isLockable) {
      super(myProject, component, displayName, isLockable);
    }

    @Override
    public void drop(DnDEvent event) {
      super.drop(event);
      Object attachedObject = event.getAttachedObject();
      if (attachedObject instanceof ShelvedChangeListDragBean) {
        unshelveSilentlyWithDnd(myProject,(ShelvedChangeListDragBean)attachedObject, getDropRootNode(myView, event));
      }
    }

    @Override
    public boolean isDropPossible(@NotNull DnDEvent event) {
      Object attachedObject = event.getAttachedObject();
      if (attachedObject instanceof ShelvedChangeListDragBean) {
        return !((ShelvedChangeListDragBean)attachedObject).getShelvedChangelists().isEmpty();
      }
      return attachedObject instanceof ChangeListDragBean;
    }
  }
}
