/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.runners.RunTab;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.execution.ui.layout.LayoutAttractionPolicy;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class DebuggerSessionTabBase extends RunTab {
  protected ExecutionConsole myConsole;

  public DebuggerSessionTabBase(@NotNull Project project, @NotNull String runnerId, @NotNull String sessionName, @NotNull GlobalSearchScope searchScope) {
    super(project, searchScope, runnerId, XDebuggerBundle.message("xdebugger.default.content.title"), sessionName);

    myUi.getDefaults()
      .initTabDefaults(0, XDebuggerBundle.message("xdebugger.debugger.tab.title"), null)
      .initFocusContent(DebuggerContentInfo.FRAME_CONTENT, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION)
      .initFocusContent(DebuggerContentInfo.CONSOLE_CONTENT, LayoutViewOptions.STARTUP, new LayoutAttractionPolicy.FocusOnce(false));
  }

  public static ActionGroup getCustomizedActionGroup(final String id) {
    return (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(id);
  }

  protected void attachNotificationTo(final Content content) {
    if (myConsole instanceof ObservableConsoleView) {
      ObservableConsoleView observable = (ObservableConsoleView)myConsole;
      observable.addChangeListener(types -> {
        if (types.contains(ConsoleViewContentType.ERROR_OUTPUT) || types.contains(ConsoleViewContentType.NORMAL_OUTPUT)) {
          content.fireAlert();
        }
      }, content);
      RunProfile profile = getRunProfile();
      if (profile instanceof RunConfigurationBase && !ApplicationManager.getApplication().isUnitTestMode()) {
        observable.addChangeListener(new RunContentBuilder.ConsoleToFrontListener((RunConfigurationBase)profile,
                                                                                  myProject,
                                                                                  DefaultDebugExecutor.getDebugExecutorInstance(),
                                                                                  myRunContentDescriptor,
                                                                                  myUi),
                                     content);
      }
    }
  }

  @Nullable
  protected RunProfile getRunProfile() {
    return myEnvironment != null ? myEnvironment.getRunProfile() : null;
  }


  public void select() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    UIUtil.invokeLaterIfNeeded(() -> {
      if (myRunContentDescriptor != null) {
        ToolWindow toolWindow = ExecutionManager.getInstance(myProject).getContentManager()
          .getToolWindowByDescriptor(myRunContentDescriptor);
        Content content = myRunContentDescriptor.getAttachedContent();
        if (toolWindow == null || content == null) return;
        ContentManager manager = toolWindow.getContentManager();
        if (ArrayUtil.contains(content, manager.getContents()) && !manager.isSelected(content)) {
          manager.setSelectedContent(content);
        }
      }
    });
  }
}
