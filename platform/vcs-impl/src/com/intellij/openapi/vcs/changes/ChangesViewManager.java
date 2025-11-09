// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.diff.impl.DiffEditorViewer;
import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.diff.util.DiffUtil;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsApplicationSettings;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffFromLocalChangesActionProvider;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewDataKeys;
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings;
import com.intellij.platform.vcs.impl.shared.changes.PreviewDiffSplitterComponent;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.content.Content;
import com.intellij.util.*;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.vcs.changes.viewModel.ChangesViewProxy;
import com.intellij.vcs.commit.*;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventListener;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.*;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerKt.isCommitToolWindowShown;
import static com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerKt.subscribeOnVcsToolWindowLayoutChanges;
import static com.intellij.util.ui.JBUI.Panels.simplePanel;

public class ChangesViewManager implements ChangesViewEx, Disposable {
  private static final String CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION = "ChangesViewManager.DETAILS_SPLITTER_PROPORTION";

  private final @NotNull CoroutineScope myScope;
  private final @NotNull Project myProject;

  private @Nullable ChangesViewProxy myChangesView;
  private @Nullable ChangesViewToolWindowPanel myToolWindowPanel;

  @NotNull
  @RequiresEdt
  ChangesViewProxy initChangesView() {
    if (myChangesView == null) {
      Activity activity = StartUpMeasurer.startActivity("ChangesViewPanel initialization");
      myChangesView = ChangesViewProxy.create(myProject, myScope);
      activity.end();
    }
    return myChangesView;
  }

  @RequiresEdt
  private @NotNull ChangesViewToolWindowPanel initToolWindowPanel() {
    if (myToolWindowPanel == null) {
      Activity activity = StartUpMeasurer.startActivity("ChangesViewToolWindowPanel initialization");

      ChangesViewProxy changesView = initChangesView();
      ChangesViewToolWindowPanel panel = new ChangesViewToolWindowPanel(myProject, changesView);
      Disposer.register(this, panel);

      panel.updateCommitWorkflow();

      myToolWindowPanel = panel;
      Disposer.register(panel, () -> {
        // Content is removed from TW
        myChangesView = null;
        myToolWindowPanel = null;
      });
      Disposer.register(panel, changesView);

      activity.end();
    }
    return myToolWindowPanel;
  }

  public ChangesViewManager(@NotNull Project project, @NotNull CoroutineScope coroutineScope) {
    myScope = coroutineScope;
    myProject = project;

    MessageBusConnection busConnection = project.getMessageBus().connect(this);
    busConnection.subscribe(ChangesViewWorkflowManager.TOPIC, () -> updateCommitWorkflow());
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

  static class ContentPreloader implements ChangesViewContentProvider.Preloader {
    private final @NotNull Project myProject;

    ContentPreloader(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void preloadTabContent(@NotNull Content content) {
      new ChangesViewCommitTabTitleUpdater(myProject, LOCAL_CHANGES).init(content);

      content.putUserData(Content.TAB_DND_TARGET_KEY, new MyContentDnDTarget(myProject, content));
    }
  }

  static final class ContentPredicate implements Predicate<Project> {
    @Override
    public boolean test(Project project) {
      return ProjectLevelVcsManager.getInstance(project).hasActiveVcss() &&
             !CommitModeManager.getInstance(project).getCurrentCommitMode().hideLocalChangesTab();
    }
  }

  @Override
  public void scheduleRefresh(@NotNull Runnable callback) {
    if (myChangesView == null) return;
    myChangesView.scheduleRefreshNow(callback);
  }

  @Override
  public void scheduleRefresh() {
    if (myChangesView == null) return;
    myChangesView.scheduleDelayedRefresh();
  }

  @Override
  public void selectFile(VirtualFile vFile) {
    if (myChangesView == null) return;
    myChangesView.selectFile(vFile);
  }

  @Override
  public void selectChanges(@NotNull List<? extends Change> changes) {
    if (myChangesView == null) return;
    myChangesView.selectChanges(new ArrayList<>(changes));
  }

  @Override
  public void updateProgressComponent(@NotNull List<Supplier<JComponent>> progress) {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.updateProgressComponent(progress);
  }

  @Override
  public void setGrouping(@NotNull String groupingKey) {
    if (myChangesView == null) return;
    myChangesView.setGrouping(groupingKey);
  }

  private void updateCommitWorkflow() {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.updateCommitWorkflow();
  }

  @Override
  public boolean isAllowExcludeFromCommit() {
    if (myToolWindowPanel == null) return false;
    return myToolWindowPanel.isAllowExcludeFromCommit();
  }

  public void closeEditorPreview(boolean onlyIfEmpty) {
    if (myToolWindowPanel == null) return;
    myToolWindowPanel.closeEditorPreview(onlyIfEmpty);
  }

  @Override
  public void resetViewImmediatelyAndRefreshLater() {
    if (myChangesView != null) {
      myChangesView.resetViewImmediatelyAndRefreshLater();
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
      content.setPreferredFocusableComponent(panel.myChangesView.getPreferredFocusedComponent());
    }
  }

  public boolean isDiffPreviewAvailable() {
    if (myToolWindowPanel == null) return false;

    return myToolWindowPanel.mySplitterDiffPreview != null ||
           ChangesViewToolWindowPanel.isOpenEditorDiffPreviewWithSingleClick.asBoolean();
  }

  public void diffPreviewChanged(boolean state) {
    if (myToolWindowPanel == null) return;
    DiffPreview preview = ObjectUtils.chooseNotNull(myToolWindowPanel.mySplitterDiffPreview,
                                                    myToolWindowPanel.myEditorDiffPreview);
    DiffPreview.setPreviewVisible(preview, state);
    myToolWindowPanel.updatePanelLayout();
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

  private static final class ChangesViewToolWindowPanel extends SimpleToolWindowPanel implements Disposable {
    private static final @NotNull RegistryValue isOpenEditorDiffPreviewWithSingleClick =
      Registry.get("show.diff.preview.as.editor.tab.with.single.click");

    private final @NotNull Project myProject;
    private final @NotNull ChangesViewSettings myChangesViewSettings;
    private final @NotNull VcsConfiguration myVcsConfiguration;

    private final @NotNull Wrapper myMainPanelContent;
    private final @NotNull BorderLayoutPanel myContentPanel;

    private final @NotNull ChangesViewProxy myChangesView;

    private final @NotNull ChangesViewCommitPanelSplitter myCommitPanelSplitter;
    private final @NotNull ChangesViewEditorDiffPreview myEditorDiffPreview;
    private @Nullable ChangesViewSplitterDiffPreview mySplitterDiffPreview;

    private final @NotNull Wrapper myProgressLabel = new Wrapper();

    private @Nullable ChangesViewCommitPanel myCommitPanel;
    private @Nullable ChangesViewCommitWorkflowHandler myCommitWorkflowHandler;

    private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

    private boolean myDisposed = false;

    private ChangesViewToolWindowPanel(@NotNull Project project,
                                       @NotNull ChangesViewProxy changesView) {
      super(false, true);
      myProject = project;
      myChangesViewSettings = ChangesViewSettings.getInstance(project);
      myChangesView = changesView;
      myChangesView.initPanel();

      MessageBusConnection busConnection = myProject.getMessageBus().connect(this);
      myVcsConfiguration = VcsConfiguration.getInstance(myProject);

      registerShortcuts(this);

      ApplicationManager.getApplication().getMessageBus().connect(project)
        .subscribe(AdvancedSettingsChangeListener.TOPIC, new AdvancedSettingsChangeListener() {
          @Override
          public void advancedSettingChanged(@NotNull String id, @NotNull Object oldValue, @NotNull Object newValue) {
            if (CommitMode.NonModalCommitMode.COMMIT_TOOL_WINDOW_SETTINGS_KEY.equals(id) && oldValue != newValue) {
              configureToolbars();
            }
          }
        });
      configureToolbars();

      myCommitPanelSplitter = new ChangesViewCommitPanelSplitter(myProject);
      Disposer.register(this, myCommitPanelSplitter);
      myCommitPanelSplitter.setFirstComponent(myChangesView.getPanel());

      myContentPanel = new BorderLayoutPanel();
      myContentPanel.addToCenter(myCommitPanelSplitter);
      myMainPanelContent = new Wrapper(myContentPanel);
      JPanel mainPanel = simplePanel(myMainPanelContent)
        .addToBottom(myProgressLabel);

      myEditorDiffPreview = new ChangesViewEditorDiffPreview();
      Disposer.register(this, myEditorDiffPreview);

      // Override the handlers registered by editorDiffPreview
      myChangesView.getTree().setDoubleClickHandler(e -> {
        if (EditSourceOnDoubleClickHandler.isToggleEvent(myChangesView.getTree(), e)) return false;
        if (performHoverAction()) return true;
        if (myEditorDiffPreview.handleDoubleClick(e)) return true;
        OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(myChangesView.getTree()), true);
        return true;
      });
      myChangesView.getTree().setEnterKeyHandler(e -> {
        if (performHoverAction()) return true;
        if (myEditorDiffPreview.handleEnterKey()) return true;
        OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(myChangesView.getTree()), false);
        return true;
      });

      setContent(mainPanel);

      subscribeOnVcsToolWindowLayoutChanges(busConnection, this::updatePanelLayout);
      updatePanelLayout();
    }

    private boolean performHoverAction() {
      ChangesBrowserNode<?> selected = VcsTreeModelData.selected(myChangesView.getTree()).iterateNodes().single();
      if (selected == null) return false;

      for (ChangesViewNodeAction extension : ChangesViewNodeAction.EP_NAME.getExtensions(myProject)) {
        if (extension.handleDoubleClick(selected)) return true;
      }
      return false;
    }

    @Override
    public void dispose() {
      myDisposed = true;

      if (mySplitterDiffPreview != null) Disposer.dispose(mySplitterDiffPreview);
      mySplitterDiffPreview = null;
    }

    private void updatePanelLayout() {
      if (myDisposed) return;

      boolean isVertical = isToolWindowTabVertical(myProject, LOCAL_CHANGES);
      boolean hasSplitterPreview = shouldHaveSplitterDiffPreview(myProject, isVertical);
      boolean isPreviewPanelShown = hasSplitterPreview && myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN;
      myCommitPanelSplitter.setOrientation(isPreviewPanelShown || isVertical);

      //noinspection DoubleNegation
      boolean needUpdatePreviews = hasSplitterPreview != (mySplitterDiffPreview != null);
      if (!needUpdatePreviews) return;

      if (hasSplitterPreview) {
        mySplitterDiffPreview = new ChangesViewSplitterDiffPreview();
        DiffPreview.setPreviewVisible(mySplitterDiffPreview, myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN);
      }
      else {
        Disposer.dispose(mySplitterDiffPreview);
        mySplitterDiffPreview = null;
      }
    }

    private class ChangesViewEditorDiffPreview extends TreeHandlerEditorDiffPreview {
      private ChangesViewEditorDiffPreview() {
        super(myChangesView.getTree(), myContentPanel, ChangesViewDiffPreviewHandler.INSTANCE);
      }

      @Override
      protected @NotNull DiffEditorViewer createViewer() {
        return createDiffPreviewProcessor(true);
      }

      @Override
      public void returnFocusToTree() {
        ToolWindow toolWindow = getToolWindowFor(myProject, LOCAL_CHANGES);
        if (toolWindow != null) toolWindow.activate(null);
      }

      @Override
      public boolean openPreview(boolean requestFocus) {
        return CommitToolWindowUtil.openDiff(LOCAL_CHANGES, this, requestFocus);
      }

      @Override
      public void updateDiffAction(@NotNull AnActionEvent e) {
        ShowDiffFromLocalChangesActionProvider.updateAvailability(e);
      }

      @Override
      public @Nullable String getEditorTabName(@Nullable ChangeViewDiffRequestProcessor.Wrapper wrapper) {
        return wrapper != null
               ? VcsBundle.message("commit.editor.diff.preview.title", wrapper.getPresentableName())
               : VcsBundle.message("commit.editor.diff.preview.empty.title");
      }

      @Override
      protected boolean isOpenPreviewWithSingleClickEnabled() {
        return isOpenEditorDiffPreviewWithSingleClick.asBoolean();
      }

      @Override
      protected boolean isOpenPreviewWithSingleClick() {
        if (myChangesView.isModelUpdateInProgress()) return false;
        if (mySplitterDiffPreview != null && myVcsConfiguration.LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN) return false;
        return super.isOpenPreviewWithSingleClick();
      }

      @Override
      protected boolean isPreviewOnDoubleClick() {
        return isCommitToolWindowShown(myProject) ?
               VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK :
               VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK;
      }

      @Override
      protected boolean isPreviewOnEnter() {
        return isCommitToolWindowShown(myProject) ?
               VcsApplicationSettings.getInstance().SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK :
               VcsApplicationSettings.getInstance().SHOW_DIFF_ON_DOUBLE_CLICK;
      }
    }

    private class ChangesViewSplitterDiffPreview implements DiffPreview, Disposable {
      private final ChangesViewDiffPreviewProcessor myProcessor;
      private final PreviewDiffSplitterComponent mySplitterComponent;

      private ChangesViewSplitterDiffPreview() {
        myProcessor = createDiffPreviewProcessor(false);
        mySplitterComponent = new PreviewDiffSplitterComponent(myProcessor, CHANGES_VIEW_PREVIEW_SPLITTER_PROPORTION);

        mySplitterComponent.setFirstComponent(myContentPanel);
        myMainPanelContent.setContent(mySplitterComponent);
      }

      @Override
      public void dispose() {
        Disposer.dispose(myProcessor);

        if (!ChangesViewToolWindowPanel.this.myDisposed) {
          myMainPanelContent.setContent(myContentPanel);
        }
      }

      @Override
      public boolean openPreview(boolean requestFocus) {
        return mySplitterComponent.openPreview(requestFocus);
      }

      @Override
      public void closePreview() {
        mySplitterComponent.closePreview();
      }
    }

    private ChangesViewDiffPreviewProcessor createDiffPreviewProcessor(boolean isInEditor) {
      ChangesViewDiffPreviewProcessor processor = new ChangesViewDiffPreviewProcessor(myChangesView.getTree(), isInEditor);
      this.addListener(new Listener() {
        @Override
        public void allowExcludeFromCommitChanged() {
          processor.setAllowExcludeFromCommit(ChangesViewToolWindowPanel.this.isAllowExcludeFromCommit());
        }
      }, processor);
      processor.setAllowExcludeFromCommit(this.isAllowExcludeFromCommit());
      return processor;
    }

    private void closeEditorPreview(boolean onlyIfEmpty) {
      if (onlyIfEmpty && myEditorDiffPreview.hasContent()) return;
      myEditorDiffPreview.closePreview();
    }

    public void updateCommitWorkflow() {
      if (myDisposed) return;

      ChangesViewCommitWorkflowHandler newWorkflowHandler = ChangesViewWorkflowManager.getInstance(myProject).getCommitWorkflowHandler();
      if (myCommitWorkflowHandler == newWorkflowHandler) return;

      if (newWorkflowHandler != null) {
        newWorkflowHandler.addActivityListener(() -> myDispatcher.getMulticaster().allowExcludeFromCommitChanged());

        ChangesViewCommitPanel newCommitPanel = (ChangesViewCommitPanel)newWorkflowHandler.getUi();
        newCommitPanel.registerRootComponent(this);
        myCommitPanelSplitter.setSecondComponent(newCommitPanel.getComponent());

        myCommitWorkflowHandler = newWorkflowHandler;
        myCommitPanel = newCommitPanel;
      }
      else {
        myCommitPanelSplitter.setSecondComponent(null);

        myCommitWorkflowHandler = null;
        myCommitPanel = null;
      }

      myDispatcher.getMulticaster().allowExcludeFromCommitChanged();
      configureToolbars();
    }

    public boolean isAllowExcludeFromCommit() {
      return ChangesViewManager.isAllowExcludeFromCommit(myCommitWorkflowHandler);
    }

    public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
      myDispatcher.addListener(listener, disposable);
    }

    private void configureToolbars() {
      boolean isToolbarHorizontal = CommitModeManager.getInstance(myProject).getCurrentCommitMode().useCommitToolWindow();
      myChangesView.setToolbarHorizontal(isToolbarHorizontal);
    }

    @Override
    public @NotNull List<AnAction> getActions(boolean originalProvider) {
      ActionManager actionManager = ActionManager.getInstance();
      DefaultActionGroup toolbarActionGroup = (DefaultActionGroup)actionManager.getAction("ChangesViewToolbar.Shared");
      return Arrays.asList(toolbarActionGroup.getChildren(actionManager));
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      super.uiDataSnapshot(sink);
      sink.set(DiffDataKeys.EDITOR_TAB_DIFF_PREVIEW, myEditorDiffPreview);
      sink.set(ChangesViewDataKeys.SETTINGS, myChangesViewSettings);
      sink.set(ChangesViewDataKeys.REFRESHER, () -> myChangesView.scheduleRefreshNow(null));

      // This makes COMMIT_WORKFLOW_HANDLER available anywhere in "Local Changes" - so commit executor actions are enabled.
      DataSink.uiDataSnapshot(sink, myCommitPanel);
    }

    private static void registerShortcuts(@NotNull JComponent component) {
      ActionUtil.wrap("ChangesView.Refresh").registerCustomShortcutSet(CommonShortcuts.getRerun(), component);
      ActionUtil.wrap("ChangesView.NewChangeList").registerCustomShortcutSet(CommonShortcuts.getNew(), component);
      ActionUtil.wrap("ChangesView.RemoveChangeList").registerCustomShortcutSet(CommonShortcuts.getDelete(), component);
      ActionUtil.wrap(IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST).registerCustomShortcutSet(CommonShortcuts.getMove(), component);
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

    private void invokeLaterIfNeeded(Runnable runnable) {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.nonModal(), myProject.getDisposed(), runnable);
    }

    public interface Listener extends EventListener {
      void allowExcludeFromCommitChanged();
    }
  }

  public static @NotNull @Nls String getLocalChangesToolWindowName(@NotNull Project project) {
    return isCommitToolWindowShown(project) ? VcsBundle.message("tab.title.commit") : VcsBundle.message("local.changes.tab");
  }

  @ApiStatus.Internal
  @Override
  public @Nullable ChangesViewCommitWorkflowHandler getCommitWorkflowHandler() {
    return ChangesViewWorkflowManager.getInstance(myProject).getCommitWorkflowHandler();
  }

  @ApiStatus.Internal
  public @Nullable ChangesViewProxy getChangesView() {
    return myChangesView;
  }

  private static boolean isAllowExcludeFromCommit(@Nullable ChangesViewCommitWorkflowHandler handler) {
    return handler != null && handler.isActive();
  }
}
