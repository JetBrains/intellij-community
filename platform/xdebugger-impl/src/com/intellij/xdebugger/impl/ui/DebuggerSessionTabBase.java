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

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.diagnostic.logging.DebuggerLogConsoleManager;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.runners.RunTab;
import com.intellij.execution.ui.*;
import com.intellij.execution.ui.layout.LayoutAttractionPolicy;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.AppIcon;
import com.intellij.ui.content.Content;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author nik
 */
public abstract class DebuggerSessionTabBase extends RunTab implements DebuggerLogConsoleManager {
  protected ExecutionConsole myConsole;

  public DebuggerSessionTabBase(@NotNull Project project, @NotNull String runnerId, @NotNull String sessionName, @NotNull GlobalSearchScope searchScope) {
    super(project, searchScope, runnerId, XDebuggerBundle.message("xdebugger.default.content.title"), sessionName);

    Disposer.register(project, this);

    myUi.getDefaults()
      .initTabDefaults(0, XDebuggerBundle.message("xdebugger.debugger.tab.title"), null)
      .initFocusContent(DebuggerContentInfo.FRAME_CONTENT, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION)
      .initFocusContent(DebuggerContentInfo.CONSOLE_CONTENT, LayoutViewOptions.STARTUP, new LayoutAttractionPolicy.FocusOnce(false));
  }

  protected static ActionGroup getCustomizedActionGroup(final String id) {
    return (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(id);
  }

  public abstract RunContentDescriptor getRunContentDescriptor();

  @Override
  public ProcessHandler getProcessHandler() {
    return getRunContentDescriptor().getProcessHandler();
  }

  @Override
  protected Content createLogContent(AdditionalTabComponent tabComponent, String id, Icon icon) {
    Content result = super.createLogContent(tabComponent, id, icon);
    result.setCloseable(false);
    result.setDescription(tabComponent.getTooltip());
    return result;
  }

  @Override
  protected Icon getDefaultIcon() {
    return AllIcons.FileTypes.Text;
  }

  @Override
  @NotNull
  public RunnerLayoutUi getUi() {
    return myUi;
  }

  protected void attachNotificationTo(final Content content) {
    if (myConsole instanceof ObservableConsoleView) {
      ObservableConsoleView observable = (ObservableConsoleView)myConsole;
      observable.addChangeListener(new ObservableConsoleView.ChangeListener() {
        @Override
        public void contentAdded(final Collection<ConsoleViewContentType> types) {
          if (types.contains(ConsoleViewContentType.ERROR_OUTPUT) || types.contains(ConsoleViewContentType.NORMAL_OUTPUT)) {
            content.fireAlert();
          }
        }
      }, content);
      RunProfile profile = getRunProfile();
      if (profile instanceof RunConfigurationBase && !ApplicationManager.getApplication().isUnitTestMode()) {
        final RunConfigurationBase runConfigurationBase = (RunConfigurationBase)profile;
        observable.addChangeListener(new RunContentBuilder.ConsoleToFrontListener(runConfigurationBase,
                                                                                  getProject(),
                                                                                  DefaultDebugExecutor.getDebugExecutorInstance(),
                                                                                  myRunContentDescriptor,
                                                                                  getUi()),
                                     content);
      }
    }
  }

  @Nullable
  protected RunProfile getRunProfile() {
    ExecutionEnvironment environment = getEnvironment();
    return environment != null ? environment.getRunProfile() : null;
  }

  public void toFront(boolean focus) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ExecutionManager.getInstance(getProject()).getContentManager().toFrontRunContent(DefaultDebugExecutor.getDebugExecutorInstance(), myRunContentDescriptor);
      if (focus) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            boolean focusWnd = Registry.is("debugger.mayBringFrameToFrontOnBreakpoint");
            ProjectUtil.focusProjectWindow(getProject(), focusWnd);
            if (!focusWnd) {
              AppIcon.getInstance().requestAttention(getProject(), true);
            }
          }
        });
      }
    }
  }
}
