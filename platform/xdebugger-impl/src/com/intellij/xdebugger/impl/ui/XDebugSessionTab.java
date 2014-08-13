/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
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
public class XDebugSessionTab extends DebuggerSessionTabBase implements DataProvider {
  private static final DataKey<XDebugSessionTab> TAB_KEY = DataKey.create("XDebugSessionTab");
  public static final DataKey<XDebugSession> SESSION_KEY = DataKey.create("XDebugSessionTab.XDebugSession");

  private XWatchesViewImpl myWatchesView;
  private final List<XDebugView> myViews = new ArrayList<XDebugView>();

  private XDebugSessionImpl session;

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
          oldTab.attachToSession();
          return oldTab;
        }
      }
    }
    return new XDebugSessionTab(session, icon, environment);
  }

  private XDebugSessionTab(@NotNull XDebugSessionImpl session,
                           @Nullable Icon icon,
                           @Nullable ExecutionEnvironment environment) {
    super(session.getProject(), "Debug", session.getSessionName(), GlobalSearchScope.allScope(session.getProject()));

    setSession(session, environment, icon);

    myUi.addContent(createFramesContent(), 0, PlaceInGrid.left, false);
    myUi.addContent(createVariablesContent(), 0, PlaceInGrid.center, false);
    myUi.addContent(createWatchesContent(), 0, PlaceInGrid.right, false);

    for (XDebugView view : myViews) {
      Disposer.register(this, view);
    }

    attachToSession();

    myUi.getContentManager().addDataProvider(this);

    DefaultActionGroup focus = new DefaultActionGroup();
    focus.add(ActionManager.getInstance().getAction(XDebuggerActions.FOCUS_ON_BREAKPOINT));
    myUi.getOptions().setAdditionalFocusActions(focus);

    myUi.addListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        Content content = event.getContent();
        XDebugSessionImpl session = XDebugSessionTab.this.session;
        if (session != null && content.isSelected() && DebuggerContentInfo.WATCHES_CONTENT.equals(ViewImpl.ID.get(content))) {
          if (myWatchesView.rebuildNeeded()) {
            myWatchesView.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED, session);
          }
        }
      }
    }, this);

    rebuildViews();
  }

  private void setSession(@NotNull XDebugSessionImpl session, @Nullable ExecutionEnvironment environment, @Nullable Icon icon) {
    if (environment != null) {
      setEnvironment(environment);
    }

    this.session = session;
    myConsole = session.getConsoleView();
    myRunContentDescriptor = new RunContentDescriptor(myConsole, session.getDebugProcess().getProcessHandler(), myUi.getComponent(), session.getSessionName(), icon);
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
    else if (LangDataKeys.RUN_PROFILE.is(dataId)) {
      ExecutionEnvironment environment = getEnvironment();
      return environment == null ? null : environment.getRunProfile();
    }
    else if (LangDataKeys.EXECUTION_ENVIRONMENT.is(dataId)) {
      return getEnvironment();
    }

    if (session != null) {
      if (SESSION_KEY.is(dataId)) {
        return session;
      }
      else if (LangDataKeys.CONSOLE_VIEW.is(dataId)) {
        return session.getConsoleView();
      }
      else if (XDebugSessionData.DATA_KEY.is(dataId)) {
        return session.getSessionData();
      }
    }

    return null;
  }

  private Content createVariablesContent() {
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

  private Content createWatchesContent() {
    myWatchesView = new XWatchesViewImpl(session);
    myViews.add(myWatchesView);
    Content watchesContent = myUi.createContent(DebuggerContentInfo.WATCHES_CONTENT, myWatchesView.getMainPanel(),
                                                XDebuggerBundle.message("debugger.session.tab.watches.title"), AllIcons.Debugger.Watches, null);
    watchesContent.setCloseable(false);
    return watchesContent;
  }

  @NotNull
  private Content createFramesContent() {
    XFramesView framesView = new XFramesView(getProject());
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
          if (session != null) {
            view.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED, session);
          }
        }
      }
    });
  }

  public XWatchesView getWatchesView() {
    return myWatchesView;
  }

  private void attachToSession() {
    for (XDebugView view : myViews) {
      session.addSessionListener(new XDebugViewSessionListener(view, session), this);
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
    ExecutionEnvironment environment = getEnvironment();
    if (environment != null) {
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
    if (!session.getDebugProcess().isValuesCustomSorted()) {
      settings.add(new ToggleSortValuesAction(commonSettings.length == 0));
    }

    leftToolbar.add(settings);

    leftToolbar.addSeparator();

    leftToolbar.add(PinToolwindowTabAction.getPinAction());
    leftToolbar.add(new CloseAction(environment != null ? environment.getExecutor() : debugExecutor, myRunContentDescriptor, getProject()));
    leftToolbar.add(new ContextHelpAction(debugExecutor.getHelpId()));

    DefaultActionGroup topToolbar = new DefaultActionGroup();
    topToolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_GROUP));

    session.getDebugProcess().registerAdditionalActions(leftToolbar, topToolbar);
    myUi.getOptions().setLeftToolbar(leftToolbar, ActionPlaces.DEBUGGER_TOOLBAR);
    myUi.getOptions().setTopToolbar(topToolbar, ActionPlaces.DEBUGGER_TOOLBAR);

    if (environment != null) {
      RunProfile runConfiguration = environment.getRunProfile();
      registerFileMatcher(runConfiguration);
      initLogConsoles(runConfiguration, myRunContentDescriptor.getProcessHandler(), myConsole);
    }
  }

  public void detachFromSession() {
    assert session != null;
    session = null;
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