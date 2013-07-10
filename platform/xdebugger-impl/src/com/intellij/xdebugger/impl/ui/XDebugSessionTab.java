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
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.tabs.PinToolwindowTabAction;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XDebugViewBase;
import com.intellij.xdebugger.impl.frame.XFramesView;
import com.intellij.xdebugger.impl.frame.XVariablesView;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.actions.SortValuesToggleAction;
import com.intellij.xdebugger.ui.XDebugLayoutCustomizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author spleaner
 */
public class XDebugSessionTab extends DebuggerSessionTabBase {
  private XWatchesView myWatchesView;
  private final List<XDebugViewBase> myViews = new ArrayList<XDebugViewBase>();

  public XDebugSessionTab(@NotNull final Project project, @NotNull final XDebugSessionImpl session, final @Nullable Icon icon,
                          ExecutionEnvironment environment, ProgramRunner runner) {
    super(project, "Debug", session.getSessionName(), GlobalSearchScope.allScope(project));
    if (environment != null) {
      setEnvironment(environment);
    }
    myConsole = session.getConsoleView();
    XDebugProcess debugProcess = session.getDebugProcess();
    myRunContentDescriptor = new RunContentDescriptor(myConsole, debugProcess.getProcessHandler(), myUi.getComponent(), mySessionName, icon);
    attachToSession(session, runner, environment, session.getSessionData(), debugProcess);
  }

  private Content createConsoleContent() {
    Content result = myUi.createContent(DebuggerContentInfo.CONSOLE_CONTENT, myConsole.getComponent(),
                                        XDebuggerBundle.message("debugger.session.tab.console.content.name"),
                                        AllIcons.Debugger.Console,
                                        myConsole.getPreferredFocusableComponent());
    result.setCloseable(false);
    return result;
  }

  private Content createVariablesContent(final XDebugSession session) {
    final XVariablesView variablesView = new XVariablesView(session, this);
    myViews.add(variablesView);
    Content result = myUi.createContent(DebuggerContentInfo.VARIABLES_CONTENT, variablesView.getPanel(),
                                        XDebuggerBundle.message("debugger.session.tab.variables.title"),
                                        AllIcons.Debugger.Value, null);
    result.setCloseable(false);

    ActionGroup group = getCustomizedActionGroup(XDebuggerActions.VARIABLES_TREE_TOOLBAR_GROUP);
    result.setActions(group, ActionPlaces.DEBUGGER_TOOLBAR, variablesView.getTree());

    return result;
  }

  private Content createWatchesContent(final XDebugSession session, final XDebugSessionData sessionData) {
    myWatchesView = new XWatchesView(session, this, sessionData);
    myViews.add(myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getMainPanel(),
                                         XDebuggerBundle.message("debugger.session.tab.watches.title"), AllIcons.Debugger.Watches, null);
    watchesContent.setCloseable(false);

    return watchesContent;
  }

  private Content createFramesContent(final XDebugSession session) {
    final XFramesView framesView = new XFramesView(session, this);
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
    for (XDebugViewBase view : myViews) {
      view.rebuildView();
    }
  }

  public XWatchesView getWatchesView() {
    return myWatchesView;
  }

  private void attachToSession(final @NotNull XDebugSession session, final @Nullable ProgramRunner runner,
                               final @Nullable ExecutionEnvironment env, final @NotNull XDebugSessionData sessionData,
                               final @NotNull XDebugProcess debugProcess) {
    myUi.addContent(createFramesContent(session), 0, PlaceInGrid.left, false);
    myUi.addContent(createVariablesContent(session), 0, PlaceInGrid.center, false);
    myUi.addContent(createWatchesContent(session, sessionData), 0, PlaceInGrid.right, false);
    XDebugLayoutCustomizer layoutCustomizer = debugProcess.getLayoutCustomizer();
    final Content consoleContent;
    if (layoutCustomizer != null) {
      consoleContent = layoutCustomizer.registerConsoleContent(myConsole, myUi);
    }
    else {
      consoleContent = createConsoleContent();
      myUi.addContent(consoleContent, 1, PlaceInGrid.bottom, false);
    }
    attachNotificationTo(consoleContent);

    debugProcess.registerAdditionalContent(myUi);
    if (layoutCustomizer != null) {
      layoutCustomizer.registerAdditionalContent(myUi);
    }
    RunContentBuilder.addAdditionalConsoleEditorActions(myConsole, consoleContent);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    DefaultActionGroup leftToolbar = new DefaultActionGroup();
    final Executor executor = DefaultDebugExecutor.getDebugExecutorInstance();
    if (runner != null && env != null) {
      RestartAction restartAction = new RestartAction(executor, runner, myRunContentDescriptor, env);
      leftToolbar.add(restartAction);
      restartAction.registerShortcut(myUi.getComponent());
    }

    leftToolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_LEFT_TOOLBAR_GROUP));

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
    settings.add(new ToggleSortValuesAction(commonSettings.length == 0));

    leftToolbar.add(settings);


    leftToolbar.addSeparator();

    leftToolbar.add(PinToolwindowTabAction.getPinAction());
    leftToolbar.add(new CloseAction(executor, myRunContentDescriptor, getProject()));
    leftToolbar.add(new ContextHelpAction(executor.getHelpId()));

    DefaultActionGroup topToolbar = new DefaultActionGroup();
    topToolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP));

    debugProcess.registerAdditionalActions(leftToolbar, topToolbar);
    myUi.getOptions().setLeftToolbar(leftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.getOptions().setTopToolbar(topToolbar, ActionPlaces.DEBUGGER_TOOLBAR);

    if (env != null) {
      final RunProfile runConfiguration = env.getRunProfile();
      registerFileMatcher(runConfiguration);
      initLogConsoles(runConfiguration, myRunContentDescriptor.getProcessHandler(), myConsole);
    }

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
