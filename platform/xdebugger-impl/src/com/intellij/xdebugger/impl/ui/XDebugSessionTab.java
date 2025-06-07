// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.actions.CreateAction;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.BackendExecutionEnvironmentProxy;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentProxy;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.UIExperiment;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl;
import com.intellij.execution.ui.layout.impl.ViewImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.customization.CustomActionsListener;
import com.intellij.ide.ui.customization.DefaultActionGroupWithDelegate;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebugSessionSelectionService;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.*;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Note: could be stored in frontend, but it kept in shared due to compatibility issues.
 */
@ApiStatus.Internal
public class XDebugSessionTab extends DebuggerSessionTabBase {
  private static final Logger LOG = Logger.getInstance(XDebugSessionTab.class);
  public static final DataKey<XDebugSessionTab> TAB_KEY = DataKey.create("XDebugSessionTab");

  protected XWatchesViewImpl myWatchesView;
  private boolean myWatchesInVariables = Registry.is("debugger.watches.in.variables");
  private final Map<String, XDebugView> myViews = new LinkedHashMap<>();

  protected @Nullable XDebugSessionProxy mySession;
  private XDebugSessionData mySessionData;

  /**
   * @deprecated Use {@link XDebugSessionTab#create(XDebugSessionProxy, Icon, ExecutionEnvironmentProxy, RunContentDescriptor, boolean, boolean)}
   */
  @Deprecated
  public static @NotNull XDebugSessionTab create(@NotNull XDebugSessionImpl session,
                                                 @Nullable Icon icon,
                                                 @Nullable ExecutionEnvironment environment,
                                                 @Nullable RunContentDescriptor contentToReuse) {
    XDebugSessionProxy proxy = XDebugSessionProxyKeeperKt.asProxy(session);
    boolean forceNewDebuggerUi = XDebugSessionTabCustomizerKt.forceShowNewDebuggerUi(session.getDebugProcess());
    boolean withFramesCustomization = XDebugSessionTabCustomizerKt.allowFramesViewCustomization(session.getDebugProcess());
    return create(proxy, icon, environment == null ? null : new BackendExecutionEnvironmentProxy(environment), contentToReuse, forceNewDebuggerUi, withFramesCustomization);
  }

  @ApiStatus.Internal
  public static @NotNull XDebugSessionTab create(@NotNull XDebugSessionProxy proxy,
                                                 @Nullable Icon icon,
                                                 @Nullable ExecutionEnvironmentProxy environmentProxy,
                                                 @Nullable RunContentDescriptor contentToReuse,
                                                 boolean forceNewDebuggerUi,
                                                 boolean withFramesCustomization) {
    if (contentToReuse != null && SystemProperties.getBooleanProperty("xdebugger.reuse.session.tab", false)) {
      JComponent component = contentToReuse.getComponent();
      if (component != null) {
        XDebugSessionTab oldTab = TAB_KEY.getData(DataManager.getInstance().getDataContext(component));
        if (oldTab != null) {
          oldTab.setSession(proxy, environmentProxy, icon);
          oldTab.attachToSession(proxy);
          return oldTab;
        }
      }
    }
    XDebugSessionTab tab;
    if (UIExperiment.isNewDebuggerUIEnabled() || forceNewDebuggerUi) {
      if (withFramesCustomization) {
        if (proxy instanceof XDebugSessionProxy.Monolith monolith) {
          tab = new XDebugSessionTab3(monolith, icon, environmentProxy);
        }
        else {
          throw new IllegalStateException("Frames view customization is not supported in split mode");
        }
      }
      else {
        tab = new XDebugSessionTabNewUI(proxy, icon, environmentProxy);
      }
    }
    else {
      tab = new XDebugSessionTab(proxy, icon, environmentProxy, true);
    }

    tab.init(proxy);
    tab.myRunContentDescriptor.setActivateToolWindowWhenAdded(contentToReuse == null || contentToReuse.isActivateToolWindowWhenAdded());
    return tab;
  }

  public @NotNull RunnerLayoutUi getUi() {
    return myUi;
  }

  protected XDebugSessionTab(@NotNull XDebugSessionProxy session,
                             @Nullable Icon icon,
                             @Nullable ExecutionEnvironmentProxy environmentProxy,
                             boolean shouldInitTabDefaults) {
    super(session.getProject(), "Debug", session.getSessionName(), GlobalSearchScope.allScope(session.getProject()), shouldInitTabDefaults);

    setSession(session, environmentProxy, icon);
    myUi.getContentManager().addUiDataProvider(sink -> {
      sink.set(XWatchesView.DATA_KEY, myWatchesView);
      sink.set(TAB_KEY, this);
      sink.set(XDebugSessionData.DATA_KEY, mySessionData);

      if (mySession != null) {
        sink.set(XDebugSessionProxy.DEBUG_SESSION_PROXY_KEY, mySession);
        mySession.putKey(sink);
        sink.set(LangDataKeys.CONSOLE_VIEW, mySession.getConsoleView());
      }
    });
  }

  protected void init(XDebugSessionProxy session) {
    initDebuggerTab(session);
    initFocusingVariablesFromFramesView();

    attachToSession(session);

    DefaultActionGroup focus = new DefaultActionGroup();
    focus.add(ActionManager.getInstance().getAction(XDebuggerActions.FOCUS_ON_BREAKPOINT));
    focus.add(ActionManager.getInstance().getAction(XDebuggerActions.FOCUS_ON_FINISH));
    myUi.getOptions().setAdditionalFocusActions(focus).setMinimizeActionEnabled(true).setMoveToGridActionEnabled(true);

    initListeners(myUi);
    rebuildViews();
  }

  protected @Nullable <T> T getView(String viewId, Class<T> viewClass) {
    return ObjectUtils.tryCast(myViews.get(viewId), viewClass);
  }

  @ApiStatus.Internal
  public @Nullable XFramesView getFramesView() {
    return getView(getFramesContentId(), XFramesView.class);
  }

  @ApiStatus.Internal
  public @Nullable XVariablesViewBase getVariablesView() {
    return getView(DebuggerContentInfo.VARIABLES_CONTENT, XVariablesViewBase.class);
  }

  @ApiStatus.Internal
  public void showTab() {
    RunContentDescriptor descriptor = getRunContentDescriptor();
    if (descriptor == null) return;
    RunContentManager.getInstance(myProject).showRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), descriptor);
  }

  protected void initFocusingVariablesFromFramesView() {
    XFramesView framesView = getView(DebuggerContentInfo.FRAME_CONTENT, XFramesView.class);
    XVariablesViewBase variablesView = getVariablesView();
    if (framesView == null || variablesView == null) return;

    framesView.onFrameSelectionKeyPressed(frame -> {
      Content content = findOrRestoreContentIfNeeded(DebuggerContentInfo.VARIABLES_CONTENT);
      if (content == null) return;
      getUi().selectAndFocus(content, true, true).doWhenDone(() -> {
        // Don't ruin an old selection from the saved tree state (if any) while it's being restored.
        // Most of the time restoring the selection is fast enough. But even if it's not,
        // the variables view is focused instantly, so the user can still use the arrow keys
        // for navigating through the variables loaded so far. The tree restorer, in turn,
        // is careful enough to not reset the selection if the user happened to change it already.
        variablesView.onReady().whenComplete((node, throwable) -> {
          if (node != null && node.getTree().isSelectionEmpty()) {
            node.getTree().setSelectionRow(0);
          }
        });
      });
    });
  }

  protected void initDebuggerTab(XDebugSessionProxy session) {
    createDefaultTabs(session);
    CustomActionsListener.subscribe(this, () -> initToolbars(session));
  }

  protected final void createDefaultTabs(XDebugSessionProxy session) {
    myUi.addContent(createFramesContent(session), 0, PlaceInGrid.left, false);

    if (Registry.is("debugger.new.threads.view")) {
      Content threadsContent = createThreadsContent(session);
      if (threadsContent != null) {
        myUi.addContent(threadsContent, 0, PlaceInGrid.right, true);
      }
    }

    addVariablesAndWatches(session);
  }

  protected void initListeners(RunnerLayoutUi ui) {
    ui.addListener(new ContentManagerListener() {
      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
        Content content = event.getContent();
        if (mySession != null && content.isSelected() && getWatchesContentId().equals(ViewImpl.ID.get(content))) {
          computeWatches();
        }
      }
    }, myRunContentDescriptor);
  }

  protected void addVariablesAndWatches(@NotNull XDebugSessionProxy session) {
    Content variablesContent = createVariablesContent(session);
    myUi.addContent(variablesContent, 0, PlaceInGrid.center, false);
    if (!myWatchesInVariables) {
      Content watchesContent = createWatchesContent(session, null);
      myUi.addContent(watchesContent, 0, PlaceInGrid.right, false);
    }
  }

  private void setSession(@NotNull XDebugSessionProxy session, @Nullable ExecutionEnvironmentProxy environmentProxy, @Nullable Icon icon) {
    myEnvironment = environmentProxy == null ? null : environmentProxy.getExecutionEnvironment();
    myEnvironmentProxy = environmentProxy;
    mySession = session;
    mySessionData = session.getSessionData();
    myConsole = session.getConsoleView();

    AnAction[] restartActions;
    List<AnAction> restartActionsList = session.getRestartActions();
    if (restartActionsList.isEmpty()) {
      restartActions = AnAction.EMPTY_ARRAY;
    }
    else {
      restartActions = restartActionsList.toArray(AnAction.EMPTY_ARRAY);
    }

    myRunContentDescriptor = new RunContentDescriptor(myConsole, session.getProcessHandler(),
                                                      myUi.getComponent(), session.getSessionName(), icon, this::computeWatches,
                                                      restartActions);
    myRunContentDescriptor.setRunnerLayoutUi(myUi);
    Disposer.register(myRunContentDescriptor, this);
    Disposer.register(myProject, myRunContentDescriptor);

    XDebugSessionSelectionService.startCurrentSessionListening(myProject);

    session.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionPaused() {
        updateActions();
      }

      @Override
      public void sessionResumed() {
        updateActions();
      }

      @Override
      public void sessionStopped() {
        updateActions();
        AppUIUtil.invokeOnEdt(() -> {
          myUi.attractBy(XDebuggerUIConstants.LAYOUT_VIEW_FINISH_CONDITION);
          if (!myProject.isDisposed()) {
            myWatchesView.updateSessionData();
          }
          detachFromSession();
        });

        if (!myProject.isDisposed() &&
            !ApplicationManager.getApplication().isUnitTestMode() &&
            XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isHideDebuggerOnProcessTermination()) {
          RunContentManager.getInstance(myProject).hideRunContent(DefaultDebugExecutor.getDebugExecutorInstance(),
                                                                  getRunContentDescriptor());
        }
      }

      @Override
      public void stackFrameChanged() {
        updateActions();
      }

      @Override
      public void beforeSessionResume() {
        UIUtil.invokeLaterIfNeeded(() -> {
          getUi().clearAttractionBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
        });
      }

      private void updateActions() {
        UIUtil.invokeLaterIfNeeded(() -> getUi().updateActionsNow());
      }
    }, this);
  }

  private @NotNull Content createVariablesContent(@NotNull XDebugSessionProxy proxy) {
    XVariablesView variablesView;
    if (myWatchesInVariables) {
      variablesView = myWatchesView = new XWatchesViewImpl(proxy, myWatchesInVariables, false, false);
    }
    else {
      variablesView = new XVariablesView(proxy);
    }
    registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView);
    Content result = myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, variablesView.getPanel(),
                                        XDebuggerBundle.message("debugger.session.tab.variables.title"),
                                        null, variablesView.getDefaultFocusedComponent());
    result.setCloseable(false);

    ActionGroup group = getCustomizedActionGroup(XDebuggerActions.VARIABLES_TREE_TOOLBAR_GROUP);
    result.setActions(group, ActionPlaces.DEBUGGER_TOOLBAR, variablesView.getTree());
    return result;
  }

  protected @NotNull Content createWatchesContent(@NotNull XDebugSessionProxy proxy, @Nullable XWatchesViewImpl watchesView) {
    myWatchesView = watchesView != null ? watchesView : new XWatchesViewImpl(proxy, myWatchesInVariables);
    registerView(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getPanel(),
                                                XDebuggerBundle.message("debugger.session.tab.watches.title"), null,
                                                myWatchesView.getDefaultFocusedComponent());
    watchesContent.setCloseable(false);
    return watchesContent;
  }

  private @NotNull Content createFramesContent(XDebugSessionProxy proxy) {
    XFramesView framesView = new XFramesView(proxy);
    registerView(DebuggerContentInfo.FRAME_CONTENT, framesView);
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, framesView.getMainPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.frames.title"), null,
                                               framesView.getFramesList());
    framesContent.setCloseable(false);
    return framesContent;
  }

  private @Nullable Content createThreadsContent(XDebugSessionProxy proxy) {
    if (!(proxy instanceof XDebugSessionProxy.Monolith monolith)) {
      LOG.error("Threads view is not supported in split mode");
      return null;
    }
    XDebugSessionImpl session = (XDebugSessionImpl)monolith.getSession();
    XThreadsView stacksView = new XThreadsView(myProject, session);
    registerView(DebuggerContentInfo.THREADS_CONTENT, stacksView);
    Content framesContent = myUi.createContent(DebuggerContentInfo.THREADS_CONTENT, stacksView.getPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.threads.title"), null,
                                               stacksView.getDefaultFocusedComponent());
    framesContent.setCloseable(false);
    return framesContent;
  }

  public void rebuildViews() {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
      if (mySession != null) {
        mySession.rebuildViews();
      }
    });
  }

  public XWatchesView getWatchesView() {
    return myWatchesView;
  }

  private void attachToSession(@NotNull XDebugSessionProxy session) {
    for (XDebugView view : myViews.values()) {
      attachViewToSession(session, view);
    }

    XDebugTabLayouter layouter = session.createTabLayouter();
    if (myConsole != null) { // TODO should be non-null
      Content consoleContent = layouter.registerConsoleContent(myUi, myConsole);
      attachNotificationTo(consoleContent);
      layouter.registerAdditionalContent(myUi);

      RunContentBuilder.addAdditionalConsoleEditorActions(myConsole, consoleContent);
      consoleContent.setHelpId(DefaultDebugExecutor.getDebugExecutorInstance().getHelpId());
    }
    else {
      layouter.registerAdditionalContent(myUi);
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    initToolbars(session);

    if (myEnvironment != null && myConsole != null) { // TODO should be non-null
      initLogConsoles(myEnvironment.getRunProfile(), myRunContentDescriptor, myConsole);
    }
  }

  protected void initToolbars(@NotNull XDebugSessionProxy session) {
    ActionGroup leftGroup = getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_LEFT_TOOLBAR_GROUP);
    DefaultActionGroup leftToolbar = new DefaultActionGroupWithDelegate(leftGroup);
    if (myEnvironment != null) {
      leftToolbar.add(ActionManager.getInstance().getAction(IdeActions.ACTION_RERUN));
      leftToolbar.addAll(session.getRestartActions());
      leftToolbar.add(new CreateAction(AllIcons.General.Settings));
      leftToolbar.addSeparator();
      leftToolbar.addAll(session.getExtraActions());
    }
    RunContentBuilder.addAvoidingDuplicates(leftToolbar, leftGroup);

    for (AnAction action : session.getExtraStopActions()) {
      leftToolbar.add(action, new Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM));
    }

    //group.addSeparator();
    //addAction(group, DebuggerActions.EXPORT_THREADS);
    leftToolbar.addSeparator();

    leftToolbar.add(myUi.getOptions().getLayoutActions());
    final AnAction[] commonSettings = myUi.getOptions().getSettingsActionsList();
    DefaultActionGroup settings = DefaultActionGroup.createPopupGroup(ActionsBundle.messagePointer("group.XDebugger.settings.text"));
    settings.getTemplatePresentation().setIcon(myUi.getOptions().getSettingsActions().getTemplatePresentation().getIcon());
    settings.addAll(commonSettings);
    leftToolbar.add(settings);

    leftToolbar.addSeparator();

    leftToolbar.add(PinToolwindowTabAction.getPinAction());

    ActionGroup topGroup = getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP);
    DefaultActionGroup topLeftToolbar = new DefaultActionGroupWithDelegate(topGroup);
    RunContentBuilder.addAvoidingDuplicates(topLeftToolbar, topGroup);

    registerAdditionalActions(leftToolbar, topLeftToolbar, settings);
    myUi.getOptions().setLeftToolbar(leftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.getOptions().setTopLeftToolbar(topLeftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
  }

  protected void registerAdditionalActions(DefaultActionGroup leftToolbar, DefaultActionGroup topLeftToolbar, DefaultActionGroup settings) {
    if (mySession != null) {
      mySession.registerAdditionalActions(leftToolbar, topLeftToolbar, settings);
    }
  }

  protected static void attachViewToSession(@NotNull XDebugSessionProxy session, @Nullable XDebugView view) {
    if (view != null) {
      XDebugViewSessionListener.attach(view, session);
    }
  }

  private void detachFromSession() {
    assert mySession != null;
    mySession = null;
  }

  public @Nullable RunContentDescriptor getRunContentDescriptor() {
    return myRunContentDescriptor;
  }

  public boolean isWatchesInVariables() {
    return myWatchesInVariables;
  }

  public void setWatchesInVariables(boolean watchesInVariables) {
    if (myWatchesInVariables != watchesInVariables) {
      myWatchesInVariables = watchesInVariables;
      Registry.get("debugger.watches.in.variables").setValue(watchesInVariables);
      if (mySession != null) {
        setWatchesInVariablesImpl();
      }
    }
  }

  protected void setWatchesInVariablesImpl() {
    if (mySession == null) return;

    removeContent(DebuggerContentInfo.VARIABLES_CONTENT);
    removeContent(DebuggerContentInfo.WATCHES_CONTENT);
    addVariablesAndWatches(mySession);
    attachViewToSession(mySession, myViews.get(DebuggerContentInfo.VARIABLES_CONTENT));
    attachViewToSession(mySession, myViews.get(DebuggerContentInfo.WATCHES_CONTENT));
    myUi.selectAndFocus(myUi.findContent(DebuggerContentInfo.VARIABLES_CONTENT), true, false);
    rebuildViews();
  }

  /**
   * @deprecated Use {@link XDebugSessionTab#showWatchesView(XDebugSessionProxy)} instead
   */
  @Deprecated
  public static void showWatchesView(@NotNull XDebugSessionImpl session) {
    showWatchesView(XDebugSessionProxyKeeperKt.asProxy(session));
  }

  public static void showWatchesView(@NotNull XDebugSessionProxy session) {
    XDebugSessionTab tab = session.getSessionTab();
    if (tab == null) return;
    tab.showView(tab.getWatchesContentId());
  }

  public static void showFramesView(@Nullable XDebugSessionImpl session) {
    if (session == null) return;
    XDebugSessionTab tab = session.getSessionTab();
    if (tab == null) return;
    tab.showView(tab.getFramesContentId());
  }

  void showView(String viewId) {
    toFront(false, null);
    Content content = findOrRestoreContentIfNeeded(viewId);
    // make sure we make it visible to the user
    if (content != null) {
      myUi.selectAndFocus(content, false, false);
    }
  }

  public void toFront(boolean focus, final @Nullable Runnable onShowCallback) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    ApplicationManager.getApplication().invokeLater(() -> {
      if (myRunContentDescriptor != null) {
        RunContentManager manager = RunContentManager.getInstance(myProject);
        ToolWindow toolWindow = manager.getToolWindowByDescriptor(myRunContentDescriptor);
        if (toolWindow != null) {
          if (!toolWindow.isVisible()) {
            toolWindow.show(() -> {
              if (onShowCallback != null) {
                onShowCallback.run();
              }
              computeWatches();
            });
          }
          manager.selectRunContent(myRunContentDescriptor);
        }
      }
    });

    if (focus) {
      ApplicationManager.getApplication().invokeLater(() -> {
        boolean stealFocus = Registry.is("debugger.mayBringFrameToFrontOnBreakpoint");
        ProjectUtil.focusProjectWindow(myProject, stealFocus);
      });
    }
  }

  protected void computeWatches() {
    if (myWatchesView != null) {
      myWatchesView.computeWatches();
    }
  }

  protected @NotNull String getWatchesContentId() {
    return myWatchesInVariables ? DebuggerContentInfo.VARIABLES_CONTENT : DebuggerContentInfo.WATCHES_CONTENT;
  }

  protected @NotNull String getFramesContentId() {
    return DebuggerContentInfo.FRAME_CONTENT;
  }

  protected void registerView(String contentId, @NotNull XDebugView view) {
    myViews.put(contentId, view);
    Disposer.register(myRunContentDescriptor, view);
  }

  private void removeContent(String contentId) {
    myUi.removeContent(findOrRestoreContentIfNeeded(contentId), true);
    unregisterView(contentId);
  }

  protected void unregisterView(String contentId) {
    XDebugView view = myViews.remove(contentId);
    if (view != null) {
      Disposer.dispose(view);
    }
  }

  public @Nullable Content findOrRestoreContentIfNeeded(@NotNull String contentId) {
    RunnerContentUi contentUi = myUi instanceof RunnerLayoutUiImpl o ? o.getContentUI() : null;
    if (contentUi != null) {
      return contentUi.findOrRestoreContentIfNeeded(contentId);
    }
    return myUi.findContent(contentId);
  }

  @ApiStatus.Internal
  public void onPause(boolean pausedByUser, boolean topFramePositionAbsent) {
    // user attractions should only be made if event happens independently (e.g. program paused/suspended)
    // and should not be made when user steps in the code
    if (!pausedByUser) return;
    if (XDebuggerSettingManagerImpl.getInstanceImpl().getGeneralSettings().isShowDebuggerOnBreakpoint()) {
      toFront(true, () -> {
        if (mySession != null) {
          mySession.updateExecutionPosition();
        }
      });
    }

    if (topFramePositionAbsent) {
      // if there is no source position available, we should somehow tell the user that session is stopped.
      // the best way is to show the stack frames.
      showView(getFramesContentId());
    }

    getUi().attractBy(XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION);
  }
}
