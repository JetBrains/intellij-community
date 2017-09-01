/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.testframework;

import com.intellij.execution.DefaultExecutionTarget;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.config.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.util.List;
import java.util.Map;

/**
 * @author anna
 * @since 25-May-2007
 */
public abstract class TestConsoleProperties extends StoringPropertyContainer implements Disposable {
  public static final BooleanProperty SCROLL_TO_STACK_TRACE = new BooleanProperty("scrollToStackTrace", false);
  public static final BooleanProperty SORT_ALPHABETICALLY = new BooleanProperty("sortTestsAlphabetically", false);
  public static final BooleanProperty SORT_BY_DURATION = new BooleanProperty("sortTestsByDuration", false);
  public static final BooleanProperty SELECT_FIRST_DEFECT = new BooleanProperty("selectFirtsDefect", false);
  public static final BooleanProperty TRACK_RUNNING_TEST = new BooleanProperty("trackRunningTest", true);
  public static final BooleanProperty HIDE_IGNORED_TEST = new BooleanProperty("hideIgnoredTests", false);
  public static final BooleanProperty HIDE_PASSED_TESTS = new BooleanProperty("hidePassedTests", true);
  public static final BooleanProperty SCROLL_TO_SOURCE = new BooleanProperty("scrollToSource", false);
  public static final BooleanProperty OPEN_FAILURE_LINE = new BooleanProperty("openFailureLine", true);
  public static final BooleanProperty TRACK_CODE_COVERAGE = new BooleanProperty("trackCodeCoverage", false);
  public static final BooleanProperty SHOW_STATISTICS = new BooleanProperty("showStatistics", false);
  public static final BooleanProperty SHOW_INLINE_STATISTICS = new BooleanProperty("showInlineStatistics", true);
  public static final BooleanProperty INCLUDE_NON_STARTED_IN_RERUN_FAILED = new BooleanProperty("includeNonStarted", true);
  public static final BooleanProperty HIDE_SUCCESSFUL_CONFIG = new BooleanProperty("hideConfig", false);

  private final Project myProject;
  private final Executor myExecutor;
  private ConsoleView myConsole;
  private boolean myUsePredefinedMessageFilter = true;
  private GlobalSearchScope myScope;
  private boolean myPreservePresentableName = false;

  protected final Map<AbstractProperty, List<TestFrameworkPropertyListener>> myListeners = ContainerUtil.newHashMap();

  public TestConsoleProperties(@NotNull Storage storage, Project project, Executor executor) {
    super(storage);
    myProject = project;
    myExecutor = executor;
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  public GlobalSearchScope getScope() {
    if (myScope == null) {
      myScope = initScope();
    }
    return myScope;
  }

  @NotNull
  protected GlobalSearchScope initScope() {
    RunProfile configuration = getConfiguration();
    if (!(configuration instanceof ModuleRunProfile)) {
      return GlobalSearchScope.allScope(myProject);
    }

    Module[] modules = ((ModuleRunProfile)configuration).getModules();
    if (modules.length == 0) {
      return GlobalSearchScope.allScope(myProject);
    }

    GlobalSearchScope scope = GlobalSearchScope.EMPTY_SCOPE;
    for (Module each : modules) {
      scope = scope.uniteWith(GlobalSearchScope.moduleRuntimeScope(each, true));
    }
    return scope;
  }

  public boolean isPreservePresentableName() {
    return myPreservePresentableName;
  }

  public void setPreservePresentableName(boolean preservePresentableName) {
    myPreservePresentableName = preservePresentableName;
  }

  public <T> void addListener(@NotNull AbstractProperty<T> property, @NotNull TestFrameworkPropertyListener<T> listener) {
    List<TestFrameworkPropertyListener> listeners = myListeners.get(property);
    if (listeners == null) {
      myListeners.put(property, (listeners = ContainerUtil.newArrayList()));
    }
    listeners.add(listener);
  }

  public <T> void addListenerAndSendValue(@NotNull AbstractProperty<T> property, @NotNull TestFrameworkPropertyListener<T> listener) {
    addListener(property, listener);
    listener.onChanged(property.get(this));
  }

  public <T> void removeListener(@NotNull AbstractProperty<T> property, @NotNull TestFrameworkPropertyListener listener) {
    List<TestFrameworkPropertyListener> listeners = myListeners.get(property);
    if (listeners != null) {
      listeners.remove(listener);
    }
  }

  public Executor getExecutor() {
    return myExecutor;
  }

  public boolean isDebug() {
    return myExecutor.getId() == DefaultDebugExecutor.EXECUTOR_ID;
  }

  public boolean isPaused() {
    XDebugSession debuggerSession = XDebuggerManager.getInstance(myProject).getDebugSession(getConsole());
    return debuggerSession != null && debuggerSession.isPaused();
  }

  @Override
  protected <T> void onPropertyChanged(@NotNull AbstractProperty<T> property, T value) {
    List<TestFrameworkPropertyListener> listeners = myListeners.get(property);
    if (listeners != null) {
      for (Object o : listeners.toArray()) {
        @SuppressWarnings("unchecked") TestFrameworkPropertyListener<T> listener = (TestFrameworkPropertyListener<T>)o;
        listener.onChanged(value);
      }
    }
  }

  public void setConsole(ConsoleView console) {
    myConsole = console;
  }

  @Override
  public void dispose() {
    myListeners.clear();
  }

  public abstract RunProfile getConfiguration();

  /**
   * Allows to make console editable and disable/enable input sending in process stdin stream.
   * Normally tests shouldn't ask anything in stdin so console is view only by default.
   * <p/>
   * NB1: Process input support feature isn't fully implemented. Input text will be lost after
   * switching to any other test/suite in tests results view. It's highly not recommended to change
   * default behaviour. Please do it only in critical cases and only if you are sure that you need this feature.
   * <p/>
   * NB2: If you are using Service Messages based test runner please ensure that before each service message
   * (e.g. #teamcity[...]) you always send "\n" to the output stream.
   *
   * @return False for view-only mode and true for stdin support.
   */
  public boolean isEditable() {
    return false;
  }

  protected ExecutionConsole getConsole() {
    return myConsole;
  }

  public boolean isUsePredefinedMessageFilter() {
    return myUsePredefinedMessageFilter;
  }

  public void setUsePredefinedMessageFilter(boolean usePredefinedMessageFilter) {
    myUsePredefinedMessageFilter = usePredefinedMessageFilter;
  }

  public void appendAdditionalActions(DefaultActionGroup actionGroup, JComponent parent, TestConsoleProperties target) { }
  
  @Nullable
  protected AnAction createImportAction() {
    return null;
  }

  @NotNull
  protected ToggleBooleanProperty createIncludeNonStartedInRerun(TestConsoleProperties target) {
    String text = ExecutionBundle.message("junit.runing.info.include.non.started.in.rerun.failed.action.name");
    return new DumbAwareToggleBooleanProperty(text, null, null, target, INCLUDE_NON_STARTED_IN_RERUN_FAILED);
  }
  
  @NotNull
  protected ToggleBooleanProperty createHideSuccessfulConfig(TestConsoleProperties target) {
    String text = ExecutionBundle.message("junit.runing.info.hide.successful.config.action.name");
    setIfUndefined(HIDE_SUCCESSFUL_CONFIG, true);
    return new DumbAwareToggleBooleanProperty(text, null, null, target, HIDE_SUCCESSFUL_CONFIG);
  }

  @JdkConstants.TreeSelectionMode
  public int getSelectionMode() {
    return TreeSelectionModel.SINGLE_TREE_SELECTION;
  }
  
  @NotNull
  public ExecutionTarget getExecutionTarget() {
    return DefaultExecutionTarget.INSTANCE;
  }

  @NotNull
  public String getWindowId() {
    return isDebug() ? ToolWindowId.DEBUG : ToolWindowId.RUN;
  }
}
