// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.autotest;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ExecutionDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

public abstract class AbstractAutoTestManager implements PersistentStateComponent<AbstractAutoTestManager.State> {
  private static final String AUTO_TEST_MANAGER_DELAY = "auto.test.manager.delay";
  private static final int AUTO_TEST_MANAGER_DELAY_DEFAULT = 3000;
  private static final Key<ProcessListener> ON_TERMINATION_RESTARTER_KEY = Key.create("auto.test.manager.on.termination.restarter");
  private final Project myProject;
  private final Set<RunProfile> myEnabledRunProfiles = new HashSet<>();
  protected int myDelayMillis;
  private AutoTestWatcher myWatcher;

  public AbstractAutoTestManager(@NotNull Project project) {
    myProject = project;
    myDelayMillis = PropertiesComponent.getInstance(project).getInt(AUTO_TEST_MANAGER_DELAY, AUTO_TEST_MANAGER_DELAY_DEFAULT);
  }

  private static @Nullable ExecutionEnvironment getCurrentEnvironment(@NotNull RunContentDescriptor descriptor) {
    JComponent component = descriptor.getComponent();
    if (component == null) return null;
    return ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
  }

  private static void clearRestarterListener(@NotNull ProcessHandler processHandler) {
    ProcessListener restarterListener = ON_TERMINATION_RESTARTER_KEY.get(processHandler, null);
    if (restarterListener != null) {
      processHandler.removeProcessListener(restarterListener);
      ON_TERMINATION_RESTARTER_KEY.set(processHandler, null);
    }
  }

  private static void restart(@NotNull RunContentDescriptor descriptor) {
    descriptor.setActivateToolWindowWhenAdded(false);
    descriptor.setReuseToolWindowActivation(true);
    ExecutionUtil.restart(descriptor);
  }

  private static void saveConfigurationState(@NotNull State state, @NotNull RunProfile profile) {
    RunConfiguration runConfiguration = ObjectUtils.tryCast(profile, RunConfiguration.class);
    if (runConfiguration != null) {
      RunConfigurationDescriptor descriptor = new RunConfigurationDescriptor();
      descriptor.myType = runConfiguration.getType().getId();
      descriptor.myName = runConfiguration.getName();
      state.myEnabledRunConfigurations.add(descriptor);
    }
  }

  private static @NotNull List<RunConfiguration> loadConfigurations(@NotNull State state, @NotNull Project project) {
    List<RunConfiguration> configurations = new ArrayList<>();
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    List<RunConfigurationDescriptor> descriptors = ContainerUtil.notNullize(state.myEnabledRunConfigurations);
    for (RunConfigurationDescriptor descriptor : descriptors) {
      if (descriptor.myType != null && descriptor.myName != null) {
        RunnerAndConfigurationSettings settings = runManager.findConfigurationByTypeAndName(descriptor.myType,
                                                                                            descriptor.myName);
        RunConfiguration configuration = settings != null ? settings.getConfiguration() : null;
        if (configuration != null) {
          configurations.add(configuration);
        }
      }
    }
    return configurations;
  }

  protected abstract @NotNull AutoTestWatcher createWatcher(@NotNull Project project);

  private void activateWatcher() {
    if (myWatcher == null) {
      myWatcher = createWatcher(myProject);
    }
    myWatcher.activate();
  }

  private void deactivateWatcher() {
    if (myWatcher != null) {
      myWatcher.deactivate();
      myWatcher = null;
    }
  }

  void setAutoTestEnabled(@NotNull RunContentDescriptor descriptor, @NotNull ExecutionEnvironment environment, boolean enabled) {
    if (enabled) {
      myEnabledRunProfiles.add(environment.getRunProfile());
      activateWatcher();
    }
    else {
      myEnabledRunProfiles.remove(environment.getRunProfile());
      if (!hasEnabledAutoTests()) {
        deactivateWatcher();
      }
      ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler != null) {
        clearRestarterListener(processHandler);
      }
    }

    myProject.getMessageBus().syncPublisher(AutoTestListener.Companion.getTOPIC()).autoTestStatusChanged();
  }

  /**
   * Disable all enabled auto-test configurations for the project.
   */
  public void disableAllAutoTests() {
    deactivateWatcher();
    for (RunContentDescriptor descriptor : RunContentManager.getInstance(myProject).getAllDescriptors()) {
      if (!isAutoTestEnabled(descriptor)) continue;
      ProcessHandler processHandler = descriptor.getProcessHandler();
      if (processHandler != null) {
        clearRestarterListener(processHandler);
      }
    }
    myEnabledRunProfiles.clear();
    myProject.getMessageBus().syncPublisher(AutoTestListener.Companion.getTOPIC()).autoTestStatusChanged();
  }

  @ApiStatus.Internal
  public boolean hasEnabledAutoTests() {
    return ContainerUtil.exists(RunContentManager.getInstance(myProject).getAllDescriptors(), this::isAutoTestEnabled);
  }

  @ApiStatus.Internal
  public boolean isAutoTestEnabled(@NotNull RunContentDescriptor descriptor) {
    ExecutionEnvironment environment = getCurrentEnvironment(descriptor);
    return environment != null && myEnabledRunProfiles.contains(environment.getRunProfile());
  }

  /**
   * @deprecated use {@link #restartAllAutoTests(BooleanSupplier)} instead
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  protected void restartAllAutoTests(int modificationStamp) {
    restartAllAutoTests(() -> myWatcher != null);
  }

  @RequiresEdt(generateAssertion = false)
  protected void restartAllAutoTests(@NotNull BooleanSupplier isUpToDate) {
    List<RunContentDescriptor> autoTestDescriptors = RunContentManager.getInstance(myProject).getAllDescriptors().stream()
      .filter(this::isAutoTestEnabled).toList();
    for (RunContentDescriptor descriptor : autoTestDescriptors) {
      restartAutoTest(descriptor, isUpToDate);
    }
    if (autoTestDescriptors.isEmpty()) {
      deactivateWatcher();
    }
  }

  private void restartAutoTest(@NotNull RunContentDescriptor descriptor, @NotNull BooleanSupplier isUpToDate) {
    ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null && !processHandler.isProcessTerminated()) {
      scheduleRestartOnTermination(descriptor, processHandler, isUpToDate);
    }
    else {
      restart(descriptor);
    }
  }

  private void scheduleRestartOnTermination(@NotNull RunContentDescriptor descriptor,
                                            @NotNull ProcessHandler processHandler,
                                            @NotNull BooleanSupplier isUpToDate) {
    ProcessListener restarterListener = ON_TERMINATION_RESTARTER_KEY.get(processHandler);
    if (restarterListener != null) {
      clearRestarterListener(processHandler);
    }
    restarterListener = new ProcessListener() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        clearRestarterListener(processHandler);
        ApplicationManager.getApplication().invokeLater(() -> {
          if (isAutoTestEnabled(descriptor) && isUpToDate.getAsBoolean()) {
            restart(descriptor);
          }
        }, ModalityState.any());
      }
    };
    ON_TERMINATION_RESTARTER_KEY.set(processHandler, restarterListener);
    processHandler.addProcessListener(restarterListener);
  }

  int getDelay() {
    return myDelayMillis;
  }

  void setDelay(int delay) {
    PropertiesComponent.getInstance(myProject).setValue(AUTO_TEST_MANAGER_DELAY, delay, AUTO_TEST_MANAGER_DELAY_DEFAULT);
    myDelayMillis = delay;
    deactivateWatcher();
    if (hasEnabledAutoTests()) {
      activateWatcher();
    }
  }

  @Override
  public @Nullable State getState() {
    State state = new State();
    for (RunProfile profile : myEnabledRunProfiles) {
      saveConfigurationState(state, profile);
    }
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    List<RunConfiguration> configurations = loadConfigurations(state, myProject);
    myEnabledRunProfiles.clear();
    myEnabledRunProfiles.addAll(configurations);
    if (!configurations.isEmpty()) {
      activateWatcher();
    }
  }

  public static class State {
    @Tag("enabled-run-configurations")
    @XCollection
    List<AutoTestManager.RunConfigurationDescriptor> myEnabledRunConfigurations = new ArrayList<>();
  }

  @Tag("run-configuration")
  static class RunConfigurationDescriptor {
    @Attribute("type")
    String myType;

    @Attribute("name")
    String myName;
  }
}
