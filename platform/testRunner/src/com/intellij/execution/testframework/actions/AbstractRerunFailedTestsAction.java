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

/*
 * User: anna
 * Date: 24-Dec-2008
 */
package com.intellij.execution.testframework.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AbstractRerunFailedTestsAction extends AnAction implements AnAction.TransparentUpdate, Disposable {
  private static final List<AbstractRerunFailedTestsAction> registry = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction");
  private TestFrameworkRunningModel myModel;
  private Getter<TestFrameworkRunningModel> myModelProvider;
  protected TestConsoleProperties myConsoleProperties;
  protected ExecutionEnvironment myEnvironment;
  private final JComponent myParent;


  public AbstractRerunFailedTestsAction() {
    //We call this constructor with a little help from reflection.
    myParent = null;
  }

  protected AbstractRerunFailedTestsAction(@NotNull ComponentContainer componentContainer) {
    myParent = componentContainer.getComponent();
    registry.add(this);
    Disposer.register(componentContainer, this);
    copyFrom(ActionManager.getInstance().getAction("RerunFailedTests"));
    registerCustomShortcutSet(getShortcutSet(), myParent);
  }

  public void dispose() {
    registry.remove(this);
  }

  public void init(final TestConsoleProperties consoleProperties,
                   final ExecutionEnvironment environment) {
    myEnvironment = environment;
    myConsoleProperties = consoleProperties;
  }

  public void setModel(TestFrameworkRunningModel model) {
    myModel = model;
  }

  public void setModelProvider(Getter<TestFrameworkRunningModel> modelProvider) {
    myModelProvider = modelProvider;
  }

  @NotNull
  private AbstractRerunFailedTestsAction findActualAction() {
    if (myParent != null || registry.isEmpty())
      return this;
    List<AbstractRerunFailedTestsAction> candidates = new ArrayList<AbstractRerunFailedTestsAction>(registry);
    Collections.sort(candidates, new Comparator<AbstractRerunFailedTestsAction>() {
      @Override
      public int compare(AbstractRerunFailedTestsAction action1, AbstractRerunFailedTestsAction action2) {
        Window window1 = SwingUtilities.windowForComponent(action1.myParent);
        Window window2 = SwingUtilities.windowForComponent(action2.myParent);
        if (window1 == null)
          return 1;
        if (window2 == null)
          return -1;
        boolean showing1 = action1.myParent.isShowing();
        boolean showing2 = action2.myParent.isShowing();
        if (showing1 && !showing2)
          return -1;
        if (showing2 && !showing1)
          return 1;
        return (window1.isActive() ? -1 : 1);
      }
    });
    return candidates.get(0);
  }

  public final void update(AnActionEvent e) {
    AbstractRerunFailedTestsAction action = findActualAction();
    e.getPresentation().setEnabled(action.isActive(e));
  }

  private boolean isActive(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;
    TestFrameworkRunningModel model = getModel();
    if (model == null || model.getRoot() == null) return false;
    final List<? extends AbstractTestProxy> myAllTests = model.getRoot().getAllTests();
    final GlobalSearchScope searchScope = model.getProperties().getScope();
    for (Object test : myAllTests) {
      if (getFilter(project, searchScope).shouldAccept((AbstractTestProxy)test)) return true;
    }
    return false;
  }

  @NotNull
  protected List<AbstractTestProxy> getFailedTests(Project project) {
    TestFrameworkRunningModel model = getModel();
    final List<? extends AbstractTestProxy> myAllTests = model != null
                                                         ? model.getRoot().getAllTests()
                                                         : Collections.<AbstractTestProxy>emptyList();
    return getFilter(project, model != null ? model.getProperties().getScope() : GlobalSearchScope.allScope(project)).select(myAllTests);
  }

  @NotNull
  protected Filter getFilter(Project project, GlobalSearchScope searchScope) {
    if (TestConsoleProperties.INCLUDE_NON_STARTED_IN_RERUN_FAILED.value(myConsoleProperties)) {
      return Filter.NOT_PASSED.or(Filter.FAILED_OR_INTERRUPTED);
    }
    return Filter.FAILED_OR_INTERRUPTED;
  }

  public void actionPerformed(AnActionEvent e) {
    findActualAction().performAction();
  }

  private void performAction() {
    boolean isDebug = myConsoleProperties.isDebug();
    final MyRunProfile profile = getRunProfile();
    if (profile == null)
      return;
    try {
      final Executor executor = isDebug ? DefaultDebugExecutor.getDebugExecutorInstance() : DefaultRunExecutor.getRunExecutorInstance();
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), profile);
      assert runner != null;
      runner.execute(new ExecutionEnvironmentBuilder(myEnvironment).setRunProfile(profile).build());
    }
    catch (ExecutionException e1) {
      LOG.error(e1);
    }
    finally {
      profile.clear();
    }
  }

  @Nullable
  public MyRunProfile getRunProfile() {
    return null;
  }

  @Nullable
  public TestFrameworkRunningModel getModel() {
    if (myModel != null) {
      return myModel;
    }
    if (myModelProvider != null) {
      return myModelProvider.get();
    }
    return null;
  }

  protected static abstract class MyRunProfile extends RunConfigurationBase implements ModuleRunProfile,
                                                                                       WrappingRunConfiguration<RunConfigurationBase> {
    @Deprecated
    public RunConfigurationBase getConfiguration() {
      return getPeer();
    }

    @Override
    public RunConfigurationBase getPeer() {
      return myConfiguration;
    }

    private final RunConfigurationBase myConfiguration;

    public MyRunProfile(RunConfigurationBase configuration) {
      super(configuration.getProject(), configuration.getFactory(), ActionsBundle.message("action.RerunFailedTests.text"));
      myConfiguration = configuration;
    }

    public void clear() {
    }


    public void checkConfiguration() throws RuntimeConfigurationException {
    }

    ///////////////////////////////////Delegates
    public void readExternal(final Element element) throws InvalidDataException {
      myConfiguration.readExternal(element);
    }

    public void writeExternal(final Element element) throws WriteExternalException {
      myConfiguration.writeExternal(element);
    }

    @NotNull
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return myConfiguration.getConfigurationEditor();
    }

    @NotNull
    public ConfigurationType getType() {
      return myConfiguration.getType();
    }

    public ConfigurationPerRunnerSettings createRunnerSettings(final ConfigurationInfoProvider provider) {
      return myConfiguration.createRunnerSettings(provider);
    }

    public SettingsEditor<ConfigurationPerRunnerSettings> getRunnerSettingsEditor(final ProgramRunner runner) {
      return myConfiguration.getRunnerSettingsEditor(runner);
    }

    public RunConfiguration clone() {
      return myConfiguration.clone();
    }

    public int getUniqueID() {
      return myConfiguration.getUniqueID();
    }

    public LogFileOptions getOptionsForPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
      return myConfiguration.getOptionsForPredefinedLogFile(predefinedLogFile);
    }

    public ArrayList<PredefinedLogFile> getPredefinedLogFiles() {
      return myConfiguration.getPredefinedLogFiles();
    }

    public ArrayList<LogFileOptions> getAllLogFiles() {
      return myConfiguration.getAllLogFiles();
    }

    public ArrayList<LogFileOptions> getLogFiles() {
      return myConfiguration.getLogFiles();
    }
  }
}