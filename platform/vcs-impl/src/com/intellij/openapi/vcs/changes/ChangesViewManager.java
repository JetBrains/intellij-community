// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.vcs.commit.ChangesViewCommitPanel;
import com.intellij.vcs.commit.ChangesViewCommitWorkflow;
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler;
import com.intellij.vcs.commit.CommitWorkflowManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.openapi.actionSystem.EmptyAction.registerWithShortcutSet;
import static com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager.unshelveSilentlyWithDnd;
import static com.intellij.openapi.vcs.changes.ui.ChangesTree.DEFAULT_GROUPING_KEYS;
import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;
import static com.intellij.ui.IdeBorderFactory.createBorder;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.containers.ContainerUtil.set;
import static com.intellij.util.ui.JBUI.Panels.simplePanel;
import static com.intellij.util.ui.UIUtil.addBorder;
import static java.util.stream.Collectors.toList;

@State(
  name = "ChangesViewManager",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class ChangesViewManager implements ChangesViewI, ProjectComponent, PersistentStateComponent<ChangesViewManager.State> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.ChangesViewManager");
  private static final String CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION = "ChangesViewManager.DETAILS_SPLITTER_PROPORTION";

  @NotNull private final ChangesListView myView;
  private ChangesViewCommitPanel myCommitPanel;
  private BorderLayoutPanel myContentPanel;
  private ChangesViewCommitPanelSplitter myCommitPanelSplitter;
  private SimpleToolWindowPanel myToolWindowPanel;
  private ChangesViewCommitWorkflowHandler myCommitWorkflowHandler;
  private final VcsConfiguration myVcsConfiguration;
  private JPanel myProgressLabel;

  private final Alarm myTreeUpdateAlarm;
  private final Object myTreeUpdateIndicatorLock = new Object();
  @NotNull private ProgressIndicator myTreeUpdateIndicator = new EmptyProgressIndicator();

  private boolean myDisposed = false;

  @NotNull private final Project myProject;
  @NotNull private final ChangesViewContentManager myContentManager;

  @NotNull private ChangesViewManager.State myState = new ChangesViewManager.State();

  private PreviewDiffSplitterComponent mySplitterComponent;

  @NotNull private final TreeSelectionListener myTsl;
  @NotNull private final PropertyChangeListener myGroupingChangeListener;
  private MyChangeViewContent myContent;
  private boolean myModelUpdateInProgress;
  private final MyTreeExpander myTreeExpander;

  @NotNull
  public static ChangesViewI getInstance(@NotNull Project project) {
    return project.getComponent(ChangesViewI.class);
  }

  public ChangesViewManager(@NotNull Project project, @NotNull ChangesViewContentManager contentManager) {
    myProject = project;
    myContentManager = contentManager;
    myVcsConfiguration = VcsConfiguration.getInstance(myProject);
    myView = new ChangesListView(project, false);
    myTreeExpander = new MyTreeExpander();
    myView.setTreeExpander(myTreeExpander);
    myTreeUpdateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
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
        boolean fromModelRefresh = myModelUpdateInProgress;
        ApplicationManager.getApplication().invokeLater(() -> updatePreview(fromModelRefresh));
      }
    };
    myGroupingChangeListener = e -> {
      myState.groupingKeys = myView.getGroupingSupport().getGroupingKeys();
      scheduleRefresh();
    };
  }

  @Override
  public void projectOpened() {
    ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListListener(), myProject);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;

    myToolWindowPanel = createChangeViewComponent();
    myContent = new MyChangeViewContent(myToolWindowPanel, ChangesViewContentManager.LOCAL_CHANGES, false);
    myContent.setHelpId(ChangesListView.HELP_ID);
    myContent.setCloseable(false);
    myContentManager.addContent(myContent);

    CommitWorkflowManager.install(myProject);

    scheduleRefresh();
    myProject.getMessageBus().connect().subscribe(RemoteRevisionsCache.REMOTE_VERSION_CHANGED, () -> scheduleRefresh());
    updatePreview(false);
  }

  @Override
  public void projectClosed() {
    myView.removeTreeSelectionListener(myTsl);
    myView.removeGroupingChangeListener(myGroupingChangeListener);
    myDisposed = true;
    myTreeUpdateAlarm.cancelAllRequests();

    synchronized (myTreeUpdateIndicatorLock) {
      myTreeUpdateIndicator.cancel();
    }
  }

  @Override
  @NonNls @NotNull
  public String getComponentName() {
    return "ChangesViewManager";
  }

  @Nullable
  public ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    return myCommitWorkflowHandler;
  }

  public void updateCommitWorkflow(boolean isNonModal) {
    if (isNonModal) {
      if (myCommitPanel == null) {
        myCommitPanel = new ChangesViewCommitPanel(myView, myToolWindowPanel);
        myCommitWorkflowHandler = new ChangesViewCommitWorkflowHandler(new ChangesViewCommitWorkflow(myProject), myCommitPanel);
        Disposer.register(myContent, myCommitPanel);

        myCommitPanelSplitter.setSecondComponent(myCommitPanel);
      }
    }
    else if (myCommitPanel != null) {
      myCommitPanelSplitter.setSecondComponent(null);
      Disposer.dispose(myCommitPanel);

      myCommitPanel = null;
      myCommitWorkflowHandler = null;
    }
  }

  @NotNull
  private SimpleToolWindowPanel createChangeViewComponent() {
    ActionToolbar changesToolbar = createChangesToolbar();
    addBorder(changesToolbar.getComponent(), createBorder(JBColor.border(), SideBorder.RIGHT));
    BorderLayoutPanel changesPanel = simplePanel(createScrollPane(myView)).addToLeft(changesToolbar.getComponent());

    myCommitPanelSplitter = new ChangesViewCommitPanelSplitter();
    myCommitPanelSplitter.setFirstComponent(changesPanel);
    myContentPanel = new BorderLayoutPanel() {
      @Override
      public Dimension getMinimumSize() {
        return isMinimumSizeSet() ? super.getMinimumSize() : changesToolbar.getComponent().getPreferredSize();
      }
    };
    myContentPanel.addToCenter(myCommitPanelSplitter);

    MyChangeProcessor changeProcessor = new MyChangeProcessor(myProject);
    mySplitterComponent = new PreviewDiffSplitterComponent(myContentPanel, changeProcessor, CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION,
                                                           myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN);

    myView.installPopupHandler((DefaultActionGroup)ActionManager.getInstance().getAction("ChangesViewPopupMenu"));
    myView.getGroupingSupport().setGroupingKeysOrSkip(myState.groupingKeys);
    ChangesDnDSupport.install(myProject, myView);
    myView.addTreeSelectionListener(myTsl);
    myView.addGroupingChangeListener(myGroupingChangeListener);

    SimpleToolWindowPanel panel = new SimpleToolWindowPanel(false, true) {
      @NotNull
      @Override
      public List<AnAction> getActions(boolean originalProvider) {
        return changesToolbar.getActions();
      }

      @Nullable
      @Override
      public Object getData(@NotNull String dataId) {
        Object data = super.getData(dataId);
        if (data != null) return data;
        // This makes COMMIT_WORKFLOW_HANDLER available anywhere in "Local Changes" - so commit executor actions are enabled.
        return myCommitPanel != null ? myCommitPanel.getDataFromProviders(dataId) : null;
      }
    };
    myProgressLabel = simplePanel();
    panel.setContent(simplePanel(mySplitterComponent).addToBottom(myProgressLabel));
    registerShortcuts(panel);
    return panel;
  }

  private static void registerShortcuts(@NotNull JComponent component) {
    registerWithShortcutSet("ChangesView.Refresh", CommonShortcuts.getRerun(), component);
    registerWithShortcutSet("ChangesView.NewChangeList", CommonShortcuts.getNew(), component);
    registerWithShortcutSet("ChangesView.RemoveChangeList", CommonShortcuts.getDelete(), component);
    registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), component);
  }

  @NotNull
  private ActionToolbar createChangesToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(CustomActionsSchema.getInstance().getCorrectedAction(ActionPlaces.CHANGES_VIEW_TOOLBAR));

    group.addSeparator();
    group.add(ActionManager.getInstance().getAction(GROUP_BY_ACTION_GROUP));

    DefaultActionGroup viewOptionsGroup = new DefaultActionGroup("View Options", true);
    viewOptionsGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Show);
    viewOptionsGroup.add(new ToggleShowIgnoredAction());
    viewOptionsGroup.add(ActionManager.getInstance().getAction("ChangesView.ViewOptions"));

    group.add(viewOptionsGroup);
    group.add(CommonActionsManager.getInstance().createExpandAllHeaderAction(myTreeExpander, myView));
    group.add(CommonActionsManager.getInstance().createCollapseAllAction(myTreeExpander, myView));
    group.addSeparator();
    group.add(new ToggleDetailsAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, group, false);
    toolbar.setTargetComponent(myView);
    return toolbar;
  }

  private void updateProgressComponent(@NotNull final Factory<? extends JComponent> progress) {
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
    int was = myTreeUpdateAlarm.cancelAllRequests();
    if (LOG.isDebugEnabled()) {
      LOG.debug("schedule refresh, was " + was);
    }
    if (!myTreeUpdateAlarm.isDisposed()) {
      myTreeUpdateAlarm.addRequest(() -> refreshView(), 100);
    }
  }

  public void refreshImmediately() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myTreeUpdateAlarm.cancelAllRequests();

    ProgressManager.getInstance().executeNonCancelableSection(() -> refreshView());
  }

  private void refreshView() {
    ProgressIndicator indicator = new EmptyProgressIndicator();
    synchronized (myTreeUpdateIndicatorLock) {
      myTreeUpdateIndicator.cancel();
      myTreeUpdateIndicator = indicator;
    }

    ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      if (myDisposed || !myProject.isInitialized() || ApplicationManager.getApplication().isUnitTestMode()) return;
      if (!ProjectLevelVcsManager.getInstance(myProject).hasActiveVcss()) return;

      ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      List<LocalChangeList> changeLists = changeListManager.getChangeListsCopy();
      List<VirtualFile> unversionedFiles = changeListManager.getUnversionedFiles();

      TreeModelBuilder treeModelBuilder = new TreeModelBuilder(myProject, myView.getGrouping())
        .setChangeLists(changeLists, Registry.is("vcs.skip.single.default.changelist"))
        .setLocallyDeletedPaths(changeListManager.getDeletedFiles())
        .setModifiedWithoutEditing(changeListManager.getModifiedWithoutEditing())
        .setSwitchedFiles(changeListManager.getSwitchedFilesMap())
        .setSwitchedRoots(changeListManager.getSwitchedRoots())
        .setLockedFolders(changeListManager.getLockedFolders())
        .setLogicallyLockedFiles(changeListManager.getLogicallyLockedFolders())
        .setUnversioned(unversionedFiles);
      if (myState.myShowIgnored) {
        treeModelBuilder.setIgnored(changeListManager.getIgnoredFiles(), changeListManager.isIgnoredInUpdateMode());
      }
      for (ChangesViewModifier extension : ChangesViewModifier.KEY.getExtensions(myProject)) {
        extension.modifyTreeModelBuilder(treeModelBuilder);
      }
      DefaultTreeModel newModel = treeModelBuilder.build();

      GuiUtils.invokeLaterIfNeeded(() -> {
        if (myDisposed) return;
        indicator.checkCanceled();

        myModelUpdateInProgress = true;
        try {
          myView.updateModel(newModel);
          if (myCommitWorkflowHandler != null) myCommitWorkflowHandler.synchronizeInclusion(changeLists, unversionedFiles);
        }
        finally {
          myModelUpdateInProgress = false;
        }
        updatePreview(true);
      }, ModalityState.NON_MODAL);
    }, indicator);
  }

  private void updatePreview(boolean fromModelRefresh) {
    if (mySplitterComponent != null) {
      mySplitterComponent.updatePreview(fromModelRefresh);
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
    migrateShowFlattenSetting();
  }

  private void migrateShowFlattenSetting() {
    if (!myState.myShowFlatten) {
      myState.groupingKeys = set(DEFAULT_GROUPING_KEYS);
      myState.myShowFlatten = true;
    }
  }

  @Override
  public void setGrouping(@NotNull String groupingKey) {
    myView.getGroupingSupport().setGroupingKeysOrSkip(set(groupingKey));
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
  public void selectChanges(@NotNull List<? extends Change> changes) {
    List<TreePath> paths = new ArrayList<>();

    for (Change change : changes) {
      ContainerUtil.addIfNotNull(paths, myView.findNodePathInTree(change));
    }

    TreeUtil.selectPaths(myView, paths);
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
    while (!dropNode.getParent().isRoot()) {
      dropNode = dropNode.getParent();
    }
    return dropNode;
  }

  public static class State {

    @Deprecated
    @Attribute("flattened_view")
    public boolean myShowFlatten = true;

    @XCollection
    public Set<String> groupingKeys = new HashSet<>();

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

  private class MyTreeExpander extends DefaultTreeExpander {
    MyTreeExpander() {
      super(myView);
    }

    @Override
    public void collapseAll() {
      TreeUtil.collapseAll(myView, 2);
      TreeUtil.expand(myView, 1);
    }
  }


  private class ToggleShowIgnoredAction extends ToggleAction implements DumbAware {
    ToggleShowIgnoredAction() {
      super(VcsBundle.message("changes.action.show.ignored.text"),
            VcsBundle.message("changes.action.show.ignored.description"),
            AllIcons.Actions.ShowHiddens);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myState.myShowIgnored;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myState.myShowIgnored = state;
      refreshView();
    }
  }

  private class ToggleDetailsAction extends ShowDiffPreviewAction {
    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      mySplitterComponent.setDetailsOn(state);
      myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = state;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN;
    }
  }

  private class MyChangeProcessor extends ChangeViewDiffRequestProcessor {
    MyChangeProcessor(@NotNull Project project) {
      super(project, DiffPlaces.CHANGES_VIEW);
      Disposer.register(project, this);

      putContextUserData(DiffUserDataKeysEx.LAST_REVISION_WITH_LOCAL, true);
    }

    @NotNull
    @Override
    protected List<Wrapper> getSelectedChanges() {
      boolean hasSelection = myView.getSelectionCount() != 0;
      if (hasSelection) {
        return wrap(myView.getSelectedChanges(), myView.getSelectedUnversionedFiles());
      }
      else {
        return getAllChanges();
      }
    }

    @NotNull
    @Override
    protected List<Wrapper> getAllChanges() {
      return wrap(myView.getChanges(), myView.getUnversionedFiles());
    }

    @Override
    protected void selectChange(@NotNull Wrapper change) {
      TreePath path = myView.findNodePathInTree(change.getUserObject());
      if (path != null) {
        TreeUtil.selectPath(myView, path, false);
      }
    }

    @NotNull
    private List<Wrapper> wrap(@NotNull Stream<? extends Change> changes, @NotNull Stream<? extends VirtualFile> unversioned) {
      return Stream.concat(changes.map(ChangeWrapper::new), unversioned.map(UnversionedFileWrapper::new)).collect(toList());
    }
  }

  private class MyChangeViewContent extends DnDActivateOnHoldTargetContent {

    private MyChangeViewContent(JComponent component, @NotNull String displayName, boolean isLockable) {
      super(myProject, component, displayName, isLockable);
    }

    @Override
    public void drop(DnDEvent event) {
      super.drop(event);
      Object attachedObject = event.getAttachedObject();
      if (attachedObject instanceof ShelvedChangeListDragBean) {
        unshelveSilentlyWithDnd(myProject, (ShelvedChangeListDragBean)attachedObject, getDropRootNode(myView, event),
                                !ChangesDnDSupport.isCopyAction(event));
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
