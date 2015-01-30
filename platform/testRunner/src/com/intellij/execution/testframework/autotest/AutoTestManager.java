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
package com.intellij.execution.testframework.autotest;

import com.intellij.execution.DelayedDocumentWatcher;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

/**
 * @author yole
 */
public class AutoTestManager {
  static final Key<Boolean> AUTOTESTABLE = Key.create("auto.test.manager.supported");
  public static final String AUTO_TEST_MANAGER_DELAY = "auto.test.manager.delay";
  private static final Key<ProcessListener> ON_TERMINATION_RESTARTER_KEY = Key.create("auto.test.manager.on.termination.restarter");

  private final Project myProject;

  private int myDelay;
  private DelayedDocumentWatcher myDocumentWatcher;

  // accessed only from EDT
  private final Set<Content> myEnabledDescriptors = Collections.newSetFromMap(new WeakHashMap<Content, Boolean>());

  public static AutoTestManager getInstance(Project project) {
    return ServiceManager.getService(project, AutoTestManager.class);
  }

  public AutoTestManager(Project project) {
    myProject = project;
    myDelay = PropertiesComponent.getInstance(myProject).getOrInitInt(AUTO_TEST_MANAGER_DELAY, 3000);
    myDocumentWatcher = createWatcher();
  }

  private DelayedDocumentWatcher createWatcher() {
    return new DelayedDocumentWatcher(myProject, myDelay, new Consumer<Integer>() {
      @Override
      public void consume(Integer modificationStamp) {
        for (Content content : myEnabledDescriptors) {
          runAutoTest(content, modificationStamp, myDocumentWatcher);
        }
      }
    }, new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile file) {
        // Vladimir.Krivosheev - I don't know, why AutoTestManager checks it, but old behavior is preserved
        return FileEditorManager.getInstance(myProject).isFileOpen(file);
      }
    });
  }

  public void setAutoTestEnabled(@NotNull RunContentDescriptor descriptor, boolean enabled) {
    Content content = descriptor.getAttachedContent();
    if (enabled) {
      myEnabledDescriptors.add(content);
      myDocumentWatcher.activate();
    }
    else {
      clearRestarterListener(descriptor.getProcessHandler());
      myEnabledDescriptors.remove(content);
      if (myEnabledDescriptors.isEmpty()) {
        myDocumentWatcher.deactivate();
      }
    }
  }

  private static void clearRestarterListener(@Nullable ProcessHandler processHandler) {
    if (processHandler != null) {
      ProcessListener restarterListener = ON_TERMINATION_RESTARTER_KEY.get(processHandler, null);
      if (restarterListener != null) {
        processHandler.removeProcessListener(restarterListener);
        ON_TERMINATION_RESTARTER_KEY.set(processHandler, null);
      }
    }
  }

  public boolean isAutoTestEnabled(RunContentDescriptor descriptor) {
    return myEnabledDescriptors.contains(descriptor.getAttachedContent());
  }

  private static void runAutoTest(@NotNull Content content, int modificationStamp, @NotNull DelayedDocumentWatcher documentWatcher) {
    JComponent component = content.getComponent();
    if (component != null) {
      DataContext dataContext = DataManager.getInstance().getDataContext(component);
      RunContentDescriptor descriptor = LangDataKeys.RUN_CONTENT_DESCRIPTOR.getData(dataContext);
      if (descriptor != null) {
        ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null && !processHandler.isProcessTerminated()) {
          scheduleRestartOnTermination(descriptor, content, processHandler, modificationStamp, documentWatcher);
        }
        else {
          restart(descriptor, content);
        }
      }
    }
  }

  private static void scheduleRestartOnTermination(@NotNull final RunContentDescriptor descriptor,
                                                   @NotNull final Content content,
                                                   @NotNull final ProcessHandler processHandler,
                                                   final int modificationStamp,
                                                   @NotNull final DelayedDocumentWatcher documentWatcher) {
    ProcessListener restarterListener = ON_TERMINATION_RESTARTER_KEY.get(processHandler);
    if (restarterListener != null) {
      return;
    }
    restarterListener = new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        clearRestarterListener(processHandler);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (documentWatcher.isUpToDate(modificationStamp)) {
              restart(descriptor, content);
            }
          }
        }, ModalityState.any());
      }
    };
    ON_TERMINATION_RESTARTER_KEY.set(processHandler, restarterListener);
    processHandler.addProcessListener(restarterListener);
  }

  private static void restart(@NotNull RunContentDescriptor descriptor, @NotNull Content content) {
    descriptor.setActivateToolWindowWhenAdded(false);
    descriptor.setReuseToolWindowActivation(true);
    ExecutionUtil.restart(content);
  }

  int getDelay() {
    return myDelay;
  }

  void setDelay(int delay) {
    myDelay = delay;
    myDocumentWatcher.deactivate();
    myDocumentWatcher = createWatcher();
    if (!myEnabledDescriptors.isEmpty()) {
      myDocumentWatcher.activate();
    }
    PropertiesComponent.getInstance(myProject).setValue(AUTO_TEST_MANAGER_DELAY, String.valueOf(myDelay));
  }
}
