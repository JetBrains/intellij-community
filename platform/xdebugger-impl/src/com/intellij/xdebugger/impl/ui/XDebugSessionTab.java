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
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.execution.ui.layout.LayoutAttractionPolicy;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XDebugViewBase;
import com.intellij.xdebugger.impl.frame.XFramesView;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class XDebugSessionTab extends DebuggerSessionTabBase {
  private final String mySessionName;
  private final RunnerLayoutUi myUi;
  private XWatchesView myWatchesView;
  private final List<XDebugViewBase> myViews = new ArrayList<XDebugViewBase>();

  public XDebugSessionTab(@NotNull final Project project, @NotNull final String sessionName) {
    super(project);
    mySessionName = sessionName;

    myUi = RunnerLayoutUi.Factory.getInstance(project).create("Debug", "unknown!", sessionName, this);
    myUi.getDefaults()
      .initTabDefaults(0, XDebuggerBundle.message("xdebugger.debugger.tab.title"), null)
      .initFocusContent(DebuggerContentInfo.FRAME_CONTENT, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION)
      .initFocusContent(DebuggerContentInfo.CONSOLE_CONTENT, LayoutViewOptions.STARTUP, new LayoutAttractionPolicy.FocusOnce(false));
  }

  private static ActionGroup getActionGroup(final String id) {
    return (ActionGroup)ActionManager.getInstance().getAction(id);
  }

  private Content createConsoleContent() {
    Content result = myUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, myConsole.getComponent(),
                                        XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                                        XDebuggerUIConstants.CONSOLE_TAB_ICON,
                                        myConsole.getPreferredFocusableComponent());
    result.setCloseable(false);
    return result;
  }

  private Content createVariablesContent(final XDebugSession session) {
    final XVariablesView variablesView = new XVariablesView(session, this);
    myViews.add(variablesView);
    Content result = myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, variablesView.getPanel(),
                                        XDebuggerBundle.message("debugger.session.tab.variables.title"),
                                        XDebuggerUIConstants.VARIABLES_TAB_ICON, null);
    result.setCloseable(false);
    return result;
  }

  private Content createWatchesContent(final XDebugSession session, final XDebugSessionData sessionData) {
    myWatchesView = new XWatchesView(session, this, sessionData);
    myViews.add(myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getMainPanel(),
                                         XDebuggerBundle.message("debugger.session.tab.watches.title"), XDebuggerUIConstants.WATCHES_TAB_ICON, null);
    watchesContent.setCloseable(false);

    ActionGroup group = getActionGroup(XDebuggerActions.WATCHES_TREE_TOOLBAR_GROUP);
    watchesContent.setActions(group, ActionPlaces.DEBUGGER_TOOLBAR, myWatchesView.getTree());
    return watchesContent;
  }

  private Content createFramesContent(final XDebugSession session) {
    final XFramesView framesView = new XFramesView(session, this);
    myViews.add(framesView);
    Content framesContent = myUi.createContent(DebuggerContentInfo.FRAME_CONTENT, framesView.getMainPanel(),
                                               XDebuggerBundle.message("debugger.session.tab.frames.title"), XDebuggerUIConstants.FRAMES_TAB_ICON, null);
    framesContent.setCloseable(false);

    final DefaultActionGroup framesGroup = new DefaultActionGroup();

    CommonActionsManager actionsManager = CommonActionsManager.getInstance();
    framesGroup.add(actionsManager.createPrevOccurenceAction(framesView.getFramesList()));
    framesGroup.add(actionsManager.createNextOccurenceAction(framesView.getFramesList()));

    framesContent.setActions(framesGroup, ActionPlaces.DEBUGGER_TOOLBAR, framesView.getFramesList());
    return framesContent;
  }

  public ExecutionConsole getConsole() {
    return myConsole;
  }

  public void rebuildViews() {
    for (XDebugViewBase view : myViews) {
      view.rebuildView();
    }
  }

  public XWatchesView getWatchesView() {
    return myWatchesView;
  }

  public RunContentDescriptor attachToSession(final @NotNull XDebugSession session, final @Nullable ProgramRunner runner,
                                              final @Nullable ExecutionEnvironment env,
                                              final @NotNull XDebugSessionData sessionData, ConsoleView consoleView) {
    final XDebugProcess debugProcess = session.getDebugProcess();
    ProcessHandler processHandler = debugProcess.getProcessHandler();
    myConsole = consoleView;
    myRunContentDescriptor = new RunContentDescriptor(myConsole, processHandler, myUi.getComponent(), mySessionName);

    myUi.addContent(createFramesContent(session), 0, PlaceInGrid.left, false);
    myUi.addContent(createVariablesContent(session), 0, PlaceInGrid.center, false);
    myUi.addContent(createWatchesContent(session, sessionData), 0, PlaceInGrid.right, false);
    final Content consoleContent = createConsoleContent();
    myUi.addContent(consoleContent, 1, PlaceInGrid.bottom, false);
    attachNotificationTo(consoleContent);

    debugProcess.registerAdditionalContent(myUi);
    RunContentBuilder.addAdditionalConsoleEditorActions(myConsole, consoleContent);
    myUi.addContent(consoleContent, 0, PlaceInGrid.bottom, false);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return myRunContentDescriptor;
    }

    DefaultActionGroup leftToolbar = new DefaultActionGroup();
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    if (runner != null && env != null) {
      RestartAction restartAction = new RestartAction(executor, runner, myRunContentDescriptor.getProcessHandler(), XDebuggerUIConstants.DEBUG_AGAIN_ICON,
                                                     myRunContentDescriptor, env);
      leftToolbar.add(restartAction);
      restartAction.registerShortcut(myUi.getComponent());
    }

    leftToolbar.addAll(getActionGroup(XDebuggerActions.TOOL_WINDOW_LEFT_TOOLBAR_GROUP));

    //group.addSeparator();
    //addAction(group, DebuggerActions.EXPORT_THREADS);
    leftToolbar.addSeparator();

    leftToolbar.add(myUi.getOptions().getLayoutActions());

    leftToolbar.addSeparator();

    leftToolbar.add(PinToolwindowTabAction.getPinAction());
    leftToolbar.add(new CloseAction(executor, myRunContentDescriptor, getProject()));
    leftToolbar.add(new ContextHelpAction(executor.getHelpId()));

    DefaultActionGroup topToolbar = new DefaultActionGroup();
    topToolbar.addAll(getActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP));

    session.getDebugProcess().registerAdditionalActions(leftToolbar, topToolbar);
    myUi.getOptions().setLeftToolbar(leftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.getOptions().setTopToolbar(topToolbar, ActionPlaces.DEBUGGER_TOOLBAR);

    if (env != null) {
      final RunProfile runConfiguration = env.getRunProfile();
      registerFileMatcher(runConfiguration);
      initLogConsoles(runConfiguration, myRunContentDescriptor.getProcessHandler());
    }

    rebuildViews();

    return myRunContentDescriptor;
  }

  public RunnerLayoutUi getUi() {
    return myUi;
  }

  @Nullable
  public RunContentDescriptor getRunContentDescriptor() {
    return myRunContentDescriptor;
  }
}
