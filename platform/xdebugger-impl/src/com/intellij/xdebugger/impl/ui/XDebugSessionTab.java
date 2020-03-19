// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.execution.ui.layout.impl.ViewImpl;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AppIcon;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.hash.LinkedHashMap;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.*;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class XDebugSessionTab extends DebuggerSessionTabBase {
  public static final DataKey<XDebugSessionTab> TAB_KEY = DataKey.create("XDebugSessionTab");

  protected XWatchesViewImpl myWatchesView;
  private boolean myWatchesInVariables = Registry.is("debugger.watches.in.variables");
  private final LinkedHashMap<String, XDebugView> myViews = new LinkedHashMap<>();

  @Nullable
  protected XDebugSessionImpl mySession;
  private XDebugSessionData mySessionData;

  @NotNull
  public static XDebugSessionTab create(@NotNull XDebugSessionImpl session,
                                        @Nullable Icon icon,
                                        @Nullable ExecutionEnvironment environment,
                                        @Nullable RunContentDescriptor contentToReuse) {
    if (contentToReuse != null && SystemProperties.getBooleanProperty("xdebugger.reuse.session.tab", false)) {
      JComponent component = contentToReuse.getComponent();
      if (component != null) {
        XDebugSessionTab oldTab = TAB_KEY.getData(DataManager.getInstance().getDataContext(component));
        if (oldTab != null) {
          oldTab.setSession(session, environment, icon);
          oldTab.attachToSession(session);
          return oldTab;
        }
      }
    }
    XDebugSessionTab tab = Registry.is("debugger.new.debug.tool.window.view") ?
                           new XDebugSessionTab2(session, icon, environment) :
                           new XDebugSessionTab(session, icon, environment, true);

    tab.init(session);
    tab.myRunContentDescriptor.setActivateToolWindowWhenAdded(contentToReuse == null || contentToReuse.isActivateToolWindowWhenAdded());
    return tab;
  }

  @NotNull
  public RunnerLayoutUi getUi() {
    return myUi;
  }

  protected XDebugSessionTab(@NotNull XDebugSessionImpl session,
                             @Nullable Icon icon,
                             @Nullable ExecutionEnvironment environment,
                             boolean shouldInitTabDefaults) {
    super(session.getProject(), "Debug", session.getSessionName(), GlobalSearchScope.allScope(session.getProject()), shouldInitTabDefaults);

    setSession(session, environment, icon);
  }

  protected void init(XDebugSessionImpl session) {
    initDebuggerTab(session);

    attachToSession(session);

    DefaultActionGroup focus = new DefaultActionGroup();
    focus.add(ActionManager.getInstance().getAction(XDebuggerActions.FOCUS_ON_BREAKPOINT));
    myUi.getOptions().setAdditionalFocusActions(focus).setMinimizeActionEnabled(true).setMoveToGridActionEnabled(true);

    initListeners(myUi);
    rebuildViews();
  }

  protected void initDebuggerTab(XDebugSessionImpl session) {
    Content framesContent = createFramesContent();
    myUi.addContent(framesContent, 0, PlaceInGrid.left, false);

    if (Registry.is("debugger.new.threads.view")) {
      myUi.addContent(createThreadsContent(), 0, PlaceInGrid.right, true);
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

  private void addVariablesAndWatches(@NotNull XDebugSessionImpl session) {
    myUi.addContent(createVariablesContent(session), 0, PlaceInGrid.center, false);
    if (!myWatchesInVariables) {
      myUi.addContent(createWatchesContent(session), 0, PlaceInGrid.right, false);
    }
  }

  private void setSession(@NotNull XDebugSessionImpl session, @Nullable ExecutionEnvironment environment, @Nullable Icon icon) {
    myEnvironment = environment;
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

    myRunContentDescriptor = new RunContentDescriptor(myConsole, session.getDebugProcess().getProcessHandler(),
                                                      myUi.getComponent(), session.getSessionName(), icon, this::computeWatches, restartActions);
    myRunContentDescriptor.setRunnerLayoutUi(myUi);
    Disposer.register(myRunContentDescriptor, this);
    Disposer.register(myProject, myRunContentDescriptor);
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (XWatchesView.DATA_KEY.is(dataId)) {
      return myWatchesView;
    }
    else if (TAB_KEY.is(dataId)) {
      return this;
    }
    else if (XDebugSessionData.DATA_KEY.is(dataId)) {
      return mySessionData;
    }

    if (mySession != null) {
      if (XDebugSession.DATA_KEY.is(dataId)) {
        return mySession;
      }
      else if (LangDataKeys.CONSOLE_VIEW.is(dataId)) {
        return mySession.getConsoleView();
      }
    }

    return super.getData(dataId);
  }

  private Content createVariablesContent(@NotNull XDebugSessionImpl session) {
    XVariablesView variablesView;
    if (myWatchesInVariables) {
      variablesView = myWatchesView = new XWatchesViewImpl(session, myWatchesInVariables);
    }
    else {
      variablesView = new XVariablesView(session);
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

  private Content createWatchesContent(@NotNull XDebugSessionImpl session) {
    myWatchesView = new XWatchesViewImpl(session, myWatchesInVariables);
    registerView(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getPanel(),
                                                XDebuggerBundle.message("debugger.session.tab.watches.title"), null, myWatchesView.getDefaultFocusedComponent());
    watchesContent.setCloseable(false);
    return watchesContent;
  }

  @NotNull
  private Content createFramesContent() {
    XFramesView framesView = new XFramesView(myProject);
    registerView(DebuggerContentInfo.FRAME_CONTENT, framesView);
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, framesView.getMainPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.frames.title"), null, framesView.getDefaultFocusedComponent());
    framesContent.setCloseable(false);
    return framesContent;
  }

  @NotNull
  private Content createThreadsContent() {
    XThreadsView stacksView = new XThreadsView(myProject, mySession);
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

  private void attachToSession(@NotNull XDebugSessionImpl session) {
    for (XDebugView view : myViews.values()) {
      attachViewToSession(session, view);
    }

    XDebugTabLayouter layouter = session.getDebugProcess().createTabLayouter();
    Content consoleContent = layouter.registerConsoleContent(myUi, myConsole);
    attachNotificationTo(consoleContent);

    layouter.registerAdditionalContent(myUi);
    RunContentBuilder.addAdditionalConsoleEditorActions(myConsole, consoleContent);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    DefaultActionGroup leftToolbar = new DefaultActionGroup();
    final Executor debugExecutor = DefaultDebugExecutor.getDebugExecutorInstance();
    consoleContent.setHelpId(debugExecutor.getHelpId());
    if (myEnvironment != null) {
      leftToolbar.add(ActionManager.getInstance().getAction(IdeActions.ACTION_RERUN));
      List<AnAction> additionalRestartActions = session.getRestartActions();
      if (!additionalRestartActions.isEmpty()) {
        leftToolbar.addAll(additionalRestartActions);
        leftToolbar.addSeparator();
      }
      leftToolbar.addAll(session.getExtraActions());
    }
    leftToolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_LEFT_TOOLBAR_GROUP));

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

    DefaultActionGroup topLeftToolbar = new DefaultActionGroup();
    topLeftToolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP));

    registerAdditionalActions(leftToolbar, topLeftToolbar, settings);
    myUi.getOptions().setLeftToolbar(leftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.getOptions().setTopLeftToolbar(topLeftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);

    if (myEnvironment != null) {
      initLogConsoles(myEnvironment.getRunProfile(), myRunContentDescriptor, myConsole);
    }
  }

  protected void registerAdditionalActions(DefaultActionGroup leftToolbar, DefaultActionGroup topLeftToolbar, DefaultActionGroup settings) {
    mySession.getDebugProcess().registerAdditionalActions(leftToolbar, topLeftToolbar, settings);
  }

  protected static void attachViewToSession(@NotNull XDebugSessionImpl session, @Nullable XDebugView view) {
    if (view != null) {
      XDebugViewSessionListener.attach(view, session);
    }
  }

  public void detachFromSession() {
    assert mySession != null;
    mySession = null;
  }

  @Nullable
  public RunContentDescriptor getRunContentDescriptor() {
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
    removeContent(DebuggerContentInfo.VARIABLES_CONTENT);
    removeContent(DebuggerContentInfo.WATCHES_CONTENT);
    addVariablesAndWatches(mySession);
    attachViewToSession(mySession, myViews.get(DebuggerContentInfo.VARIABLES_CONTENT));
    attachViewToSession(mySession, myViews.get(DebuggerContentInfo.WATCHES_CONTENT));
    myUi.selectAndFocus(myUi.findContent(DebuggerContentInfo.VARIABLES_CONTENT), true, false);
    rebuildViews();
  }

  public static void showWatchesView(@NotNull XDebugSessionImpl session) {
    XDebugSessionTab tab = session.getSessionTab();
    if (tab != null) {
      showView(session, tab.getWatchesContentId());
    }
  }

  public static void showFramesView(@Nullable XDebugSessionImpl session) {
    XDebugSessionTab tab = session != null ? session.getSessionTab() : null;
    if (tab != null) {
      showView(session, tab.getFramesContentId());
    }
  }

  private static void showView(@Nullable XDebugSessionImpl session, String viewId) {
    XDebugSessionTab tab = session != null ? session.getSessionTab() : null;
    if (tab != null) {
      tab.toFront(false, null);
      // restore watches tab if minimized
      tab.restoreContent(viewId);

      RunnerLayoutUi layoutUi = tab.getUi();
      if (layoutUi instanceof DataProvider) {
        RunnerContentUi ui = RunnerContentUi.KEY.getData(((DataProvider)layoutUi));
        if (ui != null) {
          Content content = ui.findContent(viewId);

          // if the view is not visible (e.g. Console tab is selected, while Debugger tab is not)
          // make sure we make it visible to the user
          if (content != null) {
            ui.select(content, false);
          }
        }
      }
    }
  }

  public void toFront(boolean focus, @Nullable final Runnable onShowCallback) {
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
        boolean focusWnd = Registry.is("debugger.mayBringFrameToFrontOnBreakpoint");
        ProjectUtil.focusProjectWindow(myProject, focusWnd);
        if (!focusWnd) {
          AppIcon.getInstance().requestAttention(myProject, true);
        }
      });
    }
  }

  protected void computeWatches() {
    if (myWatchesView != null) {
      myWatchesView.computeWatches();
    }
  }

  @NotNull
  protected String getWatchesContentId() {
    return myWatchesInVariables ? DebuggerContentInfo.VARIABLES_CONTENT : DebuggerContentInfo.WATCHES_CONTENT;
  }

  @NotNull
  protected String getFramesContentId() {
    return DebuggerContentInfo.FRAME_CONTENT;
  }

  protected void registerView(String contentId, @NotNull XDebugView view) {
    myViews.put(contentId, view);
    Disposer.register(myRunContentDescriptor, view);
  }

  private void removeContent(String contentId) {
    restoreContent(contentId); //findContent returns null if content is minimized
    myUi.removeContent(myUi.findContent(contentId), true);
    unregisterView(contentId);
  }

  protected void unregisterView(String contentId) {
    XDebugView view = myViews.remove(contentId);
    if (view != null) {
      Disposer.dispose(view);
    }
  }

  protected void restoreContent(String contentId) {
    if (myUi instanceof DataProvider) {
      RunnerContentUi ui = RunnerContentUi.KEY.getData(((DataProvider)myUi));
      if (ui != null) {
        ui.restoreContent(contentId);
      }
    }
  }
}
