// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.ExceptionWithAttachments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A utility class for reading shell environment.
 * Use in two steps: first, call one of {@code *command} methods to prepare a command,
 * then run it with {@link #readEnvironment(ProcessBuilder, long)}.
 *
 * @since 2025.3
 */
@ApiStatus.Experimental
public final class ShellEnvironmentReader {
  private static final Logger LOG = Logger.getInstance(ShellEnvironmentReader.class);

  private static final String OUTPUT_PLACEHOLDER = "__OUTPUT_PLACEHOLDER__";
  private static final String INTELLIJ_ENVIRONMENT_READER = "INTELLIJ_ENVIRONMENT_READER";
  private static final String DISABLE_OMZ_AUTO_UPDATE = "DISABLE_AUTO_UPDATE";
  private static final long DEFAULT_TIMEOUT_MILLIS = 20_000L;

  private ShellEnvironmentReader() { }

  /**
   * Prepares a command for reading the environment from the given shell (or from the user's shell if {@code null}).
   * Optionally, runs the given script with given parameters before reading.
   */
  public static @NotNull ProcessBuilder shellCommand(@Nullable String shell, @Nullable Path shFile, @Nullable List<@NotNull String> args) {
    if (shell == null) {
      shell = System.getenv("SHELL");
      if (shell == null || shell.isBlank()) throw new IllegalStateException("'SHELL' environment variable is not set");
    }

    if (shFile != null && !Files.exists(shFile)) throw new IllegalArgumentException("Missing: " + shFile);

    var command = new ArrayList<String>();

    command.add(shell);
    
    var name = Path.of(shell).getFileName().toString();

    if (!("csh".equals(name) || "tcsh".equals(name))) {
      // Csh/Tcsh does not allow using `-l` with any other options
      command.add("-l");
    }

    if (!"fish".equals(name)) {
      // Fish uses a single config file with conditions
      command.add("-i");
    }

    // macOS now supports the `-0` option, too (supposedly, from 12.3); we can drop `printenv` in ~3 years
    var reader = OS.CURRENT == OS.macOS ? "'" + PathManager.findBinFileWithException("printenv") + "'" : "/usr/bin/env -0";
    if (shFile != null) {
      if ("nu".equals(name) || "pwsh".equals(name) || "xonsh".equals(name))
        throw new UnsupportedOperationException("Sourcing external scripts is not supported for '" + name + "'");
      reader = ". '" + shFile + "' && " + reader;
    }
    command.add("-c");
    if ("nu".equals(name)) {
      command.add((reader.charAt(0) == '\'' ? "^" : "") + reader + " out> '" + OUTPUT_PLACEHOLDER + "'");
    }
    else if ("pwsh".equals(name) && reader.charAt(0) == '\'') {
      command.add("&" + reader + " > '" + OUTPUT_PLACEHOLDER + "'");
    }
    else if ("xonsh".equals(name) && reader.charAt(0) == '\'') {
      command.add("$[" + reader + " > '" + OUTPUT_PLACEHOLDER + "']");
    }
    else {
      command.add(reader + " > '" + OUTPUT_PLACEHOLDER + "'");
    }
    if (shFile != null) {
      command.add(shFile.toString());
      if (args != null) {
        command.addAll(args);
      }
    }

    var processBuilder = new ProcessBuilder(command);
    setWorkingDir(shFile, processBuilder);

    if ("zsh".equals(name)) {
      processBuilder.environment().put(DISABLE_OMZ_AUTO_UPDATE, "true");
    }

    return processBuilder;
  }

  /**
   * Prepares a command for reading the environment from the Windows shell.
   * Optionally, runs the given script with given parameters before reading.
   */
  public static @NotNull ProcessBuilder winShellCommand(@Nullable Path batFile, @Nullable List<@NotNull String> args) {
    if (batFile != null && !Files.exists(batFile)) throw new IllegalArgumentException("Missing: " + batFile);

    var callArgs = new ArrayList<String>();
    if (batFile != null) {
      callArgs.add("call");
      callArgs.add(batFile.toString());
      if (args != null) {
        callArgs.addAll(args);
      }
      callArgs.add("&&");
    }
    callArgs.add(javaExePath());
    callArgs.add("-cp");
    callArgs.add(readEnvClasspath());
    callArgs.add(ReadEnv.class.getName());
    callArgs.add(OUTPUT_PLACEHOLDER);
    callArgs.add("||");
    callArgs.add("exit");
    callArgs.add("/B");
    callArgs.add("%ERRORLEVEL%");

    var processBuilder = new ProcessBuilder(CommandLineUtil.getWinShellName(), "/c", prepareCallArgs(callArgs));
    setWorkingDir(batFile, processBuilder);
    return processBuilder;
  }

  private static String prepareCallArgs(List<String> callArgs) {
    var preparedCallArgs = CommandLineUtil.toCommandLine(callArgs);
    var firstArg = preparedCallArgs.remove(0);
    preparedCallArgs.add(0, CommandLineUtil.escapeParameterOnWindows(firstArg, false));
    // for `cmd.exe`, we need to add extra double quotes for the actual command in call
    // to mitigate possible JVM issue when argument contains spaces and the first word
    // starts with double quote and the last ends with double quote and JVM does not
    // wrap the argument with double quotes
    // Example: callArgs = ["\"C:\\New Folder\aa\"", "\"C:\\New Folder\aa\""]
    return '\"' + String.join(" ", preparedCallArgs) + "\"";
  }

  /**
   * Prepares a command for reading the environment from PowerShell.
   * Optionally, runs the given script with given parameters before reading.
   */
  @SuppressWarnings("SpellCheckingInspection")
  public static @NotNull ProcessBuilder powerShellCommand(@Nullable Path psFile, @Nullable List<@NotNull String> args) {
    if (psFile != null && !Files.exists(psFile)) throw new IllegalArgumentException("Missing: " + psFile);

    var innerScriptlet = "";
    if (psFile != null) {
      var argsStr = args == null ? "" : String.join(" ", args);
      innerScriptlet = String.format(Locale.ROOT, "& '%s' %s ; if (-not $?) { exit $LASTEXITCODE }; ", psFile, argsStr);
    }

    var scriptlet = String.format(
      Locale.ROOT,
      "& { %s & '%s' -cp '%s' %s '%s' ; exit $LASTEXITCODE }",
      innerScriptlet, javaExePath(), readEnvClasspath(), ReadEnv.class.getName(), OUTPUT_PLACEHOLDER
    );

    var shellName = PathEnvironmentVariableUtil.findExecutableInWindowsPath("pwsh", "powershell.exe");  // PS7 with a falback to PS5
    var processBuilder = new ProcessBuilder(shellName, "-ExecutionPolicy", "Bypass", "-NonInteractive", "-Command", scriptlet);
    setWorkingDir(psFile, processBuilder);
    return processBuilder;
  }

  @SuppressWarnings("IO_FILE_USAGE")
  private static void setWorkingDir(@Nullable Path file, ProcessBuilder processBuilder) {
    if (file != null) {
      processBuilder.directory(file.getParent().toFile());
    }
  }

  private static String javaExePath() {
    return Path.of(System.getProperty("java.home"), "bin\\java.exe").toString();
  }

  private static String readEnvClasspath() {
    var cp = PathManager.getJarPathForClass(ReadEnv.class);
    if (cp == null) throw new IllegalStateException("Cannot find the '" +  ReadEnv.class.getName() + "' class path");
    return cp;
  }

  /**
   * Runs the given command.
   * Returns loaded environment and the command output (stdout/stderr combined).
   */
  public static @NotNull Pair<@NotNull Map<String, String>, @NotNull String> readEnvironment(
    @NotNull ProcessBuilder command,
    long timeoutMillis
  ) throws IOException {
    if (timeoutMillis <= 0) timeoutMillis = DEFAULT_TIMEOUT_MILLIS;

    var dataFile = Files.createTempFile("ij-shell-env-data.", ".tmp");
    var logFile = Files.createTempFile("ij-shell-env-log.", ".tmp");

    try {
      var args = command.command();
      var substituted = false;
      for (int i = 0; i < args.size(); i++) {
        var arg = args.get(i);
        if (arg.contains(OUTPUT_PLACEHOLDER)) {
          args.set(i, arg.replace(OUTPUT_PLACEHOLDER, dataFile.toString()));
          substituted = true;
          break;
        }
      }
      if (!substituted) throw new IllegalArgumentException("The output file placeholder is missing: " + command.command());

      command.environment().put(INTELLIJ_ENVIRONMENT_READER, "true");

      @SuppressWarnings("IO_FILE_USAGE")
      var process = command
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.to(logFile.toFile()))
        .start();
      int exitCode = waitAndTerminateAfter(process, timeoutMillis);

      var envData = Files.readString(dataFile, Charset.defaultCharset());
      var log = Files.exists(logFile) ? Files.readString(logFile, Charset.defaultCharset()) : "(no log file)";
      if (exitCode != 0 || envData.isEmpty()) {
        if (!log.isEmpty()) {
          LOG.info("stdout/stderr: " + log);
        }
        throw new EnvironmentReaderException(command.command() + ", " + (exitCode == 0 ? "no data" : "ec=" + exitCode), envData, log);
      }

      var env = EnvironmentUtil.parseEnv(envData.split("\0"));
      env.remove(INTELLIJ_ENVIRONMENT_READER);
      if ("zsh".equals(Path.of(command.command().get(0)).getFileName().toString())) {
        env.remove(DISABLE_OMZ_AUTO_UPDATE);
      }
      LOG.info("shell environment loaded (" + env.size() + " vars)");
      return new Pair<>(env, log);
    }
    finally {
      deleteTempFile(logFile);
      deleteTempFile(dataFile);
    }
  }

  private static int waitAndTerminateAfter(Process process, long timeoutMillis) {
    var exitCode = waitFor(process, timeoutMillis);
    if (exitCode != null) return exitCode;

    LOG.warn("shell env loader is timed out");
    var handles = Stream.concat(Stream.of(process.toHandle()), process.descendants()).toList();

    for (var iterator = handles.listIterator(handles.size()); iterator.hasPrevious(); ) {
      iterator.previous().destroy();
    }
    exitCode = waitFor(process, 1000L);
    if (exitCode != null) return exitCode;
    LOG.warn("failed to terminate shell env loader process gracefully, terminating forcibly");

    for (var iterator = handles.listIterator(handles.size()); iterator.hasPrevious(); ) {
      iterator.previous().destroyForcibly();
    }
    exitCode = waitFor(process, 1000L);
    if (exitCode != null) return exitCode;
    LOG.warn("failed to kill shell env loader");

    return -1;
  }

  private static @Nullable Integer waitFor(Process process, long timeoutMillis) {
    try {
      if (process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
        return process.exitValue();
      }
    }
    catch (InterruptedException e) {
      LOG.info("Interrupted while waiting for process", e);
    }
    return null;
  }

  private static void deleteTempFile(Path file) {
    try {
      Files.deleteIfExists(file);
    }
    catch (IOException e) {
      LOG.warn("Cannot delete temporary file", e);
    }
  }

  private static final class EnvironmentReaderException extends IOException implements ExceptionWithAttachments {
    private final Attachment[] myAttachments;

    private EnvironmentReaderException(String message, String data, String log) {
      super(message);
      myAttachments = new Attachment[]{new Attachment("EnvReaderData.txt", data), new Attachment("EnvReaderLog.txt", log)};
    }

    @Override
    public Attachment @NotNull [] getAttachments() {
      return myAttachments;
    }
  }
}
