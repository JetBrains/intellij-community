/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.execution.ui.layout.impl.ViewImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.xdebugger.XDebugProcess;
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

/**
 * @author spleaner
 */
public class XDebugSessionTab extends DebuggerSessionTabBase {
  private XWatchesViewImpl myWatchesView;
  private final List<XDebugView> myViews = new ArrayList<XDebugView>();

  public XDebugSessionTab(@NotNull Project project,
                          @NotNull XDebugSessionImpl session,
                          @Nullable Icon icon,
                          @Nullable ExecutionEnvironment environment,
                          @Nullable ProgramRunner runner,
                          @Nullable RunContentDescriptor contentToReuse) {
    super(project, "Debug", session.getSessionName(), GlobalSearchScope.allScope(project));
    if (environment != null) {
      setEnvironment(environment);
    }
    myConsole = session.getConsoleView();
    XDebugProcess debugProcess = session.getDebugProcess();
    myRunContentDescriptor = new RunContentDescriptor(myConsole, debugProcess.getProcessHandler(), myUi.getComponent(), mySessionName, icon);
    if (contentToReuse != null && contentToReuse.isReuseToolWindowActivation()) {
      myRunContentDescriptor.setActivateToolWindowWhenAdded(contentToReuse.isActivateToolWindowWhenAdded());
    }
    attachToSession(session, runner, environment, session.getSessionData(), debugProcess);
  }

  private Content createVariablesContent(final XDebugSession session) {
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

  private Content createWatchesContent(final XDebugSessionImpl session, final XDebugSessionData sessionData) {
    myWatchesView = new XWatchesViewImpl(session);
    myViews.add(myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getMainPanel(),
                                         XDebuggerBundle.message("debugger.session.tab.watches.title"), AllIcons.Debugger.Watches, null);
    watchesContent.setCloseable(false);

    return watchesContent;
  }

  private Content createFramesContent(final XDebugSession session) {
    final XFramesView framesView = new XFramesView(session);
    myViews.add(framesView);
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, framesView.getMainPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.frames.title"), AllIcons.Debugger.Frame, null);
    framesContent.setCloseable(false);

    return framesContent;
  }

  public ExecutionConsole getConsole() {
    return myConsole;
  }

  public void rebuildViews() {
    AppUIUtil.invokeLaterIfProjectAlive(getProject(), new Runnable() {
      @Override
      public void run() {
        for (XDebugView view : myViews) {
          view.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED);
        }
      }
    });
  }

  public XWatchesView getWatchesView() {
    return myWatchesView;
  }

  private void attachToSession(final @NotNull XDebugSessionImpl session, final @Nullable ProgramRunner runner,
                               final @Nullable ExecutionEnvironment environment, final @NotNull XDebugSessionData sessionData,
                               final @NotNull XDebugProcess debugProcess) {
    myUi.addContent(createFramesContent(session), 0, PlaceInGrid.left, false);
    myUi.addContent(createVariablesContent(session), 0, PlaceInGrid.center, false);
    myUi.addContent(createWatchesContent(session, sessionData), 0, PlaceInGrid.right, false);
    for (XDebugView view : myViews) {
      Disposer.register(this, view);
      session.addSessionListener(new XDebugViewSessionListener(view, getProject()), this);
    }

    myUi.getContentManager().addDataProvider(new DataProvider() {
      @Nullable
      @Override
      public Object getData(@NonNls String dataId) {
        if (XWatchesView.DATA_KEY.is(dataId)) {
          return myWatchesView;
        }
        if (LangDataKeys.CONSOLE_VIEW.is(dataId)) {
          return session.getConsoleView();
        }
        if (XDebugSessionData.DATA_KEY.is(dataId)) {
          return sessionData;
        }
        return null;
      }
    });
    XDebugTabLayouter layouter = debugProcess.createTabLayouter();
    Content consoleContent = layouter.registerConsoleContent(myUi, myConsole);
    attachNotificationTo(consoleContent);

    layouter.registerAdditionalContent(myUi);
    RunContentBuilder.addAdditionalConsoleEditorActions(myConsole, consoleContent);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    DefaultActionGroup leftToolbar = new DefaultActionGroup();
    final Executor debugExecutor = DefaultDebugExecutor.getDebugExecutorInstance();
    final Executor executor = environment != null ? environment.getExecutor() : debugExecutor;
    if (runner != null && environment != null) {
      RestartAction restartAction = new RestartAction(executor, runner, myRunContentDescriptor, environment);
      leftToolbar.add(restartAction);
      restartAction.registerShortcut(myUi.getComponent());

      List<AnAction> additionalRestartActions = session.getRestartActions();
      leftToolbar.addAll(additionalRestartActions);
      if (!additionalRestartActions.isEmpty()) leftToolbar.addSeparator();
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
    final AnAction commonSettingsList = myUi.getOptions().getSettingsActions();

    final DefaultActionGroup settings = new DefaultActionGroup("DebuggerSettings", commonSettings.length > 0) {
      @Override
      public void update(AnActionEvent e) {
        e.getPresentation().setText(ActionsBundle.message("group.XDebugger.settings.text"));
        e.getPresentation().setIcon(commonSettingsList.getTemplatePresentation().getIcon());
      }

      @Override
      public boolean isDumbAware() {
        return true;
      }
    };
    for (AnAction each : commonSettings) {
      settings.add(each);
    }
    if (commonSettings.length > 0) {
      settings.addSeparator();
    }
    if (!debugProcess.isValuesCustomSorted()) {
      settings.add(new ToggleSortValuesAction(commonSettings.length == 0));
    }

    leftToolbar.add(settings);

    leftToolbar.addSeparator();

    leftToolbar.add(PinToolwindowTabAction.getPinAction());
    leftToolbar.add(new CloseAction(executor, myRunContentDescriptor, getProject()));
    leftToolbar.add(new ContextHelpAction(debugExecutor.getHelpId()));

    DefaultActionGroup topToolbar = new DefaultActionGroup();
    topToolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP));

    debugProcess.registerAdditionalActions(leftToolbar, topToolbar);
    myUi.getOptions().setLeftToolbar(leftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.getOptions().setTopToolbar(topToolbar, ActionPlaces.DEBUGGER_TOOLBAR);

    if (environment != null) {
      final RunProfile runConfiguration = environment.getRunProfile();
      registerFileMatcher(runConfiguration);
      initLogConsoles(runConfiguration, myRunContentDescriptor.getProcessHandler(), myConsole);
    }

    final DefaultActionGroup focus = new DefaultActionGroup();
    focus.add(ActionManager.getInstance().getAction(XDebuggerActions.FOCUS_ON_BREAKPOINT));
    myUi.getOptions().setAdditionalFocusActions(focus);

    myUi.addListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        Content content = event.getContent();
        if (content.isSelected() && DebuggerContentInfo.WATCHES_CONTENT.equals(content.getUserData(ViewImpl.ID))) {
          if (myWatchesView.rebuildNeeded()) {
            myWatchesView.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED);
          }
        }
      }
    }, this);

    rebuildViews();
  }


  @Override
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
    public void update(AnActionEvent e) {
      super.update(e);
      if (!myShowIcon) {
        e.getPresentation().setIcon(null);
      }
    }
  }
}