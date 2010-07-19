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

import com.intellij.diagnostic.logging.*;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithActions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public abstract class DebuggerSessionTabBase implements DebuggerLogConsoleManager, Disposable {
  private final Map<AdditionalTabComponent, Content> myAdditionalContent = new HashMap<AdditionalTabComponent, Content>();
  private final Map<AdditionalTabComponent, ContentManagerListener> myContentListeners =
    new HashMap<AdditionalTabComponent, ContentManagerListener>();
  private final Project myProject;
  private final LogFilesManager myManager;
  protected ExecutionEnvironment myEnvironment;

  protected ExecutionConsole myConsole;
  protected RunContentDescriptor myRunContentDescriptor;

  private final Icon DEFAULT_TAB_COMPONENT_ICON = IconLoader.getIcon("/fileTypes/text.png");

  public DebuggerSessionTabBase(Project project) {
    myProject = project;
    myManager = new LogFilesManager(project, this, this);
  }

  public abstract RunContentDescriptor getRunContentDescriptor();

  public abstract RunnerLayoutUi getUi();

  protected void registerFileMatcher(final RunProfile runConfiguration) {
    if (runConfiguration instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)runConfiguration);
    }
  }

  protected void initLogConsoles(final RunProfile runConfiguration, final ProcessHandler processHandler) {
    if (runConfiguration instanceof RunConfigurationBase && ((RunConfigurationBase)runConfiguration).needAdditionalConsole()) {
      myManager.initLogConsoles((RunConfigurationBase)runConfiguration, processHandler);
    }
  }

  // TODO[oleg]: talk to nick
  public void setEnvironment(@NotNull final ExecutionEnvironment env) {
    myEnvironment = env;
  }

  @NotNull
  public LogConsoleBase addLogConsole(String name, Reader reader, long skippedContent, Icon icon) {
    final Ref<Content> content = new Ref<Content>();
    LogConsoleBase console = new LogConsoleBase(myProject, reader, skippedContent, name, false, new DefaultLogFilterModel(myProject)) {
      public boolean isActive() {
        final Content logContent = content.get();
        return logContent != null && logContent.isSelected();
      }
    };
    addLogConsole(console, icon, content);
    return console;
  }

  public void addLogConsole(final String name, final String path, final long skippedContent, Icon icon) {
    final Ref<Content> content = new Ref<Content>();
    addLogConsole(new LogConsoleImpl(myProject, new File(path), skippedContent, name, false) {
      public boolean isActive() {
        final Content logContent = content.get();
        return logContent != null && logContent.isSelected();
      }
    }, icon, content);
  }

  private void addLogConsole(final LogConsoleBase logConsole, Icon icon, Ref<Content> content) {
    logConsole.attachStopLogConsoleTrackingListener(getRunContentDescriptor().getProcessHandler());
    // Attach custom log handlers
    if (myEnvironment != null && myEnvironment.getRunProfile() instanceof RunConfigurationBase) {
      ((RunConfigurationBase)myEnvironment.getRunProfile()).customizeLogConsole(logConsole);
    }

    content.set(addLogComponent(logConsole, icon));
    final ContentManagerAdapter l = new ContentManagerAdapter() {
      public void selectionChanged(final ContentManagerEvent event) {
        logConsole.activate();
      }
    };
    myContentListeners.put(logConsole, l);
    getUi().addListener(l, this);
  }

  public void addLogConsole(String name, String path, long skippedContent) {
    addLogConsole(name, path, skippedContent, DEFAULT_TAB_COMPONENT_ICON);
  }

  @Nullable
  public static String getLogContentId(@NotNull String tabTitle) {
    return "Log-" + tabTitle;
  }

  public void removeLogConsole(final String path) {
    LogConsoleImpl componentToRemove = null;
    for (AdditionalTabComponent tabComponent : myAdditionalContent.keySet()) {
      if (tabComponent instanceof LogConsoleImpl) {
        final LogConsoleImpl console = (LogConsoleImpl)tabComponent;
        if (Comparing.strEqual(console.getPath(), path)) {
          componentToRemove = console;
          break;
        }
      }
    }
    if (componentToRemove != null) {
      getUi().removeListener(myContentListeners.remove(componentToRemove));
      removeAdditionalTabComponent(componentToRemove);
    }
  }

  public void addAdditionalTabComponent(AdditionalTabComponent tabComponent, String id, Icon icon) {
    addLogComponent(tabComponent, id, icon);
  }

  public void addAdditionalTabComponent(final AdditionalTabComponent tabComponent, final String id) {
    addLogComponent(tabComponent, id);
  }

  private void addLogComponent(AdditionalTabComponent component, String id) {
    addLogComponent(component, id, DEFAULT_TAB_COMPONENT_ICON);
  }

  private Content addLogComponent(AdditionalTabComponent tabComponent, Icon icon) {
    @NonNls final String id = getLogContentId(tabComponent.getTabTitle());
    return addLogComponent(tabComponent, id, icon);
  }

  private Content addLogComponent(final AdditionalTabComponent tabComponent, String id, Icon icon) {
    final Content logContent = getUi().createContent(id, (ComponentWithActions)tabComponent, tabComponent.getTabTitle(), icon,
                                                     tabComponent.getPreferredFocusableComponent());
    logContent.setCloseable(false);
    logContent.setDescription(tabComponent.getTooltip());
    myAdditionalContent.put(tabComponent, logContent);
    getUi().addContent(logContent);
    Disposer.register(this, new Disposable() {
      public void dispose() {
        removeAdditionalTabComponent(tabComponent);
      }
    });

    return logContent;
  }

  public void removeAdditionalTabComponent(AdditionalTabComponent component) {
    component.dispose();
    final Content content = myAdditionalContent.remove(component);
    getUi().removeContent(content, true);
  }

  public void toFront() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ExecutionManager.getInstance(getProject()).getContentManager().toFrontRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), myRunContentDescriptor);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ProjectUtil.focusProjectWindow(getProject(), Registry.is("debugger.mayBringFrameToFrontOnBreakpoint"));
        }
      });
    }
  }

  protected Project getProject() {
    return myProject;
  }

  public void dispose() {
  }
}
