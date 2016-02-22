/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.impl.ViewImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.*;
import com.intellij.xdebugger.impl.ui.tree.actions.SortValuesToggleAction;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class XDebugSessionTab extends DebuggerSessionTabBase {
  public static final DataKey<XDebugSessionTab> TAB_KEY = DataKey.create("XDebugSessionTab");

  private XWatchesViewImpl myWatchesView;
  private final List<XDebugView> myViews = new ArrayList<XDebugView>();

  @Nullable
  private XDebugSessionImpl mySession;
  private XDebugSessionData mySessionData;

  private final Runnable myRebuildWatchesRunnable = new Runnable() {
    @Override
    public void run() {
      if (myWatchesView != null && myWatchesView.rebuildNeeded()) {
        myWatchesView.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED);
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
    return new XDebugSessionTab(session, icon, environment);
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
    myUi.addContent(createVariablesContent(session), 0, PlaceInGrid.center, false);
    myUi.addContent(createWatchesContent(session), 0, PlaceInGrid.right, false);

    for (XDebugView view : myViews) {
      Disposer.register(myRunContentDescriptor, view);
    }

    attachToSession(session);

    DefaultActionGroup focus = new DefaultActionGroup();
    focus.add(ActionManager.getInstance().getAction(XDebuggerActions.FOCUS_ON_BREAKPOINT));
    myUi.getOptions().setAdditionalFocusActions(focus);

    myUi.addListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        Content content = event.getContent();
        XDebugSessionImpl session = mySession;
        if (session != null && content.isSelected() && DebuggerContentInfo.WATCHES_CONTENT.equals(ViewImpl.ID.get(content))) {
          myRebuildWatchesRunnable.run();
        }
      }
    }, myRunContentDescriptor);

    rebuildViews();
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
    Disposer.register(myRunContentDescriptor, this);
    Disposer.register(myProject, myRunContentDescriptor);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
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
    final XVariablesView variablesView = new XVariablesView(session);
    myViews.add(variablesView);
    Content result = myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, variablesView.getPanel(),
                                        XDebuggerBundle.message("debugger.session.tab.variables.title"),
                                        AllIcons.Debugger.Value, null);
    result.setCloseable(false);

    ActionGroup group = getCustomizedActionGroup(XDebuggerActions.VARIABLES_TREE_TOOLBAR_GROUP);
    result.setActions(group, ActionPlaces.DEBUGGER_TOOLBAR, variablesView.getTree());
    return result;
  }

  private Content createWatchesContent(@NotNull XDebugSessionImpl session) {
    myWatchesView = new XWatchesViewImpl(session);
    myViews.add(myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getMainPanel(),
                                                XDebuggerBundle.message("debugger.session.tab.watches.title"), AllIcons.Debugger.Watches, null);
    watchesContent.setCloseable(false);
    return watchesContent;
  }

  @NotNull
  private Content createFramesContent() {
    XFramesView framesView = new XFramesView(myProject);
    myViews.add(framesView);
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, framesView.getMainPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.frames.title"), AllIcons.Debugger.Frame, null);
    framesContent.setCloseable(false);
    return framesContent;
  }

  public void rebuildViews() {
    AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> {
      for (XDebugView view : myViews) {
        view.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED);
      }
    });
  }

  public XWatchesView getWatchesView() {
    return myWatchesView;
  }

  private void attachToSession(@NotNull XDebugSessionImpl session) {
    for (XDebugView view : myViews) {
      session.addSessionListener(new XDebugViewSessionListener(view), myRunContentDescriptor);
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

  public void detachFromSession() {
    assert mySession != null;
    mySession = null;
  }

  @Nullable
  public RunContentDescriptor getRunContentDescriptor() {
    return myRunContentDescriptor;
  }

  private static class ToggleSortValuesAction extends SortValuesToggleAction {
    private final boolean myShowIcon;

    private ToggleSortValuesAction(boolean showIcon) {
      copyFrom(ActionManager.getInstance().getAction(XDebuggerActions.TOGGLE_SORT_VALUES));
      myShowIcon = showIcon;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (!myShowIcon) {
        e.getPresentation().setIcon(null);
      }
    }
  }
}
