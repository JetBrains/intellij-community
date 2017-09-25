/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.runner.DefaultGroovyScriptRunner;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import static org.jetbrains.plugins.groovy.console.GroovyConsoleUtilKt.getWorkingDirectory;

public class GroovyConsole {

  public static final Key<GroovyConsole> GROOVY_CONSOLE = Key.create("Groovy console key");

  private static final Logger LOG = Logger.getInstance(GroovyConsole.class);
  private static final Charset UTF_8 = Charset.forName("UTF-8");
  private static final Executor defaultExecutor = DefaultRunExecutor.getRunExecutorInstance();

  private final Project myProject;
  private final RunContentDescriptor myContentDescriptor;
  private final ConsoleView myConsoleView;
  private final ProcessHandler myProcessHandler;

  public GroovyConsole(Project project, RunContentDescriptor descriptor, ConsoleView view, ProcessHandler handler) {
    myProject = project;
    myContentDescriptor = descriptor;
    myConsoleView = view;
    myProcessHandler = handler;
  }

  private void doExecute(@NotNull String command) {
    for (String line : command.trim().split("\n")) {
      myConsoleView.print("> ", ConsoleViewContentType.USER_INPUT);
      myConsoleView.print(line, ConsoleViewContentType.USER_INPUT);
      myConsoleView.print("\n", ConsoleViewContentType.NORMAL_OUTPUT);
    }
    ApplicationManager.getApplication().executeOnPooledThread(
      () -> send(myProcessHandler, StringUtil.replace(command, "\n", "###\\n"))
    );
  }

  public void execute(@NotNull String command) {
    if (!StringUtil.isEmptyOrSpaces(command)) doExecute(command);
    ExecutionManager.getInstance(myProject).getContentManager().toFrontRunContent(defaultExecutor, myContentDescriptor);
  }

  public void stop() {
    myProcessHandler.destroyProcess(); // use force
    ExecutionManager.getInstance(myProject).getContentManager().removeRunContent(defaultExecutor, myContentDescriptor);
  }

  private static void send(@NotNull ProcessHandler processHandler, @NotNull String command) {
    final OutputStream outputStream = processHandler.getProcessInput();
    assert outputStream != null : "output stream is null";
    final Charset charset = processHandler instanceof BaseOSProcessHandler
                            ? ((BaseOSProcessHandler)processHandler).getCharset()
                            : null;
    byte[] bytes = (command + "\n").getBytes(charset != null ? charset : UTF_8);
    try {
      outputStream.write(bytes);
      outputStream.flush();
    }
    catch (IOException ignored) {
      LOG.warn(ignored);
    }
  }

  public static void getOrCreateConsole(@NotNull final Project project,
                                        @NotNull final VirtualFile contentFile,
                                        @NotNull final Consumer<GroovyConsole> callback) {
    final GroovyConsole existingConsole = contentFile.getUserData(GROOVY_CONSOLE);
    if (existingConsole != null) return;

    final Consumer<Module> initializer = module -> {
      final GroovyConsole console = createConsole(project, contentFile, module);
      if (console != null) {
        callback.consume(console);
      }
    };

    final Module module = GroovyConsoleStateService.getInstance(project).getSelectedModule(contentFile);
    if (module == null || module.isDisposed()) {
      // if not, then select module, then run initializer
      GroovyConsoleUtil.selectModuleAndRun(project, initializer);
    }
    else {
      // if module for console is already selected, then use it for creation
      initializer.consume(module);
    }
  }

  @Nullable
  public static GroovyConsole createConsole(@NotNull final Project project,
                                            @NotNull final VirtualFile contentFile,
                                            @NotNull Module module) {
    final ProcessHandler processHandler = createProcessHandler(module);
    if (processHandler == null) return null;

    final GroovyConsoleStateService consoleStateService = GroovyConsoleStateService.getInstance(project);
    consoleStateService.setFileModule(contentFile, module);
    final String title = consoleStateService.getSelectedModuleTitle(contentFile);

    final ConsoleViewImpl consoleView = new GroovyConsoleView(project);
    final RunContentDescriptor descriptor = new RunContentDescriptor(consoleView, processHandler, new JPanel(new BorderLayout()), title);
    final GroovyConsole console = new GroovyConsole(project, descriptor, consoleView, processHandler);

    // must call getComponent before createConsoleActions()
    final JComponent consoleViewComponent = consoleView.getComponent();

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new BuildAndRestartConsoleAction(module, project, defaultExecutor, descriptor, restarter(project, contentFile)));
    actionGroup.addSeparator();
    actionGroup.addAll(consoleView.createConsoleActions());
    actionGroup.add(new CloseAction(defaultExecutor, descriptor, project) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        processHandler.destroyProcess(); // use force
        super.actionPerformed(e);
      }
    });

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("GroovyConsole", actionGroup, false);
    toolbar.setTargetComponent(consoleViewComponent);

    final JComponent ui = descriptor.getComponent();
    ui.add(consoleViewComponent, BorderLayout.CENTER);
    ui.add(toolbar.getComponent(), BorderLayout.WEST);

    project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      @Override
      public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        if (file.equals(contentFile)) {
          // if file was closed then kill process and hide console content
          console.stop();
        }
      }
    });
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        if (contentFile.getUserData(GROOVY_CONSOLE) == console) {
          // process terminated either by closing file or by close action
          contentFile.putUserData(GROOVY_CONSOLE, null);
        }
      }
    });

    contentFile.putUserData(GROOVY_CONSOLE, console);
    consoleView.attachToProcess(processHandler);
    processHandler.startNotify();

    ExecutionManager.getInstance(project).getContentManager().showRunContent(defaultExecutor, descriptor);
    return console;
  }

  private static ProcessHandler createProcessHandler(Module module) {
    try {
      final JavaParameters javaParameters = createJavaParameters(module);
      final GeneralCommandLine commandLine = javaParameters.toCommandLine();
      return new OSProcessHandler(commandLine) {
        @Override
        public boolean isSilentlyDestroyOnClose() {
          return true;
        }
      };
    }
    catch (ExecutionException e) {
      LOG.warn(e);
      return null;
    }
  }

  private static JavaParameters createJavaParameters(@NotNull Module module) throws ExecutionException {
    JavaParameters res = GroovyScriptRunConfiguration.createJavaParametersWithSdk(module);
    DefaultGroovyScriptRunner.configureGenericGroovyRunner(res, module, "groovy.ui.GroovyMain", !GroovyConsoleUtil.hasGroovyAll(module), true, true, false);
    res.getProgramParametersList().addAll("-p", GroovyScriptRunner.getPathInConf("console.txt"));
    res.setWorkingDirectory(getWorkingDirectory(module));
    res.setUseDynamicClasspath(true);
    return res;
  }

  private static Consumer<Module> restarter(final Project project, final VirtualFile file) {
    return module -> createConsole(project, file, module);
  }
}
