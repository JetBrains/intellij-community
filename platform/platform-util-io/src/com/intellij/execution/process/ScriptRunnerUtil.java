// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.KillableProcess;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.configurations.PtyCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.io.IdeUtilIoBundle;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

@SuppressWarnings("rawtypes")
public final class ScriptRunnerUtil {
  private static final Logger LOG = Logger.getInstance(ScriptRunnerUtil.class);

  public static final Condition<Key> STDOUT_OUTPUT_KEY_FILTER = key -> ProcessOutputType.isStdout(key);
  public static final Condition<Key> STDERR_OUTPUT_KEY_FILTER = key -> ProcessOutputType.isStderr(key);
  @SuppressWarnings("unused")
  public static final Condition<Key> STDOUT_OR_STDERR_OUTPUT_KEY_FILTER = Conditions.or(STDOUT_OUTPUT_KEY_FILTER, STDERR_OUTPUT_KEY_FILTER);

  private static final int DEFAULT_TIMEOUT = 30000;

  private ScriptRunnerUtil() {}

  public static String getProcessOutput(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    return getProcessOutput(commandLine, STDOUT_OUTPUT_KEY_FILTER, DEFAULT_TIMEOUT);
  }

  public static String getProcessOutput(
    @NotNull GeneralCommandLine commandLine,
    @NotNull Condition<Key> outputTypeFilter,
    long timeout
  ) throws ExecutionException {
    return getProcessOutput(new OSProcessHandler(commandLine), outputTypeFilter, timeout);
  }

  public static String getProcessOutput(
    @NotNull ProcessHandler processHandler,
    @NotNull Condition<Key> outputTypeFilter,
    long timeout
  ) throws ExecutionException {
    LOG.assertTrue(!processHandler.isStartNotified());
    var outputBuilder = new StringBuilder();
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (outputTypeFilter.value(outputType)) {
          var text = event.getText();
          outputBuilder.append(text);
          LOG.debug(text);
        }
      }
    });
    processHandler.startNotify();
    if (!processHandler.waitFor(timeout)) {
      throw new ExecutionException(IdeUtilIoBundle.message("script.execution.timeout", String.valueOf(timeout / 1000)));
    }
    return outputBuilder.toString();
  }

  public static @NotNull OSProcessHandler execute(
    @NotNull String exePath,
    @Nullable String workingDirectory,
    @Nullable VirtualFile scriptFile,
    String[] parameters
  ) throws ExecutionException {
    return execute(exePath, workingDirectory, scriptFile, parameters, null, commandLine -> new ColoredProcessHandler(commandLine), null);
  }

  public static @NotNull OSProcessHandler execute(
    @NotNull String exePath,
    @Nullable String workingDirectory,
    @Nullable VirtualFile scriptFile,
    String[] parameters,
    @Nullable Charset charset,
    @NotNull ThrowableNotNullFunction<? super GeneralCommandLine, ? extends OSProcessHandler, ? extends ExecutionException> creator
  ) throws ExecutionException {
    return execute(exePath, workingDirectory, scriptFile, parameters, charset, creator, null);
  }

  public static @NotNull OSProcessHandler execute(
    @NotNull String exePath,
    @Nullable String workingDirectory,
    @Nullable VirtualFile scriptFile,
    String[] parameters,
    @Nullable Charset charset,
    @NotNull ThrowableNotNullFunction<? super GeneralCommandLine, ? extends OSProcessHandler, ? extends ExecutionException> creator,
    String[] options
  ) throws ExecutionException {
    return execute(exePath, workingDirectory, scriptFile, parameters, charset, creator, options, false);
  }

  public static @NotNull OSProcessHandler execute(
    @NotNull String exePath,
    @Nullable String workingDirectory,
    @Nullable VirtualFile scriptFile,
    String[] parameters,
    @Nullable Charset charset,
    @NotNull ThrowableNotNullFunction<? super GeneralCommandLine, ? extends OSProcessHandler, ? extends ExecutionException> creator,
    String[] options,
    boolean withPty
  ) throws ExecutionException {
    var winExePath = OS.CURRENT == OS.Windows && !OSAgnosticPathUtil.isAbsolute(exePath) ? PathEnvironmentVariableUtil.findFirst(exePath) : null;
    var commandLine = new GeneralCommandLine(winExePath != null ? winExePath.toString() : exePath);
    if (options != null) {
      commandLine.addParameters(options);
    }
    if (scriptFile != null) {
      commandLine.addParameter(scriptFile.getPresentableUrl());
    }
    commandLine.addParameters(parameters);

    if (workingDirectory != null) {
      commandLine.setWorkDirectory(workingDirectory);
    }

    LOG.debug("Command line: ", commandLine.getCommandLineString());
    LOG.debug("Command line env: ", commandLine.getEnvironment());

    if (charset == null) {
      charset = EncodingManager.getInstance().getDefaultCharset();
    }
    commandLine.setCharset(charset);
    if (withPty && !ApplicationManager.getApplication().isHeadlessEnvironment() && !ApplicationManager.getApplication().isUnitTestMode()) {
      if (OS.CURRENT != OS.Windows) {
        commandLine = new PtyCommandLine(commandLine).withInitialColumns(PtyCommandLine.MAX_COLUMNS).withConsoleMode(false);
      }
      else {
        commandLine.getEnvironment().putIfAbsent("TERM", "xterm");
      }
    }
    var processHandler = creator.fun(commandLine);
    if (LOG.isDebugEnabled()) {
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          LOG.debug(outputType + ": " + event.getText());
        }
      });
    }

    return processHandler;
  }

  /// @deprecated use `PathEnvironmentVariableUtil.findFirst(exeName) != null` instead
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public static boolean isExecutableInPath(@NotNull String exeName) {
    return PathEnvironmentVariableUtil.findFirst(exeName) != null;
  }

  @ApiStatus.Internal
  public static ScriptOutput executeScriptInConsoleWithFullOutput(
    String exePathString,
    @Nullable VirtualFile scriptFile,
    @Nullable String workingDirectory,
    long timeout,
    Condition<Key> scriptOutputType,
    String... parameters
  ) throws ExecutionException {
    var processHandler = execute(exePathString, workingDirectory, scriptFile, parameters);

    var output = new ScriptOutput(scriptOutputType);
    processHandler.addProcessListener(output);
    processHandler.startNotify();

    if (!processHandler.waitFor(timeout)) {
      LOG.warn("Process did not complete in " + timeout / 1000 + "s");
      throw new ExecutionException(IdeUtilIoBundle.message("script.execution.timeout", String.valueOf(timeout / 1000)));
    }
    LOG.debug("script output: ", output.myFilteredOutput);
    return output;
  }

  @ApiStatus.Internal
  public static class ScriptOutput implements ProcessListener {
    private final Condition<Key> myScriptOutputType;

    public final StringBuilder myFilteredOutput;
    public final StringBuffer myMergedOutput;

    public ScriptOutput(Condition<Key> scriptOutputType) {
      myScriptOutputType = scriptOutputType;
      myFilteredOutput = new StringBuilder();
      myMergedOutput = new StringBuffer();
    }

    public String getFilteredOutput() {
      return myFilteredOutput.toString();
    }

    public String getMergedOutput() {
      return myMergedOutput.toString();
    }

    public String[] getOutputToParseArray() {
      return getFilteredOutput().split("\n");
    }

    public String getDescriptiveOutput() {
      var outputToParse = getFilteredOutput();
      return StringUtil.isEmpty(outputToParse) ? getMergedOutput() : outputToParse;
    }

    @Override
    public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
      var text = event.getText();
      if (myScriptOutputType.value(outputType)) {
        myFilteredOutput.append(text);
      }
      myMergedOutput.append(text);
    }
  }

  /**
   * Gracefully terminates a process handler.
   * Initially, 'soft kill' is performed (on UNIX it's equivalent to SIGINT signal sending).
   * If the process isn't terminated within a given timeout, 'force quit' is performed (on UNIX it's equivalent to SIGKILL
   * signal sending).
   *
   * @param processHandler {@link ProcessHandler} instance
   * @param millisTimeout timeout in milliseconds between 'soft kill' and 'force quit'
   * @param commandLine command line
   */
  public static void terminateProcessHandler(@NotNull ProcessHandler processHandler, long millisTimeout, @Nullable String commandLine) {
    if (processHandler.isProcessTerminated()) {
      if (commandLine == null && processHandler instanceof BaseProcessHandler) {
        commandLine = ((BaseProcessHandler<?>)processHandler).getCommandLineForLog();
      }
      LOG.warn("Process '" + commandLine + "' is already terminated!");
      return;
    }
    processHandler.destroyProcess();
    if (processHandler instanceof KillableProcess killableProcess) {
      if (killableProcess.canKillProcess()) {
        if (!processHandler.waitFor(millisTimeout)) {
          // doing 'force quit'
          killableProcess.killProcess();
        }
      }
    }
  }
}
