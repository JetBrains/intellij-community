// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ExecutionDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.search.GlobalSearchScope;
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
import java.util.function.Supplier;

public abstract class AbstractRerunFailedTestsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(AbstractRerunFailedTestsAction.class);

  private TestFrameworkRunningModel myModel;
  private Supplier<? extends TestFrameworkRunningModel> myModelProvider;
  protected TestConsoleProperties myConsoleProperties;

  protected AbstractRerunFailedTestsAction(@NotNull ComponentContainer componentContainer) {
    ActionUtil.copyFrom(this, "RerunFailedTests");
    registerCustomShortcutSet(getShortcutSet(), componentContainer.getComponent());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public void init(TestConsoleProperties consoleProperties) {
    myConsoleProperties = consoleProperties;
  }

  public void setModel(TestFrameworkRunningModel model) {
    myModel = model;
  }

  public void setModelProvider(Getter<? extends TestFrameworkRunningModel> modelProvider) {
    myModelProvider = modelProvider;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isActive(e));
  }

  public boolean isActive(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return false;
    }

    TestFrameworkRunningModel model = getModel();
    if (model == null || model.getRoot() == null) {
      return false;
    }

    RunProfile profile = model.getProperties().getConfiguration();
    if (profile instanceof RunConfiguration && !((RunConfiguration)profile).getType().isDumbAware() && DumbService.isDumb(project)) {
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
    ExecutionEnvironment environment = e.getData(ExecutionDataKeys.EXECUTION_ENVIRONMENT);
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
      final ProgramRunner runner = ProgramRunner.getRunner(ex.getId(), profile);
      if (runner != null) {
        availableRunners.put(ex, runner);
      }
    }

    if (availableRunners.isEmpty()) {
      LOG.error(environment.getExecutor().getActionName() + " is not available now");
    }
    else if (availableRunners.size() == 1) {
      performAction(environmentBuilder.runner(availableRunners.get(environment.getExecutor())));
    }
    else {
      ArrayList<Executor> model = new ArrayList<>(availableRunners.keySet());
      JBPopupFactory.getInstance().createPopupChooserBuilder(model)
        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        .setSelectedValue(environment.getExecutor(), true)
        .setRenderer(new DefaultListCellRenderer() {
          @NotNull
          @Override
          public Component getListCellRendererComponent(@NotNull JList list,
                                                        Object value,
                                                        int index,
                                                        boolean isSelected,
                                                        boolean cellHasFocus) {
            final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof Executor) {
              setText(((Executor)value).getActionName());
              setIcon(((Executor)value).getIcon());
            }
            return component;
          }
        })
        .setTitle(TestRunnerBundle.message("popup.title.restart.failed.tests"))
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .setItemChosenCallback(value -> performAction(environmentBuilder.runner(availableRunners.get(value)).executor(value)))
        .createPopup().showUnderneathOf(event.getComponent());
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

  @Nullable
  protected MyRunProfile getRunProfile(@NotNull ExecutionEnvironment environment) {
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

  protected static abstract class MyRunProfile extends RunConfigurationBase<Element> implements ModuleRunProfile,
                                                                                                WrappingRunConfiguration<RunConfigurationBase<?>>,
                                                                                                ConsolePropertiesProvider {

    @Override
    public @NotNull RunConfigurationBase<?> getPeer() {
      return myConfiguration;
    }

    private final RunConfigurationBase<?> myConfiguration;

    public MyRunProfile(@NotNull RunConfigurationBase<?> configuration) {
      super(configuration.getProject(), configuration.getFactory(), ActionsBundle.message("action.RerunFailedTests.text"));
      myConfiguration = configuration;
    }

    public void clear() {
    }

    @Override
    public @Nullable TestConsoleProperties createTestConsoleProperties(@NotNull Executor executor) {
      return myConfiguration instanceof ConsolePropertiesProvider ?
             ((ConsolePropertiesProvider)myConfiguration).createTestConsoleProperties(executor) : null;
    }

    ///////////////////////////////////Delegates
    @Override
    public void readExternal(@NotNull final Element element) throws InvalidDataException {
      myConfiguration.readExternal(element);
    }

    @Override
    public void writeExternal(@NotNull final Element element) throws WriteExternalException {
      myConfiguration.writeExternal(element);
    }

    @Override
    @NotNull
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
      return myConfiguration.getConfigurationEditor();
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

    @NotNull
    @Override
    public List<PredefinedLogFile> getPredefinedLogFiles() {
      return myConfiguration.getPredefinedLogFiles();
    }

    @Override
    public @NotNull ArrayList<LogFileOptions> getAllLogFiles() {
      return myConfiguration.getAllLogFiles();
    }

    @NotNull
    @Override
    public List<LogFileOptions> getLogFiles() {
      return myConfiguration.getLogFiles();
    }
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
