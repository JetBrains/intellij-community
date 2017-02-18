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
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author anna
 * @since 24-Dec-2008
 */
public class AbstractRerunFailedTestsAction extends AnAction implements AnAction.TransparentUpdate {
  private static final Logger LOG = Logger.getInstance(AbstractRerunFailedTestsAction.class);

  private TestFrameworkRunningModel myModel;
  private Getter<TestFrameworkRunningModel> myModelProvider;
  protected TestConsoleProperties myConsoleProperties;

  protected AbstractRerunFailedTestsAction(@NotNull ComponentContainer componentContainer) {
    copyFrom(ActionManager.getInstance().getAction("RerunFailedTests"));
    registerCustomShortcutSet(getShortcutSet(), componentContainer.getComponent());
  }

  public void init(TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  public void setModel(TestFrameworkRunningModel model) {
    myModel = model;
  }

  public void setModelProvider(Getter<TestFrameworkRunningModel> modelProvider) {
    myModelProvider = modelProvider;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isActive(e));
  }

  private boolean isActive(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return false;
    }

    TestFrameworkRunningModel model = getModel();
    if (model == null || model.getRoot() == null) {
      return false;
    }
    Filter filter = getFailuresFilter();
    for (AbstractTestProxy test : model.getRoot().getAllTests()) {
      //noinspection unchecked
      if (filter.shouldAccept(test)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  protected List<AbstractTestProxy> getFailedTests(@NotNull Project project) {
    TestFrameworkRunningModel model = getModel();
    if (model == null) return Collections.emptyList();
    //noinspection unchecked
    return getFilter(project, model.getProperties().getScope()).select(model.getRoot().getAllTests());
  }

  @NotNull
  protected Filter getFilter(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    return getFailuresFilter();
  }

  protected Filter<?> getFailuresFilter() {
    return getFailuresFilter(myConsoleProperties);
  }

  @TestOnly
  public static Filter<?> getFailuresFilter(TestConsoleProperties consoleProperties) {
    if (TestConsoleProperties.INCLUDE_NON_STARTED_IN_RERUN_FAILED.value(consoleProperties)) {
      return Filter.NOT_PASSED.or(Filter.FAILED_OR_INTERRUPTED).and(Filter.IGNORED.not());
    }
    return Filter.FAILED_OR_INTERRUPTED.and(Filter.IGNORED.not());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ExecutionEnvironment environment = e.getData(LangDataKeys.EXECUTION_ENVIRONMENT);
    if (environment == null) {
      return;
    }

    execute(e, environment);
  }

  void execute(@NotNull AnActionEvent e, @NotNull ExecutionEnvironment environment) {
    MyRunProfile profile = getRunProfile(environment);
    if (profile == null) {
      return;
    }

    final ExecutionEnvironmentBuilder environmentBuilder = new ExecutionEnvironmentBuilder(environment).runProfile(profile);

    final InputEvent event = e.getInputEvent();
    if (!(event instanceof MouseEvent) || !event.isShiftDown()) {
      performAction(environmentBuilder);
      return;
    }

    final LinkedHashMap<Executor, ProgramRunner> availableRunners = new LinkedHashMap<>();
    for (Executor ex : new Executor[] {DefaultRunExecutor.getRunExecutorInstance(), DefaultDebugExecutor.getDebugExecutorInstance()}) {
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(ex.getId(), profile);
      if (runner != null) {
        availableRunners.put(ex, runner);
      }
    }

    if (availableRunners.isEmpty()) {
      LOG.error(environment.getExecutor().getActionName() + " is not available now");
    }
    else if (availableRunners.size() == 1) {
      //noinspection ConstantConditions
      performAction(environmentBuilder.runner(availableRunners.get(environment.getExecutor())));
    }
    else {
      final JBList list = new JBList(availableRunners.keySet());
      list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      list.setSelectedValue(environment.getExecutor(), true);
      list.setCellRenderer(new DefaultListCellRenderer() {
        @NotNull
        @Override
        public Component getListCellRendererComponent(@NotNull JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (value instanceof Executor) {
            setText(UIUtil.removeMnemonic(((Executor)value).getStartActionText()));
            setIcon(((Executor)value).getIcon());
          }
          return component;
        }
      });
      //noinspection ConstantConditions
      JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Restart Failed Tests")
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChoosenCallback(() -> {
          final Object value = list.getSelectedValue();
          if (value instanceof Executor) {
            //noinspection ConstantConditions
            performAction(environmentBuilder.runner(availableRunners.get(value)).executor((Executor)value));
          }
        }).createPopup().showUnderneathOf(event.getComponent());
    }
  }

  private static void performAction(@NotNull ExecutionEnvironmentBuilder builder) {
    ExecutionEnvironment environment = builder.build();
    try {
      environment.getRunner().execute(environment);
    }
    catch (ExecutionException e) {
      LOG.error(e);
    }
    finally {
      ((MyRunProfile)environment.getRunProfile()).clear();
    }
  }

  @Deprecated
  public MyRunProfile getRunProfile() {
    return null;
  }

  @Nullable
  protected MyRunProfile getRunProfile(@NotNull ExecutionEnvironment environment) {
    //noinspection deprecation
    return getRunProfile();
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

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
    }

    ///////////////////////////////////Delegates
    @Override
    public void readExternal(final Element element) throws InvalidDataException {
      myConfiguration.readExternal(element);
    }

    @Override
    public void writeExternal(final Element element) throws WriteExternalException {
      myConfiguration.writeExternal(element);
    }

    @Override
    @NotNull
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return myConfiguration.getConfigurationEditor();
    }

    @Override
    @NotNull
    public ConfigurationType getType() {
      return myConfiguration.getType();
    }

    @Override
    public ConfigurationPerRunnerSettings createRunnerSettings(final ConfigurationInfoProvider provider) {
      return myConfiguration.createRunnerSettings(provider);
    }

    @Override
    public SettingsEditor<ConfigurationPerRunnerSettings> getRunnerSettingsEditor(final ProgramRunner runner) {
      return myConfiguration.getRunnerSettingsEditor(runner);
    }

    @Override
    public RunConfiguration clone() {
      return myConfiguration.clone();
    }

    @Override
    public int getUniqueID() {
      return myConfiguration.getUniqueID();
    }

    @Override
    public LogFileOptions getOptionsForPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
      return myConfiguration.getOptionsForPredefinedLogFile(predefinedLogFile);
    }

    @Override
    public ArrayList<PredefinedLogFile> getPredefinedLogFiles() {
      return myConfiguration.getPredefinedLogFiles();
    }

    @NotNull
    @Override
    public ArrayList<LogFileOptions> getAllLogFiles() {
      return myConfiguration.getAllLogFiles();
    }

    @Override
    public ArrayList<LogFileOptions> getLogFiles() {
      return myConfiguration.getLogFiles();
    }
  }

  @Override
  public boolean isDumbAware() {
    return Registry.is("dumb.aware.run.configurations");
  }
}
