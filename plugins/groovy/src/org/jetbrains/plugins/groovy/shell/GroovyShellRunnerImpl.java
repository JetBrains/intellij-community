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
package org.jetbrains.plugins.groovy.shell;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.console.ProcessBackedConsoleExecuteActionHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.AbstractConsoleRunnerWithHistory;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.console.BuildAndRestartConsoleAction;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyFileImpl;

import java.util.List;

public class GroovyShellRunnerImpl extends AbstractConsoleRunnerWithHistory<LanguageConsoleView> {

  private static final Logger LOG = Logger.getInstance(GroovyShellRunnerImpl.class);
  public static final Key<Boolean> GROOVY_SHELL_FILE = Key.create("GROOVY_SHELL_FILE");
  public static final String GROOVY_SHELL_EXECUTE = "Groovy.Shell.Execute";

  private final GroovyShellConfig myShellRunner;
  private final Module myModule;
  private final Consumer<Module> myStarter = new Consumer<Module>() {
    @Override
    public void consume(Module module) {
      doRunShell(myShellRunner, module);
    }
  };
  private GeneralCommandLine myCommandLine;

  public GroovyShellRunnerImpl(@NotNull String consoleTitle,
                               @NotNull GroovyShellConfig shellRunner,
                               @NotNull Module module) {
    super(module.getProject(), consoleTitle, shellRunner.getWorkingDirectory(module));
    myShellRunner = shellRunner;
    myModule = module;
  }

  @Override
  protected List<AnAction> fillToolBarActions(DefaultActionGroup toolbarActions,
                                              final Executor defaultExecutor,
                                              final RunContentDescriptor contentDescriptor) {
    BuildAndRestartConsoleAction rebuildAction =
      new BuildAndRestartConsoleAction(myModule, getProject(), defaultExecutor, contentDescriptor, myStarter);
    toolbarActions.add(rebuildAction);
    List<AnAction> actions = super.fillToolBarActions(toolbarActions, defaultExecutor, contentDescriptor);
    actions.add(rebuildAction);
    return actions;
  }

  @Override
  protected LanguageConsoleView createConsoleView() {
    final LanguageConsoleView res = new GroovyShellLanguageConsoleView(getProject(), getConsoleTitle());
    final GroovyFileImpl file = (GroovyFileImpl)res.getFile();
    assert file.getContext() == null;
    file.putUserData(GROOVY_SHELL_FILE, Boolean.TRUE);
    file.setContext(myShellRunner.getContext(myModule));
    return res;
  }

  @Override
  protected Process createProcess() throws ExecutionException {
    JavaParameters javaParameters = myShellRunner.createJavaParameters(myModule);

    final Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
    assert sdk != null;
    SdkTypeId sdkType = sdk.getSdkType();
    assert sdkType instanceof JavaSdkType;
    final String exePath = ((JavaSdkType)sdkType).getVMExecutablePath(sdk);

    myCommandLine = JdkUtil.setupJVMCommandLine(exePath, javaParameters, true);
    return myCommandLine.createProcess();
  }

  @Override
  protected OSProcessHandler createProcessHandler(Process process) {
    return new OSProcessHandler(process, myCommandLine.getCommandLineString());
  }

  @NotNull
  @Override
  protected ProcessBackedConsoleExecuteActionHandler createExecuteActionHandler() {
    ProcessBackedConsoleExecuteActionHandler handler = new ProcessBackedConsoleExecuteActionHandler(getProcessHandler(), false) {
      @Override
      public String getEmptyExecuteAction() {
        return GROOVY_SHELL_EXECUTE;
      }
    };
    new ConsoleHistoryController(getConsoleTitle(), null, getConsoleView()).install();
    return handler;
  }

  public static void doRunShell(final GroovyShellConfig config, final Module module) {
    try {
      new GroovyShellRunnerImpl(config.getTitle(), config, module).initAndRun();
    }
    catch (ExecutionException e) {
      LOG.info(e);
      Messages.showErrorDialog(module.getProject(), e.getMessage(), "Cannot Run " + config.getTitle());
    }
  }
}
