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
package com.intellij.execution.testframework.autotest;

import com.intellij.execution.*;
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
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
@State(
  name = "AutoTestManager",
  storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
public class AutoTestManager implements PersistentStateComponent<AutoTestManager.State> {
  private static final String AUTO_TEST_MANAGER_DELAY = "auto.test.manager.delay";
  private static final int AUTO_TEST_MANAGER_DELAY_DEFAULT = 3000;

  private static final Key<ProcessListener> ON_TERMINATION_RESTARTER_KEY = Key.create("auto.test.manager.on.termination.restarter");

  private final Project myProject;
  private int myDelayMillis;
  private DelayedDocumentWatcher myDocumentWatcher;
  private final Set<RunProfile> myEnabledRunProfiles = ContainerUtil.newHashSet();

  @NotNull
  public static AutoTestManager getInstance(Project project) {
    return ServiceManager.getService(project, AutoTestManager.class);
  }

  public AutoTestManager(@NotNull Project project) {
    myProject = project;
    myDelayMillis = PropertiesComponent.getInstance(project).getInt(AUTO_TEST_MANAGER_DELAY, AUTO_TEST_MANAGER_DELAY_DEFAULT);
    myDocumentWatcher = createWatcher();
  }

  @NotNull
  private DelayedDocumentWatcher createWatcher() {
    return new DelayedDocumentWatcher(myProject, myDelayMillis, new Consumer<Integer>() {
      @Override
      public void consume(Integer modificationStamp) {
        restartAllAutoTests(modificationStamp);
      }
    }, new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile file) {
        if (ScratchFileService.getInstance().getRootType(file) != null) {
          return false;
        }
        // Vladimir.Krivosheev - I don't know, why AutoTestManager checks it, but old behavior is preserved
        return FileEditorManager.getInstance(myProject).isFileOpen(file);
      }
    });
  }

  public void setAutoTestEnabled(@NotNull RunContentDescriptor descriptor, @NotNull ExecutionEnvironment environment, boolean enabled) {
    Content content = descriptor.getAttachedContent();
    if (content != null) {
      if (enabled) {
        myEnabledRunProfiles.add(environment.getRunProfile());
        myDocumentWatcher.activate();
      }
      else {
        myEnabledRunProfiles.remove(environment.getRunProfile());
        if (!hasEnabledAutoTests()) {
          myDocumentWatcher.deactivate();
        }
        ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null) {
          clearRestarterListener(processHandler);
        }
      }
    }
  }

  private boolean hasEnabledAutoTests() {
    RunContentManager contentManager = ExecutionManager.getInstance(myProject).getContentManager();
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

  @Nullable
  private static ExecutionEnvironment getCurrentEnvironment(@NotNull Content content) {
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

  private void restartAllAutoTests(int modificationStamp) {
    RunContentManager contentManager = ExecutionManager.getInstance(myProject).getContentManager();
    boolean active = false;
    for (RunContentDescriptor descriptor : contentManager.getAllDescriptors()) {
      if (isAutoTestEnabledForDescriptor(descriptor)) {
        restartAutoTest(descriptor, modificationStamp, myDocumentWatcher);
        active = true;
      }
    }
    if (!active) {
      myDocumentWatcher.deactivate();
    }
  }

  private void restartAutoTest(@NotNull RunContentDescriptor descriptor,
                               int modificationStamp,
                               @NotNull DelayedDocumentWatcher documentWatcher) {
    ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null && !processHandler.isProcessTerminated()) {
      scheduleRestartOnTermination(descriptor, processHandler, modificationStamp, documentWatcher);
    }
    else {
      restart(descriptor);
    }
  }

  private void scheduleRestartOnTermination(@NotNull final RunContentDescriptor descriptor,
                                            @NotNull final ProcessHandler processHandler,
                                            final int modificationStamp,
                                            @NotNull final DelayedDocumentWatcher documentWatcher) {
    ProcessListener restarterListener = ON_TERMINATION_RESTARTER_KEY.get(processHandler);
    if (restarterListener != null) {
      clearRestarterListener(processHandler);
    }
    restarterListener = new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        clearRestarterListener(processHandler);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (isAutoTestEnabledForDescriptor(descriptor) && documentWatcher.isUpToDate(modificationStamp)) {
              restart(descriptor);
            }
          }
        }, ModalityState.any());
      }
    };
    ON_TERMINATION_RESTARTER_KEY.set(processHandler, restarterListener);
    processHandler.addProcessListener(restarterListener);
  }

  private static void restart(@NotNull RunContentDescriptor descriptor) {
    descriptor.setActivateToolWindowWhenAdded(false);
    descriptor.setReuseToolWindowActivation(true);
    ExecutionUtil.restart(descriptor);
  }

  int getDelay() {
    return myDelayMillis;
  }

  void setDelay(int delay) {
    myDelayMillis = delay;
    myDocumentWatcher.deactivate();
    myDocumentWatcher = createWatcher();
    if (hasEnabledAutoTests()) {
      myDocumentWatcher.activate();
    }
    PropertiesComponent.getInstance(myProject).setValue(AUTO_TEST_MANAGER_DELAY, myDelayMillis, AUTO_TEST_MANAGER_DELAY_DEFAULT);
  }

  @Nullable
  @Override
  public State getState() {
    State state = new State();
    for (RunProfile profile : myEnabledRunProfiles) {
      RunConfiguration runConfiguration = ObjectUtils.tryCast(profile, RunConfiguration.class);
      if (runConfiguration != null) {
        RunConfigurationDescriptor descriptor = new RunConfigurationDescriptor();
        descriptor.myType = runConfiguration.getType().getId();
        descriptor.myName = runConfiguration.getName();
        state.myEnabledRunConfigurations.add(descriptor);
      }
    }
    return state;
  }

  @Override
  public void loadState(State state) {
    List<RunConfiguration> configurations = ContainerUtil.newArrayList();
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myProject);
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
    myEnabledRunProfiles.clear();
    myEnabledRunProfiles.addAll(configurations);
  }

  static class State {
    @Tag("enabled-run-configurations")
    @AbstractCollection(surroundWithTag = false)
    List<RunConfigurationDescriptor> myEnabledRunConfigurations = ContainerUtil.newArrayList();
  }

  @Tag("run-configuration")
  static class RunConfigurationDescriptor {
    @Attribute("type")
    String myType;

    @Attribute("name")
    String myName;
  }
}
