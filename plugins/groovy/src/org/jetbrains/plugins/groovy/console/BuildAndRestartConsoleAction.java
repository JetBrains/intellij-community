// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public class BuildAndRestartConsoleAction extends AnAction {

  private final Module myModule;
  private final Project myProject;
  private final Executor myExecutor;
  private final RunContentDescriptor myContentDescriptor;
  private final Consumer<? super Module> myRestarter;

  public BuildAndRestartConsoleAction(@NotNull Module module,
                                      @NotNull Project project,
                                      @NotNull Executor executor,
                                      @NotNull RunContentDescriptor contentDescriptor,
                                      @NotNull Consumer<? super Module> restarter) {
    super("Build and Restart", "Build module '" + module.getName() + "' and restart", AllIcons.Actions.Restart);
    myModule = module;
    myProject = project;
    myExecutor = executor;
    myContentDescriptor = contentDescriptor;
    myRestarter = restarter;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled());
  }

  private boolean isEnabled() {
    if (myModule == null || myModule.isDisposed()) return false;

    final ProcessHandler processHandler = myContentDescriptor.getProcessHandler();
    if (processHandler == null || processHandler.isProcessTerminated()) return false;

    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (ExecutionManager.getInstance(myProject).getContentManager().removeRunContent(myExecutor, myContentDescriptor)) {
      CompilerManager.getInstance(myProject).compile(myModule, new CompileStatusNotification() {
        @Override
        public void finished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext) {
          if (!myModule.isDisposed()) {
            myRestarter.consume(myModule);
          }
        }
      });
    }
  }
}
