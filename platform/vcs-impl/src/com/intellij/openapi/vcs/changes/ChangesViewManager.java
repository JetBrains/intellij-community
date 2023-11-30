// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diff.util.DiffUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.InitialVfsRefreshService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffFromLocalChangesActionProvider;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vcs.impl.LineStatusTrackerSettingListener;
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpan.ChangesView;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.problems.ProblemListener;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.util.EditSourceOnDoubleClickHandler;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.vcs.commit.*;
import com.intellij.vcsUtil.VcsUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.openapi.vcs.changes.ui.ChangesTree.DEFAULT_GROUPING_KEYS;
import static com.intellij.openapi.vcs.changes.ui.ChangesTree.GROUP_BY_ACTION_GROUP;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.LOCAL_CHANGES;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.getToolWindowFor;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerKt.isCommitToolWindowShown;
import static com.intellij.util.ui.JBUI.Panels.simplePanel;
import static java.util.Arrays.asList;
import static org.jetbrains.concurrency.Promises.cancelledPromise;

@State(name = "ChangesViewManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class ChangesViewManager implements ChangesViewEx,
                                           PersistentStateComponent<ChangesViewManager.State>,
                                           Disposable {

  private static final Tracer TRACER = TelemetryManager.getInstance().getTracer(VcsScopeKt.VcsScope);
  private static final String CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION = "ChangesViewManager.DETAILS_SPLITTER_PROPORTION";

  private final @NotNull Project myProject;

  private @NotNull ChangesViewManager.State myState = new ChangesViewManager.State();

  private @Nullable ChangesViewPanel myChangesPanel;
  private @Nullable ChangesViewToolWindowPanel myToolWindowPanel;

  @NotNull
  @RequiresEdt
  ChangesViewPanel initChangesPanel() {
    if (myChangesPanel == null) {
      Activity activity = StartUpMeasurer.startActivity("ChangesViewPanel initialization");
      ChangesListView tree = new LocalChangesListView(myProject);
      myChangesPanel = new ChangesViewPanel(tree);
      activity.end();
    }
    return myChangesPanel;
  }

  @RequiresEdt
  private @NotNull ChangesViewToolWindowPanel initToolWindowPanel() {
    if (myToolWindowPanel == null) {
      Activity activity = StartUpMeasurer.startActivity("ChangesViewToolWindowPanel initialization");

      // ChangesViewPanel is used for a singular ChangesViewToolWindowPanel instance. Cleanup is not needed.
      ChangesViewPanel changesViewPanel = initChangesPanel();
      ChangesViewToolWindowPanel panel = new ChangesViewToolWindowPanel(myProject, this, changesViewPanel);
      Disposer.register(this, panel);

      panel.updateCommitWorkflow();

      myToolWindowPanel = panel;
      Disposer.register(panel, () -> {
        // Content is removed from TW
        myChangesPanel = null;
        myToolWindowPanel = null;
      });

      activity.end();
    }
    return myToolWindowPanel;
  }

  public ChangesViewManager(@NotNull Project project) {
    myProject = project;
    ChangesViewModifier.KEY.addChangeListener(project, this::resetViewImmediatelyAndRefreshLater, this);

    MessageBusConnection busConnection = project.getMessageBus().connect(this);
    busConnection.subscribe(ChangesViewWorkflowManager.TOPIC, () -> updateCommitWorkflow());
  }

  @Override
  public @NotNull ChangesViewManager.State getState() {
    return myState;
  }

  public @NotNull Collection<String> getGrouping() {
    return myState.groupingKeys;
  }

  public static class DisplayNameSupplier implements Supplier<String> {
    private final @NotNull Project myProject;

    public DisplayNameSupplier(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public String get() {
      return getLocalChangesToolWindowName(myProject);
    }
  }

  public static @NotNull ChangesViewI getInstance(@NotNull Project project) {
    return project.getService(ChangesViewI.class);
  }

  public static @NotNull ChangesViewEx getInstanceEx(@NotNull Project project) {
    return (ChangesViewEx)getInstance(project);
  }

  public static @NotNull Factory<JComponent> createTextStatusFactory(@NlsContexts.Label String text, final boolean isError) {
    return () -> {
      JLabel label = new JBLabel(StringUtil.replace(text.trim(), "\n", UIUtil.BR)).setCopyable(true);
      label.setVerticalTextPosition(SwingConstants.TOP);
      label.setBorder(JBUI.Borders.empty(3));
      label.setForeground(isError ? JBColor.RED : UIUtil.getLabelForeground());
      return label;
    };
  }

  @Override
  public void dispose() {
  }

  public static class ContentPreloader implements ChangesViewContentProvider.Preloader {
    private final @NotNull Project myProject;

    public ContentPreloader(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void preloadTabContent(@NotNull Content content) {
      content.putUserData(Content.TAB_DND_TARGET_KEY, new MyContentDnDTarget(myProject, content));
    }
  }

  @Override
  public void loadState(@NotNull ChangesViewManager.State state) {
    myState = state;
    migrateShowFlattenSetting();
  }

  private void migrateShowFlattenSetting() {
    if (!myState.myShowFlatten) {
      myState.groupingKeys.clear();
      myState.groupingKeys.addAll(DEFAULT_GROUPING_KEYS);
      myState.myShowFlatten = true;
    }
  }

  static final class ContentPredicate implements Predicate<Project> {
    @Override
    public boolean test(Project project) {
      return ProjectLevelVcsManager.getInstance(project).hasActiveVcss() &&
             !CommitModeManager.getInstance(project).getCurrentCommitMode().hideLocalChangesTab();
    }
  }

  public void setGrouping(@NotNull Collection<String> grouping) {
    myState.groupingKeys.clear();
    myState.groupingKeys.addAll(grouping);
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
    public TreeSet<String> groupingKeys = new TreeSet<>();

    @Attribute("show_ignored")
    public boolean myShowIgnored;
  }

  @Override
  public @NotNull Promise<?> promiseRefresh(@NotNull ModalityState modalityState) {
    if (myToolWindowPanel == null) return cancelledPromise();
    return myToolWindowPanel.scheduleRefreshWithDelay(0, modalityState);
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
  public void updateProgressText(@NlsContexts.Label String text, boolean isError) {
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

  private void updateCommitWorkflow() {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.updateCommitWorkflow();
  }

  @Override
  public void refreshImmediately() {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.scheduleRefreshNow();
  }

  @Override
  public boolean isAllowExcludeFromCommit() {
    if (myToolWindowPanel == null) return false;
    return myToolWindowPanel.isAllowExcludeFromCommit();
  }

  public static boolean isEditorPreview(@NotNull Project project) {
    return EditorTabDiffPreviewManager.getInstance(project).isEditorDiffPreviewAvailable();
  }

  public void closeEditorPreview(boolean onlyIfEmpty) {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.closeEditorPreview(onlyIfEmpty);
  }

  @Override
  public void resetViewImmediatelyAndRefreshLater() {
    if (myToolWindowPanel != null) {
      myToolWindowPanel.resetViewImmediatelyAndRefreshLater();
    }
  }

  public static class ContentProvider implements ChangesViewContentProvider {
    private final @NotNull Project myProject;

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

  private static class LocalChangesListView extends ChangesListView {
    private LocalChangesListView(@NotNull Project project) {
      super(project, false);
      putClientProperty(LOG_COMMIT_SESSION_EVENTS, true);

      setTreeExpander(new MyTreeExpander(this));

      setDoubleClickHandler(e -> {
        if (EditSourceOnDoubleClickHandler.isToggleEvent(this, e)) return false;
        OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(this), true);
        return true;
      });
      setEnterKeyHandler(e -> {
        OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(this), false);
        return true;
      });

      new HoverChangesTree(this) {
        @Override
        public @Nullable HoverIcon getHoverIcon(@NotNull ChangesBrowserNode<?> node) {
          return ChangesViewNodeAction.EP_NAME.computeSafeIfAny(myProject, (it) -> it.createNodeHoverIcon(node));
        }
      }.install();
    }

    @Override
    protected @NotNull ChangesGroupingSupport installGroupingSupport() {
      // can't install support here - 'rebuildTree' is not defined
      return new ChangesGroupingSupport(myProject, this, true);
    }

    private static class MyTreeExpander extends DefaultTreeExpander {
      private MyTreeExpander(@NotNull JTree tree) {
        super(tree);
      }

      @Override
      protected void collapseAll(@NotNull JTree tree, int keepSelectionLevel) {
        super.collapseAll(tree, 2);
        TreeUtil.expand(tree, 1);
      }
    }
  }

  public boolean isDiffPreviewAvailable() {
    if (myToolWindowPanel == null) return false;

    return myToolWindowPanel.mySplitterDiffPreview != null ||
           myToolWindowPanel.myEditorDiffPreview != null && ChangesViewToolWindowPanel.isOpenEditorDiffPreviewWithSingleClick.asBoolean();
  }

  public void diffPreviewChanged(boolean state) {
    if (myToolWindowPanel == null) return;
    DiffPreview preview = ObjectUtils.chooseNotNull(myToolWindowPanel.mySplitterDiffPreview,
                                                    myToolWindowPanel.myEditorDiffPreview);
    DiffPreview.setPreviewVisible(preview, state);
    myToolWindowPanel.setCommitSplitOrientation();
  }


  private static final class MyContentDnDTarget extends VcsToolwindowDnDTarget {
    private MyContentDnDTarget(@NotNull Project project, @NotNull Content content) {
      super(project, content);
    }

    @Override
    public void drop(DnDEvent event) {
      super.drop(event);
      Object attachedObject = event.getAttachedObject();
      if (attachedObject instanceof ShelvedChangeListDragBean) {
        ShelveChangesManager.unshelveSilentlyWithDnd(myProject, (ShelvedChangeListDragBean)attachedObject, null,
                                                     !ChangesTreeDnDSupport.isCopyAction(event));
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

  public static final class ChangesViewToolWindowPanel extends SimpleToolWindowPanel implements Disposable {
    private static final @NotNull RegistryValue isToolbarHorizontalSetting = Registry.get("vcs.local.changes.toolbar.horizontal");
    private static final @NotNull RegistryValue isOpenEditorDiffPreviewWithSingleClick =
      Registry.get("show.diff.preview.as.editor.tab.with.single.click");

    private final @NotNull Project myProject;
    private final @NotNull ChangesViewManager myChangesViewManager;
    private final @NotNull VcsConfiguration myVcsConfiguration;

    private final @NotNull BorderLayoutPanel myMainPanel;
    private final @NotNull BorderLayoutPanel myContentPanel;
    private final @NotNull ChangesViewPanel myChangesPanel;
    private final @NotNull ChangesListView myView;

    private final @NotNull ChangesViewCommitPanelSplitter myCommitPanelSplitter;
    private ChangesViewDiffPreviewProcessor myEditorChangeProcessor;
    private ChangesViewDiffPreviewProcessor mySplitterChangeProcessor;
    private EditorTabPreview myEditorDiffPreview;
    private PreviewDiffSplitterComponent mySplitterDiffPreview;
    private final @NotNull Wrapper myProgressLabel = new Wrapper();

    private @Nullable ChangesViewCommitPanel myCommitPanel;
    private @Nullable ChangesViewCommitWorkflowHandler myCommitWorkflowHandler;

    private final BackgroundRefresher<@Nullable Runnable> myBackgroundRefresher =
      new BackgroundRefresher<>(getClass().getSimpleName() + " refresh", this);

    private boolean myModelUpdateInProgress;

    private boolean myDisposed = false;

    private ChangesViewToolWindowPanel(@NotNull Project project,
                                       @NotNull ChangesViewManager changesViewManager,
                                       @NotNull ChangesViewPanel changesViewPanel) {
      super(false, true);
      myProject = project;
      myChangesViewManager = changesViewManager;
      myChangesPanel = changesViewPanel;

      MessageBusConnection busConnection = myProject.getMessageBus().connect(this);
      myVcsConfiguration = VcsConfiguration.getInstance(myProject);

      // ChangesViewPanel is used for a singular ChangesViewToolWindowPanel instance. Cleanup is not needed.
      myView = myChangesPanel.getChangesView();
      myView.installPopupHandler((DefaultActionGroup)ActionManager.getInstance().getAction("ChangesViewPopupMenu"));
      ChangesTree.installGroupingSupport(myView.getGroupingSupport(),
                                         () -> myChangesViewManager.getGrouping(),
                                         (newValue) -> myChangesViewManager.setGrouping(newValue),
                                         () -> scheduleRefresh());
      ChangesViewDnDSupport.install(myProject, myView, this);

      myChangesPanel.getToolbarActionGroup().addAll(createChangesToolbarActions(myView.getTreeExpander()));
      registerShortcuts(this);

      configureToolbars();
      isToolbarHorizontalSetting.addListener(new RegistryValueListener() {
        @Override
        public void afterValueChanged(@NotNull RegistryValue value) {
          configureToolbars();
        }
      }, this);

      myCommitPanelSplitter = new ChangesViewCommitPanelSplitter(myProject);
      Disposer.register(this, myCommitPanelSplitter);
      myCommitPanelSplitter.setFirstComponent(myChangesPanel);

      myContentPanel = new BorderLayoutPanel() {
        @Override
        public Dimension getMinimumSize() {
          return isMinimumSizeSet() || myChangesPanel.isToolbarHorizontal()
                 ? super.getMinimumSize()
                 : myChangesPanel.getToolbar().getComponent().getPreferredSize();
        }
      };
      myContentPanel.addToCenter(myCommitPanelSplitter);
      myMainPanel = simplePanel(myContentPanel)
        .addToBottom(myProgressLabel);

      setDiffPreview();

      EditorTabDiffPreviewManager.getInstance(project).subscribeToPreviewVisibilityChange(this, this::setDiffPreview);
      isOpenEditorDiffPreviewWithSingleClick.addListener(new RegistryValueListener() {
        @Override
        public void afterValueChanged(@NotNull RegistryValue value) {
          if (myEditorDiffPreview != null) setDiffPreview();
        }
      }, this);

      setContent(myMainPanel);

      busConnection.subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
        @Override
        public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
          setCommitSplitOrientation();
        }
      });

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
      busConnection.subscribe(ChangeListListener.TOPIC, new MyChangeListListener());
      busConnection.subscribe(LineStatusTrackerSettingListener.TOPIC, () -> {
        if (myEditorChangeProcessor != null) myEditorChangeProcessor.fireDiffSettingsChanged();
        if (mySplitterChangeProcessor != null) mySplitterChangeProcessor.fireDiffSettingsChanged();
      });

      busConnection.subscribe(ChangesViewModifier.TOPIC, () -> scheduleRefresh());
      busConnection.subscribe(VcsManagedFilesHolder.TOPIC, () -> {
        ApplicationManager.getApplication().invokeLater(() -> {
          myView.repaint();
        });
      });

      scheduleRefresh();
      updatePreview(false);
    }

    @Override
    public void dispose() {
      myDisposed = true;
    }

    private void setDiffPreview() {
      if (myDisposed) return;

      boolean isEditorPreview = isEditorPreview(myProject);
      boolean hasSplitterPreview = !isCommitToolWindowShown(myProject);

      //noinspection DoubleNegation
      boolean needUpdatePreviews = isEditorPreview != (myEditorChangeProcessor != null) ||
                                   hasSplitterPreview != (mySplitterChangeProcessor != null);
      if (!needUpdatePreviews) return;

      if (myEditorChangeProcessor != null) Disposer.dispose(myEditorChangeProcessor);
      if (mySplitterChangeProcessor != null) Disposer.dispose(mySplitterChangeProcessor);

      if (isEditorPreview) {
        myEditorChangeProcessor = new ChangesViewDiffPreviewProcessor(myView, true);
        Disposer.register(this, myEditorChangeProcessor);
        myEditorDiffPreview = installEditorPreview(myEditorChangeProcessor, hasSplitterPreview);
      }
      else {
        myEditorChangeProcessor = null;
        myEditorDiffPreview = null;
      }

      if (hasSplitterPreview) {
        mySplitterChangeProcessor = new ChangesViewDiffPreviewProcessor(myView, false);
        Disposer.register(this, mySplitterChangeProcessor);
        mySplitterDiffPreview = installSplitterPreview(mySplitterChangeProcessor);
      }
      else {
        mySplitterChangeProcessor = null;
        mySplitterDiffPreview = null;
      }

      configureDiffPreview();
    }

    private @NotNull EditorTabPreview installEditorPreview(@NotNull ChangesViewDiffPreviewProcessor changeProcessor,
                                                           boolean hasSplitterPreview) {
      return new SimpleTreeEditorDiffPreview(changeProcessor, myView, myContentPanel,
                                             isOpenEditorDiffPreviewWithSingleClick.asBoolean() && !hasSplitterPreview) {
        @Override
        public void returnFocusToTree() {
          ToolWindow toolWindow = getToolWindowFor(myProject, LOCAL_CHANGES);
          if (toolWindow != null) toolWindow.activate(null);
        }

        @Override
        public void updateDiffAction(@NotNull AnActionEvent e) {
          ShowDiffFromLocalChangesActionProvider.updateAvailability(e);
        }

        @Override
        protected String getCurrentName() {
          String changeName = changeProcessor.getCurrentChangeName();
          return changeName != null
                 ? VcsBundle.message("commit.editor.diff.preview.title", changeName)
                 : VcsBundle.message("commit.editor.diff.preview.empty.title");
        }

        @Override
        protected boolean skipPreviewUpdate() {
          if (super.skipPreviewUpdate()) return true;
          if (!myView.equals(IdeFocusManager.getInstance(myProject).getFocusOwner())) return true;
          if (!isPreviewOpen() && !isEditorPreviewAllowed()) return true;

          return myModelUpdateInProgress;
        }

        @Override
        protected boolean isPreviewOnDoubleClickAllowed() {
          return isCommitToolWindowShown(myProject) ? VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK :
                 VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK;
        }

        @Override
        protected boolean isPreviewOnEnterAllowed() {
          return isCommitToolWindowShown(myProject) ? VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK :
                 VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK;
        }
      };
    }

    private @NotNull PreviewDiffSplitterComponent installSplitterPreview(@NotNull ChangesViewDiffPreviewProcessor changeProcessor) {
      PreviewDiffSplitterComponent previewSplitter =
        new PreviewDiffSplitterComponent(changeProcessor, CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION);
      previewSplitter.setFirstComponent(myContentPanel);
      DiffPreview.setPreviewVisible(previewSplitter, myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN);

      myView.addSelectionListener(() -> {
        boolean fromModelRefresh = myModelUpdateInProgress;
        invokeLater(() -> previewSplitter.updatePreview(fromModelRefresh));
      }, changeProcessor);

      myMainPanel.addToCenter(previewSplitter);
      Disposer.register(changeProcessor, () -> {
        if (Disposer.isDisposed(this)) return;
        myMainPanel.remove(previewSplitter);
        myMainPanel.addToCenter(myContentPanel);

        myMainPanel.revalidate();
        myMainPanel.repaint();
      });

      return previewSplitter;
    }

    private boolean isEditorPreviewAllowed() {
      return !isOpenEditorDiffPreviewWithSingleClick.asBoolean() || myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN;
    }

    private void closeEditorPreview(boolean onlyIfEmpty) {
      if (myEditorDiffPreview == null) return;

      EditorTabPreview editorPreview = myEditorDiffPreview;

      if (onlyIfEmpty && editorPreview.hasContent()) return;
      editorPreview.closePreview();
    }

    private void updatePreview(boolean fromModelRefresh) {
      if (myEditorDiffPreview != null) {
        myEditorDiffPreview.updatePreview(fromModelRefresh);
      }
      if (mySplitterDiffPreview != null) {
        mySplitterDiffPreview.updatePreview(fromModelRefresh);
      }
    }

    public void updateCommitWorkflow() {
      if (myDisposed) return;

      ChangesViewCommitWorkflowHandler newWorkflowHandler = ChangesViewWorkflowManager.getInstance(myProject).getCommitWorkflowHandler();
      if (myCommitWorkflowHandler == newWorkflowHandler) return;

      if (newWorkflowHandler != null) {
        newWorkflowHandler.addActivityListener(() -> configureDiffPreview());

        ChangesViewCommitPanel newCommitPanel = (ChangesViewCommitPanel)newWorkflowHandler.getUi();
        newCommitPanel.registerRootComponent(this);
        myCommitPanelSplitter.setSecondComponent(newCommitPanel);

        myCommitWorkflowHandler = newWorkflowHandler;
        myCommitPanel = newCommitPanel;
      }
      else {
        myCommitPanelSplitter.setSecondComponent(null);

        myCommitWorkflowHandler = null;
        myCommitPanel = null;
      }

      configureDiffPreview();
      configureToolbars();
    }

    public boolean isAllowExcludeFromCommit() {
      return myCommitWorkflowHandler != null && myCommitWorkflowHandler.isActive();
    }

    private void configureDiffPreview() {
      if (myEditorChangeProcessor != null) {
        myEditorChangeProcessor.setAllowExcludeFromCommit(isAllowExcludeFromCommit());
      }
      if (mySplitterChangeProcessor != null) {
        mySplitterChangeProcessor.setAllowExcludeFromCommit(isAllowExcludeFromCommit());
      }
    }

    private void configureToolbars() {
      boolean isToolbarHorizontal = CommitModeManager.getInstance(myProject).getCurrentCommitMode().useCommitToolWindow() &&
                                    isToolbarHorizontalSetting.asBoolean();
      myChangesPanel.setToolbarHorizontal(isToolbarHorizontal);
      if (myCommitPanel != null) myCommitPanel.setToolbarHorizontal(isToolbarHorizontal);
    }

    private void setCommitSplitOrientation() {
      boolean hasPreviewPanel = myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN && mySplitterDiffPreview != null;
      ToolWindow tw = getToolWindowFor(myProject, LOCAL_CHANGES);
      if (tw != null) {
        boolean toolwindowIsHorizontal = tw.getAnchor().isHorizontal();
        myCommitPanelSplitter.setOrientation(hasPreviewPanel || !toolwindowIsHorizontal);
      }
    }

    private @NotNull Function<ChangeNodeDecorator, ChangeNodeDecorator> getChangeDecoratorProvider() {
      return baseDecorator -> new PartialCommitChangeNodeDecorator(myProject, baseDecorator, () -> isAllowExcludeFromCommit());
    }

    @Override
    public @NotNull List<AnAction> getActions(boolean originalProvider) {
      return asList(myChangesPanel.getToolbarActionGroup().getChildren(null));
    }

    @Override
    public @Nullable Object getData(@NotNull String dataId) {
      Object data = super.getData(dataId);
      if (data != null) return data;
      if (EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW.is(dataId)) {
        return myEditorDiffPreview;
      }
      // This makes COMMIT_WORKFLOW_HANDLER available anywhere in "Local Changes" - so commit executor actions are enabled.
      return myCommitPanel != null ? myCommitPanel.getDataFromProviders(dataId) : null;
    }

    private static void registerShortcuts(@NotNull JComponent component) {
      ActionUtil.wrap("ChangesView.Refresh").registerCustomShortcutSet(CommonShortcuts.getRerun(), component);
      ActionUtil.wrap("ChangesView.NewChangeList").registerCustomShortcutSet(CommonShortcuts.getNew(), component);
      ActionUtil.wrap("ChangesView.RemoveChangeList").registerCustomShortcutSet(CommonShortcuts.getDelete(), component);
      ActionUtil.wrap(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST).registerCustomShortcutSet(CommonShortcuts.getMove(), component);
    }

    private @NotNull List<AnAction> createChangesToolbarActions(@NotNull TreeExpander treeExpander) {
      List<AnAction> actions = new ArrayList<>();
      actions.add(CustomActionsSchema.getInstance().getCorrectedAction(ActionPlaces.CHANGES_VIEW_TOOLBAR));

      if (!ExperimentalUI.isNewUI()) {
        actions.add(Separator.getInstance());
      }

      DefaultActionGroup viewOptionsGroup =
        DefaultActionGroup.createPopupGroup(() -> VcsBundle.message("action.ChangesViewToolWindowPanel.text"));

      viewOptionsGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Show);
      viewOptionsGroup.add(ActionManager.getInstance().getAction(GROUP_BY_ACTION_GROUP));
      viewOptionsGroup.add(new Separator(VcsBundle.message("action.vcs.log.show.separator")));
      viewOptionsGroup.add(new ToggleShowIgnoredAction());
      viewOptionsGroup.add(ActionManager.getInstance().getAction("ChangesView.ViewOptions"));

      actions.add(viewOptionsGroup);
      actions.add(CommonActionsManager.getInstance().createExpandAllHeaderAction(treeExpander, myView));
      actions.add(CommonActionsManager.getInstance().createCollapseAllAction(treeExpander, myView));
      actions.add(Separator.getInstance());
      actions.add(ActionManager.getInstance().getAction("ChangesView.SingleClickPreview"));

      return actions;
    }

    private void updateProgressComponent(@NotNull List<Supplier<@Nullable JComponent>> progress) {
      invokeLaterIfNeeded(() -> {
        if (myDisposed) return;
        List<? extends @Nullable JComponent> components = ContainerUtil.mapNotNull(progress, it -> it.get());
        if (!components.isEmpty()) {
          JComponent component = DiffUtil.createStackedComponents(components, DiffUtil.TITLE_GAP);
          myProgressLabel.setContent(new FixedSizeScrollPanel(component, new JBDimension(400, 100)));
        }
        else {
          myProgressLabel.setContent(null);
        }
      });
    }

    public void updateProgressText(@NlsContexts.Label String text, boolean isError) {
      updateProgressComponent(Collections.singletonList(createTextStatusFactory(text, isError)));
    }

    public void setBusy(final boolean b) {
      invokeLaterIfNeeded(() -> myView.setPaintBusy(b));
    }

    public void scheduleRefresh() {
      scheduleRefreshWithDelay(100, ModalityState.nonModal());
    }

    private void scheduleRefreshNow() {
      scheduleRefreshWithDelay(0, ModalityState.nonModal());
    }

    @CalledInAny
    private @NotNull Promise<?> scheduleRefreshWithDelay(int delayMillis, @NotNull ModalityState modalityState) {
      setBusy(true);
      return myBackgroundRefresher.requestRefresh(delayMillis, this::refreshView)
        .thenAsync(callback -> callback != null
                               ? AppUIExecutor.onUiThread(modalityState).submit(callback)
                               : Promises.rejectedPromise(Promises.createError("ChangesViewManager is not available", false)))
        .onProcessed(__ -> setBusy(false));
    }

    @RequiresBackgroundThread
    private @Nullable Runnable refreshView() {
      if (myDisposed || !myProject.isInitialized() || ApplicationManager.getApplication().isUnitTestMode()) return null;

      Span span = TRACER.spanBuilder(ChangesView.ChangesViewRefreshBackground.getName()).startSpan();
      try {
        ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
        List<LocalChangeList> changeLists = changeListManager.getChangeLists();
        List<FilePath> unversionedFiles = changeListManager.getUnversionedFilesPaths();

        boolean shouldShowUntrackedLoading = unversionedFiles.isEmpty() &&
                                             !myProject.getService(InitialVfsRefreshService.class).isInitialVfsRefreshFinished() &&
                                             changeListManager.isUnversionedInUpdateMode();

        boolean skipSingleDefaultChangeList = Registry.is("vcs.skip.single.default.changelist") ||
                                              !changeListManager.areChangeListsEnabled();
        TreeModelBuilder treeModelBuilder = new TreeModelBuilder(myProject, myView.getGrouping())
          .setChangeLists(changeLists, skipSingleDefaultChangeList, getChangeDecoratorProvider())
          .setLocallyDeletedPaths(changeListManager.getDeletedFiles())
          .setModifiedWithoutEditing(changeListManager.getModifiedWithoutEditing())
          .setSwitchedFiles(changeListManager.getSwitchedFilesMap())
          .setSwitchedRoots(changeListManager.getSwitchedRoots())
          .setLockedFolders(changeListManager.getLockedFolders())
          .setLogicallyLockedFiles(changeListManager.getLogicallyLockedFolders())
          .setUnversioned(unversionedFiles);
        if (myChangesViewManager.myState.myShowIgnored) {
          List<FilePath> ignoredFilePaths = changeListManager.getIgnoredFilePaths();
          treeModelBuilder.setIgnored(ignoredFilePaths);
        }
        if (shouldShowUntrackedLoading) {
          treeModelBuilder.insertSubtreeRoot(new ChangesBrowserUnversionedLoadingPendingNode());
        }

        for (ChangesViewModifier extension : ChangesViewModifier.KEY.getExtensions(myProject)) {
          try {
            extension.modifyTreeModelBuilder(treeModelBuilder);
          }
          catch (ProcessCanceledException e) {
            throw e;
          }
          catch (Throwable t) {
            Logger.getInstance(ChangesViewToolWindowPanel.class).error(t);
          }
        }

        DefaultTreeModel treeModel = treeModelBuilder.build(true);

        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        indicator.checkCanceled();

        boolean[] wasCalled = new boolean[1]; // ensure multiple merged refresh requests are applied once
        return () -> {
          if (wasCalled[0]) return;
          wasCalled[0] = true;
          refreshViewOnEdt(treeModel, changeLists, unversionedFiles, indicator.isCanceled());
        };
      }
      finally {
        span.end();
      }
    }

    /**
     * Immediately reset changes view and request refresh when NON_MODAL modality allows (i.e. after a plugin was unloaded or a dialog closed)
     */
    @RequiresEdt
    private void resetViewImmediatelyAndRefreshLater() {
      myView.setModel(new DefaultTreeModel(ChangesBrowserNode.createRoot()));
      myView.setPaintBusy(true);
      ApplicationManager.getApplication().invokeLater(() -> {
        scheduleRefreshNow();
      }, ModalityState.nonModal());
    }

    @RequiresEdt
    private void refreshViewOnEdt(@NotNull DefaultTreeModel treeModel,
                                  @NotNull List<? extends LocalChangeList> changeLists,
                                  @NotNull List<? extends FilePath> unversionedFiles,
                                  boolean hasPendingRefresh) {
      if (myDisposed) return;

      Span span = TRACER.spanBuilder(ChangesView.ChangesViewRefreshEdt.getName()).startSpan();
      try {
        myModelUpdateInProgress = true;
        try {
          updateModel(myView, treeModel);
        }
        finally {
          myModelUpdateInProgress = false;
        }

        if (myCommitWorkflowHandler != null && !hasPendingRefresh) {
          myCommitWorkflowHandler.synchronizeInclusion(changeLists, unversionedFiles);
        }

        updatePreview(true);
      }
      finally {
        span.end();
      }
    }

    private static void updateModel(@NotNull ChangesListView view,
                                    @NotNull DefaultTreeModel newModel) {
      TreeState state = TreeState.createOn(view, view.getRoot());
      state.setScrollToSelection(false);
      ChangesBrowserNode<?> oldRoot = view.getRoot();
      view.setModel(newModel);
      ChangesBrowserNode<?> newRoot = view.getRoot();
      state.applyTo(view, newRoot);

      initTreeStateIfNeeded(view, oldRoot, newRoot);
    }

    private static void initTreeStateIfNeeded(@NotNull ChangesListView view,
                                              @NotNull ChangesBrowserNode<?> oldRoot,
                                              @NotNull ChangesBrowserNode<?> newRoot) {
      ChangesBrowserNode<?> defaultListNode = getDefaultChangelistNode(newRoot);
      if (defaultListNode == null) return;

      if (view.getSelectionCount() == 0) {
        TreeUtil.selectNode(view, defaultListNode);
      }

      if (oldRoot.getFileCount() == 0 && TreeUtil.collectExpandedPaths(view).isEmpty()) {
        view.expandSafe(defaultListNode);
      }
    }

    @Nullable
    private static ChangesBrowserNode<?> getDefaultChangelistNode(@NotNull ChangesBrowserNode<?> root) {
      return root.iterateNodeChildren()
        .filter(ChangesBrowserChangeListNode.class)
        .find(node -> {
          ChangeList list = node.getUserObject();
          return list instanceof LocalChangeList && ((LocalChangeList)list).isDefault();
        });
    }

    public void setGrouping(@NotNull String groupingKey) {
      myView.getGroupingSupport().setGroupingKeysOrSkip(Set.of(groupingKey));
      scheduleRefreshNow();
    }

    public void selectFile(@Nullable VirtualFile vFile) {
      if (vFile == null) return;

      ChangesBrowserNode<?> node = findNodeForFile(vFile);
      if (node == null) return;

      TreeUtil.selectNode(myView, node);
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
      ChangesBrowserNode<?> node = findNodeForFile(file);
      if (node == null) return;

      myView.getModel().nodeChanged(node);
    }

    private @Nullable ChangesBrowserNode<?> findNodeForFile(@NotNull VirtualFile file) {
      FilePath filePath = VcsUtil.getFilePath(file);
      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
      return (ChangesBrowserNode<?>)TreeUtil.findNode(root, node -> {
        FilePath nodeFilePath = VcsTreeModelData.mapUserObjectToFilePath(node.getUserObject());
        return Objects.equals(filePath, nodeFilePath);
      });
    }

    private void invokeLater(Runnable runnable) {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.nonModal(), myProject.getDisposed());
    }

    private void invokeLaterIfNeeded(Runnable runnable) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), myProject.getDisposed(), runnable);
    }

    private class MyChangeListListener extends ChangeListAdapter {
      @Override
      public void changeListsChanged() {
        scheduleRefresh();
      }

      @Override
      public void unchangedFileStatusChanged() {
        scheduleRefresh();
      }

      @Override
      public void changedFileStatusChanged() {
        setBusy(false);
        scheduleRefresh();

        ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
        updateProgressComponent(changeListManager.getAdditionalUpdateInfo());
      }
    }

    private class ToggleShowIgnoredAction extends ToggleAction implements DumbAware {
      ToggleShowIgnoredAction() {
        super(VcsBundle.messagePointer("changes.action.show.ignored.text"),
              VcsBundle.messagePointer("changes.action.show.ignored.description"), AllIcons.Actions.ToggleVisibility);
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return myChangesViewManager.myState.myShowIgnored;
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        myChangesViewManager.myState.myShowIgnored = state;
        scheduleRefreshNow();
      }
    }
  }

  public static @NotNull @Nls String getLocalChangesToolWindowName(@NotNull Project project) {
    return isCommitToolWindowShown(project) ? VcsBundle.message("tab.title.commit") : VcsBundle.message("local.changes.tab");
  }

  @Override
  public @Nullable ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    return ChangesViewWorkflowManager.getInstance(myProject).getCommitWorkflowHandler();
  }
}
