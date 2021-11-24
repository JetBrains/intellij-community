// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui;

import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.runners.RunTab;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.layout.LayoutAttractionPolicy;
import com.intellij.execution.ui.layout.LayoutStateDefaults;
import com.intellij.execution.ui.layout.LayoutViewOptions;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.Content;
import com.intellij.util.ModalityUiUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DebuggerSessionTabBase extends RunTab {
  protected ExecutionConsole myConsole;

  public DebuggerSessionTabBase(
    @NotNull Project project,
    @NotNull String runnerId,
    @NotNull String sessionName,
    @NotNull GlobalSearchScope searchScope,
    boolean shouldInitTabDefaults) {
    super(project, searchScope, runnerId, XDebuggerBundle.message("xdebugger.default.content.title"), sessionName);

    LayoutStateDefaults defaults = myUi.getDefaults();
    if (shouldInitTabDefaults)
      defaults.initTabDefaults(0, XDebuggerBundle.message("xdebugger.debugger.tab.title"), null);

    defaults
      .initContentAttraction(DebuggerContentInfo.FRAME_CONTENT, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION)
      .initContentAttraction(DebuggerContentInfo.CONSOLE_CONTENT, LayoutViewOptions.STARTUP, new LayoutAttractionPolicy.FocusOnce(false))
      .initContentAttraction(DebuggerContentInfo.CONSOLE_CONTENT, XDebuggerUIConstants.LAYOUT_VIEW_FINISH_CONDITION, new LayoutAttractionPolicy.FocusOnce());
  }

  public static ActionGroup getCustomizedActionGroup(final String id) {
    return (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(id);
  }

  protected void attachNotificationTo(final Content content) {
    if (myConsole instanceof ObservableConsoleView) {
      ObservableConsoleView observable = (ObservableConsoleView)myConsole;
      AtomicBoolean alertFired = new AtomicBoolean();
      observable.addChangeListener(new ObservableConsoleView.ChangeListener() {
        @Override
        public void textAdded(@NotNull String text, @NotNull ConsoleViewContentType type) {
          if ((type.equals(ConsoleViewContentType.ERROR_OUTPUT) || type.equals(ConsoleViewContentType.NORMAL_OUTPUT)) && alertFired.compareAndSet(false, true)) {
            content.fireAlert();
          }
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

    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
      if (myRunContentDescriptor != null) {
        RunContentManager manager = RunContentManager.getInstance(myProject);
        ToolWindow toolWindow = manager.getToolWindowByDescriptor(myRunContentDescriptor);
        Content content = myRunContentDescriptor.getAttachedContent();
        if (toolWindow == null || content == null) return;
        manager.selectRunContent(myRunContentDescriptor);
      }
    });
  }
}
