// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.autotest;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.Content;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class AbstractAutoTestManager implements PersistentStateComponent<AbstractAutoTestManager.State> {
  protected static final String AUTO_TEST_MANAGER_DELAY = "auto.test.manager.delay";
  protected static final int AUTO_TEST_MANAGER_DELAY_DEFAULT = 3000;
  private static final Key<ProcessListener> ON_TERMINATION_RESTARTER_KEY = Key.create("auto.test.manager.on.termination.restarter");
  private final Project myProject;
  private final Set<RunProfile> myEnabledRunProfiles = new HashSet<>();
  protected int myDelayMillis;
  private AutoTestWatcher myWatcher;

  public AbstractAutoTestManager(@NotNull Project project) {
    myProject = project;
    myDelayMillis = PropertiesComponent.getInstance(project).getInt(AUTO_TEST_MANAGER_DELAY, AUTO_TEST_MANAGER_DELAY_DEFAULT);
    myWatcher = createWatcher(project);
  }

  private static @Nullable ExecutionEnvironment getCurrentEnvironment(@NotNull Content content) {
    JComponent component = content.getComponent();
    if (component == null) {
      return null;
    }
    return LangDataKeys.EXECUTION_ENVIRONMENT.getData(DataManager.getInstance().getDataContext(component));
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

  public static void saveConfigurationState(State state, RunProfile profile) {
    RunConfiguration runConfiguration = ObjectUtils.tryCast(profile, RunConfiguration.class);
    if (runConfiguration != null) {
      RunConfigurationDescriptor descriptor = new RunConfigurationDescriptor();
      descriptor.myType = runConfiguration.getType().getId();
      descriptor.myName = runConfiguration.getName();
      state.myEnabledRunConfigurations.add(descriptor);
    }
  }

  public static List<RunConfiguration> loadConfigurations(State state, Project project) {
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

  public void setAutoTestEnabled(@NotNull RunContentDescriptor descriptor, @NotNull ExecutionEnvironment environment, boolean enabled) {
    Content content = descriptor.getAttachedContent();
    if (content != null) {
      if (enabled) {
        myEnabledRunProfiles.add(environment.getRunProfile());
        myWatcher.activate();
      }
      else {
        myEnabledRunProfiles.remove(environment.getRunProfile());
        if (!hasEnabledAutoTests()) {
          myWatcher.deactivate();
        }
        ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null) {
          clearRestarterListener(processHandler);
        }
      }
    }
  }

  private boolean hasEnabledAutoTests() {
    RunContentManager contentManager = RunContentManager.getInstance(myProject);
    for (RunContentDescriptor descriptor : contentManager.getAllDescriptors()) {
      if (isAutoTestEnabledForDescriptor(descriptor)) {
        return true;
      }
    }
    return false;
  }

  public boolean isAutoTestEnabled(@NotNull RunContentDescriptor descriptor) {
    return isAutoTestEnabledForDescriptor(descriptor);
  }

  private boolean isAutoTestEnabledForDescriptor(@NotNull RunContentDescriptor descriptor) {
    Content content = descriptor.getAttachedContent();
    if (content != null) {
      ExecutionEnvironment environment = getCurrentEnvironment(content);
      return environment != null && myEnabledRunProfiles.contains(environment.getRunProfile());
    }
    return false;
  }

  protected void restartAllAutoTests(int modificationStamp) {
    RunContentManager contentManager = RunContentManager.getInstance(myProject);
    boolean active = false;
    for (RunContentDescriptor descriptor : contentManager.getAllDescriptors()) {
      if (isAutoTestEnabledForDescriptor(descriptor)) {
        restartAutoTest(descriptor, modificationStamp, myWatcher);
        active = true;
      }
    }
    if (!active) {
      myWatcher.deactivate();
    }
  }

  private void restartAutoTest(@NotNull RunContentDescriptor descriptor,
                               int modificationStamp,
                               @NotNull AutoTestWatcher documentWatcher) {
    ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null && !processHandler.isProcessTerminated()) {
      scheduleRestartOnTermination(descriptor, processHandler, modificationStamp, documentWatcher);
    }
    else {
      restart(descriptor);
    }
  }

  private void scheduleRestartOnTermination(final @NotNull RunContentDescriptor descriptor,
                                            final @NotNull ProcessHandler processHandler,
                                            final int modificationStamp,
                                            final @NotNull AutoTestWatcher watcher) {
    ProcessListener restarterListener = ON_TERMINATION_RESTARTER_KEY.get(processHandler);
    if (restarterListener != null) {
      clearRestarterListener(processHandler);
    }
    restarterListener = new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        clearRestarterListener(processHandler);
        ApplicationManager.getApplication().invokeLater(() -> {
          if (isAutoTestEnabledForDescriptor(descriptor) && watcher.isUpToDate(modificationStamp)) {
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
    myDelayMillis = delay;
    myWatcher.deactivate();
    myWatcher = createWatcher(myProject);
    if (hasEnabledAutoTests()) {
      myWatcher.activate();
    }
    PropertiesComponent.getInstance(myProject).setValue(AUTO_TEST_MANAGER_DELAY, myDelayMillis, AUTO_TEST_MANAGER_DELAY_DEFAULT);
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
      myWatcher.activate();
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
