// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.console;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.io.BaseOutputReader;
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
import java.nio.charset.StandardCharsets;

import static com.intellij.openapi.util.RemoveUserDataKt.removeUserData;
import static org.jetbrains.plugins.groovy.console.GroovyConsoleUtilKt.getWorkingDirectory;
import static org.jetbrains.plugins.groovy.console.GroovyConsoleUtilKt.hasNeededDependenciesToRunConsole;

public final class GroovyConsole {

  private static final Key<GroovyConsole> GROOVY_CONSOLE = Key.create("Groovy console key");

  private static final Logger LOG = Logger.getInstance(GroovyConsole.class);
  private static final Executor defaultExecutor = DefaultRunExecutor.getRunExecutorInstance();

  private final Project myProject;
  private final RunContentDescriptor myContentDescriptor;
  private final ConsoleView myConsoleView;
  private final ProcessHandler myProcessHandler;

  private GroovyConsole(Project project, RunContentDescriptor descriptor, ConsoleView view, ProcessHandler handler) {
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
    if (!StringUtil.isEmptyOrSpaces(command)) {
      doExecute(command);
    }
    RunContentManager.getInstance(myProject).toFrontRunContent(defaultExecutor, myContentDescriptor);
  }

  public void stop() {
    myProcessHandler.destroyProcess(); // use force
  }

  private static void send(@NotNull ProcessHandler processHandler, @NotNull String command) {
    final OutputStream outputStream = processHandler.getProcessInput();
    assert outputStream != null : "output stream is null";
    final Charset charset = processHandler instanceof BaseOSProcessHandler
                            ? ((BaseOSProcessHandler)processHandler).getCharset()
                            : null;
    byte[] bytes = (command + "\n").getBytes(charset != null ? charset : StandardCharsets.UTF_8);
    try {
      outputStream.write(bytes);
      outputStream.flush();
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  public static void stopConsole(@NotNull VirtualFile contentFile) {
    GroovyConsole console = removeUserData(contentFile, GROOVY_CONSOLE);
    if (console != null) {
      console.stop();
    }
  }

  public static void getOrCreateConsole(final @NotNull Project project,
                                        final @NotNull VirtualFile contentFile,
                                        final @NotNull Consumer<? super GroovyConsole> callback) {
    final GroovyConsole existingConsole = contentFile.getUserData(GROOVY_CONSOLE);
    if (existingConsole != null) {
      callback.consume(existingConsole);
      return;
    }

    final Consumer<Module> initializer = module -> {
      final GroovyConsole console = createConsole(project, contentFile, module);
      if (console != null) {
        callback.consume(console);
      }
    };

    final GroovyConsoleStateService service = GroovyConsoleStateService.getInstance(project);
    final Module module = service.getSelectedModule(contentFile);
    if (module != null) {
      // if module for console is already selected, then use it for creation
      initializer.consume(module);
      return;
    }

    // if not, then select module, then run initializer
    GroovyConsoleUtil.selectModuleAndRun(project, selectedModule -> {
      service.setFileModule(contentFile, selectedModule);
      initializer.consume(selectedModule);
    });
  }

  public static @Nullable GroovyConsole createConsole(final @NotNull Project project,
                                                      final @NotNull VirtualFile contentFile,
                                                      @NotNull Module module) {
    final ProcessHandler processHandler = createProcessHandler(module);
    if (processHandler == null) return null;

    final ConsoleViewImpl consoleView = new GroovyConsoleView(project);
    final RunContentDescriptor descriptor = new RunContentDescriptor(
      consoleView, processHandler, new JPanel(new BorderLayout()), contentFile.getNameWithoutExtension()
    );
    final GroovyConsole console = new GroovyConsole(project, descriptor, consoleView, processHandler);

    // must call getComponent before createConsoleActions()
    final JComponent consoleViewComponent = consoleView.getComponent();

    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    actionGroup.add(new BuildAndRestartConsoleAction(module, project, defaultExecutor, descriptor, restarter(project, contentFile)));
    actionGroup.addSeparator();
    actionGroup.addAll(consoleView.createConsoleActions());
    actionGroup.add(new CloseAction(defaultExecutor, descriptor, project) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        processHandler.destroyProcess(); // use force
        super.actionPerformed(e);
      }
    });

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("GroovyConsole", actionGroup, false);
    toolbar.setTargetComponent(consoleViewComponent);

    final JComponent ui = descriptor.getComponent();
    ui.add(consoleViewComponent, BorderLayout.CENTER);
    ui.add(toolbar.getComponent(), BorderLayout.WEST);

    processHandler.addProcessListener(new ProcessListener() {
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

    RunContentManager.getInstance(project).showRunContent(defaultExecutor, descriptor);
    return console;
  }

  private static ProcessHandler createProcessHandler(Module module) {
    try {
      final JavaParameters javaParameters = createJavaParameters(module);
      final GeneralCommandLine commandLine = javaParameters.toCommandLine();
      return new ColoredProcessHandler(commandLine) {

        @Override
        protected @NotNull BaseOutputReader.Options readerOptions() {
          return BaseOutputReader.Options.forMostlySilentProcess();
        }

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
    DefaultGroovyScriptRunner.configureGenericGroovyRunner(
      res, module, "groovy.ui.GroovyMain",
      !hasNeededDependenciesToRunConsole(module), true, true, false
    );
    res.getProgramParametersList().addAll("-p", GroovyScriptRunner.getPathInConf("console.groovy"));
    res.setWorkingDirectory(getWorkingDirectory(module));
    res.setUseDynamicClasspath(true);
    if (useArgsFile(res)) {
      res.setArgFile(true);
    }
    return res;
  }

  private static boolean useArgsFile(@NotNull JavaParameters res) {
    Sdk jdk = res.getJdk();
    if (jdk == null) {
      return false;
    }
    String rootPath = jdk.getHomePath();
    if (rootPath == null) {
      return false;
    }
    return JdkUtil.isModularRuntime(rootPath);
  }

  private static Consumer<Module> restarter(final Project project, final VirtualFile file) {
    return module -> createConsole(project, file, module);
  }
}
