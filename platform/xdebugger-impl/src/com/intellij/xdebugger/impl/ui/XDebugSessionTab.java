/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.ui;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.execution.ui.layout.impl.ViewImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ContextHelpAction;
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
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
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

  private XWatchesViewImpl myWatchesView;
  private XFramesView myFramesView;
  private boolean myWatchesInVariables = Registry.is("debugger.watches.in.variables");
  private final LinkedHashMap<String, XDebugView> myViews = new LinkedHashMap<>();

  @Nullable
  private XDebugSessionImpl mySession;
  private XDebugSessionData mySessionData;

  private final Runnable myRebuildWatchesRunnable = new Runnable() {
    @Override
    public void run() {
      if (myWatchesView != null) {
        myWatchesView.computeWatches();
      }
    }
  };

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
    XDebugSessionTab tab = new XDebugSessionTab(session, icon, environment);
    tab.myRunContentDescriptor.setActivateToolWindowWhenAdded(contentToReuse == null || contentToReuse.isActivateToolWindowWhenAdded());
    return tab;
  }

  @NotNull
  public RunnerLayoutUi getUi() {
    return myUi;
  }

  private XDebugSessionTab(@NotNull XDebugSessionImpl session,
                           @Nullable Icon icon,
                           @Nullable ExecutionEnvironment environment) {
    super(session.getProject(), "Debug", session.getSessionName(), GlobalSearchScope.allScope(session.getProject()));

    setSession(session, environment, icon);

    myUi.addContent(createFramesContent(), 0, PlaceInGrid.left, false);
    addVariablesAndWatches(session);

    attachToSession(session);

    DefaultActionGroup focus = new DefaultActionGroup();
    focus.add(ActionManager.getInstance().getAction(XDebuggerActions.FOCUS_ON_BREAKPOINT));
    myUi.getOptions().setAdditionalFocusActions(focus);

    myUi.addListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        Content content = event.getContent();
        if (mySession != null && content.isSelected() && getWatchesContentId().equals(ViewImpl.ID.get(content))) {
          myRebuildWatchesRunnable.run();
        }
      }
    }, myRunContentDescriptor);

    rebuildViews();
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
    if (ContainerUtil.isEmpty(restartActionsList)) {
      restartActions = AnAction.EMPTY_ARRAY;
    }
    else {
      restartActions = restartActionsList.toArray(new AnAction[restartActionsList.size()]);
    }

    myRunContentDescriptor = new RunContentDescriptor(myConsole, session.getDebugProcess().getProcessHandler(),
                                                      myUi.getComponent(), session.getSessionName(), icon, myRebuildWatchesRunnable, restartActions);
    myRunContentDescriptor.setRunnerLayoutUi(myUi);
    Disposer.register(myRunContentDescriptor, this);
    Disposer.register(myProject, myRunContentDescriptor);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (XWatchesView.DATA_KEY.is(dataId)) {
      return myWatchesView;
    }
    else if (XFramesView.DATA_KEY.is(dataId)) {
      return myFramesView;
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
                                        AllIcons.Debugger.Value, null);
    result.setCloseable(false);

    ActionGroup group = getCustomizedActionGroup(XDebuggerActions.VARIABLES_TREE_TOOLBAR_GROUP);
    result.setActions(group, ActionPlaces.DEBUGGER_TOOLBAR, variablesView.getTree());
    return result;
  }

  private Content createWatchesContent(@NotNull XDebugSessionImpl session) {
    myWatchesView = new XWatchesViewImpl(session, myWatchesInVariables);
    registerView(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getPanel(),
                                                XDebuggerBundle.message("debugger.session.tab.watches.title"), AllIcons.Debugger.Watches, null);
    watchesContent.setCloseable(false);
    return watchesContent;
  }

  @NotNull
  private Content createFramesContent() {
    myFramesView = new XFramesView(myProject);
    registerView(DebuggerContentInfo.FRAME_CONTENT, myFramesView);
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, myFramesView.getMainPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.frames.title"), AllIcons.Debugger.Frame, null);
    framesContent.setCloseable(false);
    return framesContent;
  }

  public void rebuildViews() {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
      if (mySession != null) {
        for (XDebugView view : myViews.values()) {
          view.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED, mySession);
        }
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
    DefaultActionGroup settings = new DefaultActionGroup(ActionsBundle.message("group.XDebugger.settings.text"), true);
    settings.getTemplatePresentation().setIcon(myUi.getOptions().getSettingsActions().getTemplatePresentation().getIcon());
    settings.addAll(commonSettings);
    leftToolbar.add(settings);

    leftToolbar.addSeparator();

    leftToolbar.add(PinToolwindowTabAction.getPinAction());
    leftToolbar.add(new CloseAction(myEnvironment != null ? myEnvironment.getExecutor() : debugExecutor, myRunContentDescriptor, myProject));
    leftToolbar.add(new ContextHelpAction(debugExecutor.getHelpId()));

    DefaultActionGroup topToolbar = new DefaultActionGroup();
    topToolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP));

    session.getDebugProcess().registerAdditionalActions(leftToolbar, topToolbar, settings);
    myUi.getOptions().setLeftToolbar(leftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.getOptions().setTopToolbar(topToolbar, ActionPlaces.DEBUGGER_TOOLBAR);

    if (myEnvironment != null) {
      initLogConsoles(myEnvironment.getRunProfile(), myRunContentDescriptor, myConsole);
    }
  }

  private static void attachViewToSession(@NotNull XDebugSessionImpl session, @Nullable XDebugView view) {
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
        removeContent(DebuggerContentInfo.VARIABLES_CONTENT);
        removeContent(DebuggerContentInfo.WATCHES_CONTENT);
        addVariablesAndWatches(mySession);
        attachViewToSession(mySession, myViews.get(DebuggerContentInfo.VARIABLES_CONTENT));
        attachViewToSession(mySession, myViews.get(DebuggerContentInfo.WATCHES_CONTENT));
        myUi.selectAndFocus(myUi.findContent(DebuggerContentInfo.VARIABLES_CONTENT), true, false);
        rebuildViews();
      }
    }
  }

  public static void showWatchesView(@NotNull XDebugSessionImpl session) {
    XDebugSessionTab tab = session.getSessionTab();
    if (tab != null) {
      showView(session, tab.getWatchesContentId());
    }
  }

  public static void showFramesView(@NotNull XDebugSessionImpl session) {
    showView(session, DebuggerContentInfo.FRAME_CONTENT);
  }

  private static void showView(@NotNull XDebugSessionImpl session, String viewId) {
    XDebugSessionTab tab = session.getSessionTab();
    if (tab != null) {
      tab.toFront(false, null);
      // restore watches tab if minimized
      tab.restoreContent(viewId);

      JComponent component = tab.getUi().getComponent();
      if (component instanceof DataProvider) {
        RunnerContentUi ui = RunnerContentUi.KEY.getData(((DataProvider)component));
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
        RunContentManager manager = ExecutionManager.getInstance(myProject).getContentManager();
        ToolWindow toolWindow = manager.getToolWindowByDescriptor(myRunContentDescriptor);
        if (toolWindow != null) {
          if (!toolWindow.isVisible()) {
            toolWindow.show(() -> {
              if (onShowCallback != null) {
                onShowCallback.run();
              }
              myRebuildWatchesRunnable.run();
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

  @NotNull
  private String getWatchesContentId() {
    return myWatchesInVariables ? DebuggerContentInfo.VARIABLES_CONTENT : DebuggerContentInfo.WATCHES_CONTENT;
  }

  private void registerView(String contentId, @NotNull XDebugView view) {
    myViews.put(contentId, view);
    Disposer.register(myRunContentDescriptor, view);
  }

  private void removeContent(String contentId) {
    restoreContent(contentId); //findContent returns null if content is minimized
    myUi.removeContent(myUi.findContent(contentId), true);
    XDebugView view = myViews.remove(contentId);
    if (view != null) {
      Disposer.dispose(view);
    }
  }

  private void restoreContent(String contentId) {
    JComponent component = myUi.getComponent();
    if (component instanceof DataProvider) {
      RunnerContentUi ui = RunnerContentUi.KEY.getData(((DataProvider)component));
      if (ui != null) {
        ui.restoreContent(contentId);
      }
    }
  }
}
