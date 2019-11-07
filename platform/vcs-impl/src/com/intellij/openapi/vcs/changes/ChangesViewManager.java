// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
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
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.ProblemListener;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.vcs.commit.*;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.openapi.actionSystem.EmptyAction.registerWithShortcutSet;
import static com.intellij.openapi.vcs.changes.ui.ChangesTree.DEFAULT_GROUPING_KEYS;
import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;
import static com.intellij.util.containers.ContainerUtil.set;
import static com.intellij.util.ui.JBUI.Panels.simplePanel;
import static com.intellij.vcs.commit.ToggleChangesViewCommitUiActionKt.isToggleCommitUi;
import static java.util.Arrays.asList;

@State(
  name = "ChangesViewManager",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class ChangesViewManager implements ChangesViewEx,
                                           PersistentStateComponent<ChangesViewManager.State>,
                                           Disposable {

  private static final Logger LOG = Logger.getInstance(ChangesViewManager.class);
  private static final String CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION = "ChangesViewManager.DETAILS_SPLITTER_PROPORTION";

  @NotNull private final Project myProject;

  @NotNull private ChangesViewManager.State myState = new ChangesViewManager.State();

  @Nullable private ChangesViewToolWindowPanel myToolWindowPanel;

  @NotNull
  public static ChangesViewI getInstance(@NotNull Project project) {
    return project.getService(ChangesViewI.class);
  }

  @NotNull
  public static ChangesViewEx getInstanceEx(@NotNull Project project) {
    return (ChangesViewEx)getInstance(project);
  }

  public ChangesViewManager(@NotNull Project project) {
    myProject = project;
  }

  public static class ContentPreloader implements ChangesViewContentProvider.Preloader {
    @NotNull private final Project myProject;

    public ContentPreloader(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void preloadTabContent(@NotNull Content content) {
      content.putUserData(Content.TAB_DND_TARGET_KEY, new MyContentDnDTarget(myProject, content));
    }
  }

  public static class ContentProvider implements ChangesViewContentProvider {
    @NotNull private final Project myProject;

    public ContentProvider(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void initTabContent(@NotNull Content content) {
      ChangesViewManager viewManager = (ChangesViewManager)getInstance(myProject);
      ChangesViewToolWindowPanel panel = viewManager.initToolWindowPanel();

      content.setHelpId(ChangesListView.HELP_ID);
      content.setComponent(panel);
      content.setPreferredFocusableComponent(panel.myView);
    }
  }

  @NotNull
  @CalledInAwt
  private ChangesViewToolWindowPanel initToolWindowPanel() {
    if (myToolWindowPanel == null) {
      ChangesViewToolWindowPanel panel = new ChangesViewToolWindowPanel(myProject, this);
      Disposer.register(myProject, panel);
      myToolWindowPanel = panel;
    }
    return myToolWindowPanel;
  }

  @Override
  public void dispose() {
    myToolWindowPanel = null;
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

  public static class State {
    /**
     * @deprecated Use {@link #groupingKeys} instead.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Attribute("flattened_view")
    public boolean myShowFlatten = true;

    @XCollection
    public Set<String> groupingKeys = new HashSet<>();

    @Attribute("show_ignored")
    public boolean myShowIgnored;
  }


  @Override
  public void scheduleRefresh() {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.scheduleRefresh();
  }

  @Override
  public void selectFile(VirtualFile vFile) {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.selectFile(vFile);
  }

  @Override
  public void selectChanges(@NotNull List<? extends Change> changes) {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.selectChanges(changes);
  }

  @Override
  public void updateProgressText(String text, boolean isError) {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.updateProgressText(text, isError);
  }

  @Override
  public void setBusy(boolean b) {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.setBusy(b);
  }

  @Override
  public void setGrouping(@NotNull String groupingKey) {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.setGrouping(groupingKey);
  }

  @Override
  public void updateCommitWorkflow() {
    boolean isNonModal = CommitWorkflowManager.getInstance(myProject).isNonModal();
    if (myToolWindowPanel != null) {
      myToolWindowPanel.updateCommitWorkflow(isNonModal);
    }
    else if (isNonModal) {
      // CommitWorkflowManager needs our ChangesViewCommitWorkflowHandler to work
      initToolWindowPanel().updateCommitWorkflow(isNonModal);
    }
  }

  @Override
  @Nullable
  public ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    if (myToolWindowPanel == null) return null;
    return myToolWindowPanel.getCommitWorkflowHandler();
  }

  @Override
  public void refreshImmediately() {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.refreshImmediately();
  }

  @Override
  public boolean isAllowExcludeFromCommit() {
    if (myToolWindowPanel == null) return false;
    return myToolWindowPanel.isAllowExcludeFromCommit();
  }

  public static class ChangesViewToolWindowPanel extends SimpleToolWindowPanel implements Disposable {
    @NotNull private static final RegistryValue isToolbarHorizontalSetting = Registry.get("vcs.local.changes.toolbar.horizontal");
    @NotNull private static final RegistryValue isCommitSplitHorizontal =
      Registry.get("vcs.non.modal.commit.split.horizontal.if.no.diff.preview");

    @NotNull private final Project myProject;
    @NotNull private final ChangesViewManager myChangesViewManager;
    @NotNull private final VcsConfiguration myVcsConfiguration;

    @NotNull private final ChangesViewPanel myChangesPanel;
    @NotNull private final ChangesListView myView;

    @NotNull private final ChangesViewCommitPanelSplitter myCommitPanelSplitter;
    @NotNull private final ChangesViewPreview myDiffPreview;
    @NotNull private final Wrapper myProgressLabel = new Wrapper();

    @Nullable private ChangesViewCommitPanel myCommitPanel;
    @Nullable private ChangesViewCommitWorkflowHandler myCommitWorkflowHandler;

    @NotNull private final Alarm myTreeUpdateAlarm;
    @NotNull private final Object myTreeUpdateIndicatorLock = new Object();
    @NotNull private ProgressIndicator myTreeUpdateIndicator = new EmptyProgressIndicator();
    private boolean myModelUpdateInProgress;

    private boolean myDisposed = false;

    private ChangesViewToolWindowPanel(@NotNull Project project, @NotNull ChangesViewManager changesViewManager) {
      super(false, true);
      myProject = project;
      myChangesViewManager = changesViewManager;
      CommitWorkflowManager commitWorkflowManager = CommitWorkflowManager.getInstance(myProject);

      myVcsConfiguration = VcsConfiguration.getInstance(myProject);
      myTreeUpdateAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

      myChangesPanel = new ChangesViewPanel(project);
      myView = myChangesPanel.getChangesView();
      myView.installPopupHandler((DefaultActionGroup)ActionManager.getInstance().getAction("ChangesViewPopupMenu"));
      myView.getGroupingSupport().setGroupingKeysOrSkip(myChangesViewManager.myState.groupingKeys);
      myView.addGroupingChangeListener(e -> {
        myChangesViewManager.myState.groupingKeys = myView.getGroupingSupport().getGroupingKeys();
        scheduleRefresh();
      });
      ChangesDnDSupport.install(myProject, myView);

      myChangesPanel.getToolbarActionGroup().addAll(createChangesToolbarActions(myView.getTreeExpander()));
      myChangesPanel.setToolbarHorizontal(commitWorkflowManager.isNonModal() && isToolbarHorizontalSetting.asBoolean());
      registerShortcuts(this);

      isToolbarHorizontalSetting.addListener(new RegistryValueListener.Adapter() {
        @Override
        public void afterValueChanged(@NotNull RegistryValue value) {
          boolean isToolbarHorizontal = value.asBoolean() && commitWorkflowManager.isNonModal();

          myChangesPanel.setToolbarHorizontal(isToolbarHorizontal);
          if (myCommitPanel != null) myCommitPanel.setToolbarHorizontal(isToolbarHorizontal);
        }
      }, this);

      myCommitPanelSplitter = new ChangesViewCommitPanelSplitter();
      myCommitPanelSplitter.setFirstComponent(myChangesPanel);

      BorderLayoutPanel contentPanel = new BorderLayoutPanel() {
        @Override
        public Dimension getMinimumSize() {
          return isMinimumSizeSet() || myChangesPanel.isToolbarHorizontal()
                 ? super.getMinimumSize()
                 : myChangesPanel.getToolbar().getComponent().getPreferredSize();
        }
      };
      contentPanel.addToCenter(myCommitPanelSplitter);

      ChangesViewDiffPreviewProcessor changeProcessor = new ChangesViewDiffPreviewProcessor(myView);
      Disposer.register(this, changeProcessor);

      JComponent mainPanel;
      if (Registry.is("show.diff.preview.as.editor.tab")) {
        myDiffPreview = new EditorTabPreview(changeProcessor, myProject,
          contentPanel, myView){

          @Override
          protected String getCurrentName() {
            return changeProcessor.getCurrentChangeName();
          }

          @Override
          protected boolean shouldSkip() {
            return myModelUpdateInProgress;
          }

          @Override
          protected void doRefresh() {
            changeProcessor.refresh(false);
          }
        };
        mainPanel = contentPanel;

        myView.setExpandableItemsEnabled(false);
      }
      else {
        PreviewDiffSplitterComponent splitter =
          new PreviewDiffSplitterComponent(contentPanel, changeProcessor,
                                           CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION,
                                           myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN);
        myDiffPreview = new PanelPreview(splitter);
        mainPanel = splitter;

        myView.addTreeSelectionListener(e -> {
          boolean fromModelRefresh = myModelUpdateInProgress;
          invokeLater(() -> myDiffPreview.updatePreview(fromModelRefresh));
        });
      }
      setContent(simplePanel(mainPanel).addToBottom(myProgressLabel));

      setCommitSplitOrientation();
      isCommitSplitHorizontal.addListener(new RegistryValueListener.Adapter() {
        @Override
        public void afterValueChanged(@NotNull RegistryValue value) {
          setCommitSplitOrientation();
        }
      }, this);

      isToggleCommitUi().addListener(new RegistryValueListener.Adapter() {
        @Override
        public void afterValueChanged(@NotNull RegistryValue value) {
          if (myCommitWorkflowHandler == null) return;

          if (value.asBoolean()) {
            myCommitWorkflowHandler.deactivate(false);
          }
          else {
            myCommitWorkflowHandler.activate();
          }
        }
      }, this);

      MessageBusConnection busConnection = myProject.getMessageBus().connect(this);
      busConnection.subscribe(RemoteRevisionsCache.REMOTE_VERSION_CHANGED, () -> scheduleRefresh());
      busConnection.subscribe(ProblemListener.TOPIC, new ProblemListener() {
        @Override
        public void problemsAppeared(@NotNull VirtualFile file) {
          refreshChangesViewNodeAsync(file);
        }

        @Override
        public void problemsDisappeared(@NotNull VirtualFile file) {
          refreshChangesViewNodeAsync(file);
        }
      });
      ChangeListManager.getInstance(myProject).addChangeListListener(new MyChangeListListener(), this);

      scheduleRefresh();
      myDiffPreview.updatePreview(false);
    }

    @Override
    public void dispose() {
      myDisposed = true;
      myTreeUpdateAlarm.cancelAllRequests();

      synchronized (myTreeUpdateIndicatorLock) {
        myTreeUpdateIndicator.cancel();
      }
    }

    @Nullable
    public ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
      return myCommitWorkflowHandler;
    }

    public void updateCommitWorkflow(boolean isNonModal) {
      if (isNonModal) {
        if (myCommitPanel == null) {
          myChangesPanel.setToolbarHorizontal(isToolbarHorizontalSetting.asBoolean());
          myCommitPanel = myChangesViewManager.createCommitPanel(myView, this);
          myCommitPanel.setToolbarHorizontal(isToolbarHorizontalSetting.asBoolean());
          myCommitWorkflowHandler = new ChangesViewCommitWorkflowHandler(new ChangesViewCommitWorkflow(myProject), myCommitPanel);
          if (isToggleCommitUi().asBoolean()) myCommitWorkflowHandler.deactivate(false);
          Disposer.register(this, myCommitPanel);

          myCommitPanelSplitter.setSecondComponent(myCommitPanel);
          myDiffPreview.setAllowExcludeFromCommit(isAllowExcludeFromCommit());
          myCommitWorkflowHandler.addActivityListener(
            () -> myDiffPreview.setAllowExcludeFromCommit(isAllowExcludeFromCommit()),
            myCommitWorkflowHandler
          );
        }
      }
      else {
        myChangesPanel.setToolbarHorizontal(false);
        if (myCommitPanel != null) {

          myDiffPreview.setAllowExcludeFromCommit(false);
          myCommitPanelSplitter.setSecondComponent(null);
          Disposer.dispose(myCommitPanel);

          myCommitPanel = null;
          myCommitWorkflowHandler = null;
        }
      }
    }

    public boolean isAllowExcludeFromCommit() {
      return myCommitWorkflowHandler != null && myCommitWorkflowHandler.isActive();
    }

    private void setCommitSplitOrientation() {
      boolean hasPreviewPanel = myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN && myDiffPreview instanceof PanelPreview;
      myCommitPanelSplitter.setOrientation(hasPreviewPanel || !isCommitSplitHorizontal.asBoolean());
    }

    @NotNull
    private Function<ChangeNodeDecorator, ChangeNodeDecorator> getChangeDecoratorProvider() {
      return baseDecorator -> new PartialCommitChangeNodeDecorator(myProject, baseDecorator, () -> isAllowExcludeFromCommit());
    }

    @NotNull
    @Override
    public List<AnAction> getActions(boolean originalProvider) {
      return asList(myChangesPanel.getToolbarActionGroup().getChildren(null));
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      Object data = super.getData(dataId);
      if (data != null) return data;
      // This makes COMMIT_WORKFLOW_HANDLER available anywhere in "Local Changes" - so commit executor actions are enabled.
      return myCommitPanel != null ? myCommitPanel.getDataFromProviders(dataId) : null;
    }

    private static void registerShortcuts(@NotNull JComponent component) {
      registerWithShortcutSet("ChangesView.Refresh", CommonShortcuts.getRerun(), component);
      registerWithShortcutSet("ChangesView.NewChangeList", CommonShortcuts.getNew(), component);
      registerWithShortcutSet("ChangesView.RemoveChangeList", CommonShortcuts.getDelete(), component);
      registerWithShortcutSet(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST, CommonShortcuts.getMove(), component);
    }

    @NotNull
    private List<AnAction> createChangesToolbarActions(@NotNull TreeExpander treeExpander) {
      List<AnAction> actions = new ArrayList<>();
      actions.add(CustomActionsSchema.getInstance().getCorrectedAction(ActionPlaces.CHANGES_VIEW_TOOLBAR));

      actions.add(Separator.getInstance());
      actions.add(ActionManager.getInstance().getAction(GROUP_BY_ACTION_GROUP));

      DefaultActionGroup viewOptionsGroup = new DefaultActionGroup("View Options", true);
      viewOptionsGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Show);
      viewOptionsGroup.add(new ToggleShowIgnoredAction());
      viewOptionsGroup.add(ActionManager.getInstance().getAction("ChangesView.ViewOptions"));

      actions.add(viewOptionsGroup);
      actions.add(CommonActionsManager.getInstance().createExpandAllHeaderAction(treeExpander, myView));
      actions.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, myView));
      actions.add(Separator.getInstance());
      actions.add(new ToggleDetailsAction());

      return actions;
    }

    private void updateProgressComponent(@Nullable Factory<? extends JComponent> progress) {
      invokeLaterIfNeeded(() -> {
        if (myDisposed) return;
        JComponent component = progress != null ? progress.create() : null;
        myProgressLabel.setContent(component);
        myProgressLabel.setMinimumSize(JBUI.emptySize());
      });
    }

    public void updateProgressText(String text, boolean isError) {
      updateProgressComponent(createTextStatusFactory(text, isError));
    }

    public void setBusy(final boolean b) {
      invokeLaterIfNeeded(() -> myView.setPaintBusy(b));
    }

    public void scheduleRefresh() {
      if (myDisposed) return;
      myTreeUpdateAlarm.cancelAllRequests();
      myTreeUpdateAlarm.addRequest(() -> refreshView(), 100);
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
        List<FilePath> unversionedFiles = changeListManager.getUnversionedFilesPaths();

        TreeModelBuilder treeModelBuilder = new TreeModelBuilder(myProject, myView.getGrouping())
          .setChangeLists(changeLists, Registry.is("vcs.skip.single.default.changelist"), getChangeDecoratorProvider())
          .setLocallyDeletedPaths(changeListManager.getDeletedFiles())
          .setModifiedWithoutEditing(changeListManager.getModifiedWithoutEditing())
          .setSwitchedFiles(changeListManager.getSwitchedFilesMap())
          .setSwitchedRoots(changeListManager.getSwitchedRoots())
          .setLockedFolders(changeListManager.getLockedFolders())
          .setLogicallyLockedFiles(changeListManager.getLogicallyLockedFolders())
          .setUnversioned(unversionedFiles);
        if (myChangesViewManager.myState.myShowIgnored) {
          treeModelBuilder.setIgnored(changeListManager.getIgnoredFilePaths(), changeListManager.isIgnoredInUpdateMode());
        }
        for (ChangesViewModifier extension : ChangesViewModifier.KEY.getExtensions(myProject)) {
          extension.modifyTreeModelBuilder(treeModelBuilder);
        }
        DefaultTreeModel newModel = treeModelBuilder.build();

        invokeLaterIfNeeded(() -> {
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
          myDiffPreview.updatePreview(true);
        });
      }, indicator);
    }

    public void setGrouping(@NotNull String groupingKey) {
      myView.getGroupingSupport().setGroupingKeysOrSkip(set(groupingKey));
    }

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

    public void selectChanges(@NotNull List<? extends Change> changes) {
      List<TreePath> paths = new ArrayList<>();

      for (Change change : changes) {
        ContainerUtil.addIfNotNull(paths, myView.findNodePathInTree(change));
      }

      TreeUtil.selectPaths(myView, paths);
    }


    public void refreshChangesViewNodeAsync(@NotNull VirtualFile file) {
      invokeLater(() -> refreshChangesViewNode(file));
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

    private void invokeLater(Runnable runnable) {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL, myProject.getDisposed());
    }

    private void invokeLaterIfNeeded(Runnable runnable) {
      GuiUtils.invokeLaterIfNeeded(runnable, ModalityState.NON_MODAL, myProject.getDisposed());
    }

    private class MyChangeListListener extends ChangeListAdapter {
      @Override
      public void changeListsChanged() {
        scheduleRefresh();
      }

      @Override
      public void changeListUpdateDone() {
        setBusy(false);
        scheduleRefresh();

        ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
        VcsException updateException = changeListManager.getUpdateException();
        if (updateException == null) {
          updateProgressComponent(changeListManager.getAdditionalUpdateInfo());
        }
        else {
          updateProgressText(VcsBundle.message("error.updating.changes", updateException.getMessage()), true);
        }
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
        return myChangesViewManager.myState.myShowIgnored;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        myChangesViewManager.myState.myShowIgnored = state;
        refreshView();
      }
    }

    private class ToggleDetailsAction extends ShowDiffPreviewAction {
      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = state;
        myDiffPreview.setDiffPreviewVisible(state);
        setCommitSplitOrientation();
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN;
      }
    }

    private class PanelPreview implements ChangesViewPreview {
      @NotNull private final PreviewDiffSplitterComponent myPreviewSplitter;

      private PanelPreview(@NotNull PreviewDiffSplitterComponent previewSplitter) {
        myPreviewSplitter = previewSplitter;
      }

      @Override
      public void updatePreview(boolean fromModelRefresh) {
        myPreviewSplitter.updatePreview(fromModelRefresh);
      }

      @Override
      public void setDiffPreviewVisible(boolean isVisible) {
        myPreviewSplitter.setDetailsOn(isVisible);
      }

      @Override
      public void setAllowExcludeFromCommit(boolean value) {
        myPreviewSplitter.setAllowExcludeFromCommit(value);
      }
    }


  }

  protected ChangesViewCommitPanel createCommitPanel(@NotNull ChangesListView myView, @NotNull ChangesViewToolWindowPanel changesViewToolWindowPanel) {
      return new ChangesViewCommitPanel(myView, changesViewToolWindowPanel);
  }

  private static class MyContentDnDTarget extends VcsToolwindowDnDTarget {
    private MyContentDnDTarget(@NotNull Project project, @NotNull Content content) {
      super(project, content);
    }

    @Override
    public void drop(DnDEvent event) {
      super.drop(event);
      Object attachedObject = event.getAttachedObject();
      if (attachedObject instanceof ShelvedChangeListDragBean) {
        ChangesViewToolWindowPanel panel = ((ChangesViewManager)getInstance(myProject)).initToolWindowPanel();
        ShelveChangesManager.unshelveSilentlyWithDnd(myProject, (ShelvedChangeListDragBean)attachedObject,
                                                     ChangesDnDSupport.getDropRootNode(panel.myView, event),
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

  @NotNull
  public static Factory<JComponent> createTextStatusFactory(final String text, final boolean isError) {
    return () -> {
      JLabel label = new JLabel(text);
      label.setForeground(isError ? JBColor.RED : UIUtil.getLabelForeground());
      return label;
    };
  }
}