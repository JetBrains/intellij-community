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

/**
 * Created by Max Medvedev on 21/03/14
 */
public class BuildAndRestartConsoleAction extends AnAction {

  private Module myModule;
  private Project myProject;
  private Executor myExecutor;
  private RunContentDescriptor myContentDescriptor;
  private Consumer<Module> myRestarter;

  public BuildAndRestartConsoleAction(@NotNull Module module,
                                      @NotNull Project project,
                                      @NotNull Executor executor,
                                      @NotNull RunContentDescriptor contentDescriptor,
                                      @NotNull Consumer<Module> restarter) {
    super("Build and restart", "Build module '" + module.getName() + "' and restart", AllIcons.Actions.Restart);
    myModule = module;
    myProject = project;
    myExecutor = executor;
    myContentDescriptor = contentDescriptor;
    myRestarter = restarter;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled());
  }

  private boolean isEnabled() {
    if (myModule == null || myModule.isDisposed()) return false;

    final ProcessHandler processHandler = myContentDescriptor.getProcessHandler();
    if (processHandler == null || processHandler.isProcessTerminated()) return false;

    return true;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (ExecutionManager.getInstance(myProject).getContentManager().removeRunContent(myExecutor, myContentDescriptor)) {
      CompilerManager.getInstance(myProject).compile(myModule, new CompileStatusNotification() {
        @Override
        public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
          if (!myModule.isDisposed()) {
            myRestarter.consume(myModule);
          }
        }
      });
    }
  }
}
