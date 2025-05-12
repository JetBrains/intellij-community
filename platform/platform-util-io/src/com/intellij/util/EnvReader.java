// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@ApiStatus.Internal
public class EnvReader extends EnvironmentUtil.ShellEnvReader {

  private static final String READ_ENV_CLASS_NAME = ReadEnv.class.getCanonicalName();

  private static @Nullable String readEnvClasspath() {
    return PathManager.getJarPathForClass(ReadEnv.class);
  }

  private static @NotNull String javaExePath() {
    return (System.getProperty("java.home") + "/bin/java").replace('/', File.separatorChar);
  }

  public EnvReader() {
  }

  public EnvReader(long timeoutMillis) {
    super(timeoutMillis);
  }

  public @NotNull Map<String, String> readBatEnv(@Nullable Path batchFile, List<String> args) throws IOException {
    return readBatOutputAndEnv(batchFile, args).second;
  }

  /**
   * @see #readBatOutputAndEnv(Path, List, String)
   * @see #readBatOutputAndEnv(Path, List, String, Consumer)
   */
  public final @NotNull Pair<String, Map<String, String>> readBatOutputAndEnv(@Nullable Path batchFile, @Nullable List<@NotNull String> args) throws IOException {
    return readBatOutputAndEnv(batchFile, args, "cmd.exe"); // NON-NLS
  }

  /**
   * @param cmdExePath the path (either a full or a short one) to {@code cmd.exe}.
   * @see #readBatOutputAndEnv(Path, List)
   * @see #readBatOutputAndEnv(Path, List, String, Consumer)
   */
  public final @NotNull Pair<String, Map<String, String>> readBatOutputAndEnv(@Nullable Path batchFile,
                                                                              @Nullable List<@NotNull String> args,
                                                                              @NotNull String cmdExePath) throws IOException {
    return readBatOutputAndEnv(batchFile, args, cmdExePath, (it) -> {});
  }

  /**
   * @param cmdExePath the path (either a full or a short one) to {@code cmd.exe}.
   * @param scriptEnvironmentProcessor the block which accepts the environment
   *                                   of the new process, allowing to add and
   *                                   remove environment variables.
   * @see #readBatOutputAndEnv(Path, List)
   * @see #readBatOutputAndEnv(Path, List, String)
   */
  public @NotNull Pair<String, Map<String, String>> readBatOutputAndEnv(@Nullable Path batchFile,
                                                                        @Nullable List<@NotNull String> args,
                                                                        @NotNull String cmdExePath,
                                                                        @NotNull Consumer<? super @NotNull Map<String, String>> scriptEnvironmentProcessor) throws IOException {
    if (batchFile != null && !Files.exists(batchFile)) {
      throw new NoSuchFileException(batchFile.toString());
    }
    final Path envDataFile = Files.createTempFile("intellij-cmd-env-data.", ".tmp"); // NON-NLS

    final List<String> callArgs = new ArrayList<>();
    if (batchFile != null) {
      callArgs.add("call");
      callArgs.add(batchFile.toString());
      if (args != null) {
        callArgs.addAll(args);
      }
      // Scripts like `vcvarsall.bat` may write debugging logs to stdout.
      // They would interfere with environment variables, if there was no stream redirection.
      callArgs.add("1>&2");
      callArgs.add("&&");
    }

    callArgs.add(javaExePath()); // NON-NLS
    callArgs.add("-cp"); // NON-NLS
    callArgs.add(PathManager.getJarPathForClass(ReadEnv.class));
    callArgs.add(ReadEnv.class.getCanonicalName());
    callArgs.add(envDataFile.toString());

    callArgs.add("||");
    callArgs.add("exit"); // NON-NLS
    callArgs.add("/B"); // NON-NLS
    callArgs.add("%ERRORLEVEL%"); // NON-NLS

    final List<@NonNls String> cl = new ArrayList<>();
    cl.add(cmdExePath);
    cl.add("/c");
    cl.add(prepareCallArgs(callArgs));
    Map.Entry<String, Map<String, String>> entry =
      runProcessAndReadOutputAndEnvs(cl, batchFile != null ? batchFile.getParent() : null, scriptEnvironmentProcessor, envDataFile);
    return new Pair<>(entry.getKey(), entry.getValue());
  }

  @SuppressWarnings("SpellCheckingInspection")
  public @NotNull Pair<String, Map<String, String>> readPs1OutputAndEnv(
    @Nullable Path ps1Path,
    @Nullable List<@NotNull String> args,
    @NotNull Consumer<? super @NotNull Map<String, String>> scriptEnvironmentProcessor
  ) throws IOException {
    if (ps1Path != null && !Files.exists(ps1Path)) {
      throw new NoSuchFileException(ps1Path.toString());
    }
    var envDataFile = Files.createTempFile("intellij-cmd-env-data.", ".tmp");
    final String innerScriptlet;

    if (ps1Path == null) {
      innerScriptlet = "";
    }
    else {
      var argsStr = args == null ? "" : String.join(" ", args);
      innerScriptlet = String.format(Locale.ROOT, "& '%s' %s ; if (-not $?) { exit $LASTEXITCODE }; ", ps1Path, argsStr);
    }

    final var scriptlet = String.format(Locale.ROOT, "& { %s & '%s' -cp '%s' %s '%s' ; exit $LASTEXITCODE }",
                                        innerScriptlet, javaExePath(), readEnvClasspath(), READ_ENV_CLASS_NAME, envDataFile.toString());
    // Powershell 7+ with a falback
    String shellName = PathEnvironmentVariableUtil.findExecutableInWindowsPath("pwsh", "powershell.exe");
    var command = List.of(shellName, "-ExecutionPolicy", "Bypass", "-NonInteractive", "-Command", scriptlet);
    Path workingDir = ps1Path != null ? ps1Path.getParent() : null;
    var output =
      runProcessAndReadOutputAndEnvs(command, workingDir, scriptEnvironmentProcessor, envDataFile);
    return new Pair<>(output.getKey(), output.getValue());

  }

  @VisibleForTesting
  public @NotNull Map<String, String> readPs1Env(Path ps1Path, List<String> args) throws IOException {
    return readPs1OutputAndEnv(ps1Path, args, (it) -> {}).second;
  }

  private static @NotNull String prepareCallArgs(@NotNull List<String> callArgs) {
    List<String> preparedCallArgs = CommandLineUtil.toCommandLine(callArgs);
    String firstArg = preparedCallArgs.remove(0);
    preparedCallArgs.add(0, CommandLineUtil.escapeParameterOnWindows(firstArg, false));
    // for CMD we would like to add extra double quotes for the actual command in call
    // to mitigate possible JVM issue when argument contains spaces and the first word
    // starts with double quote and the last ends with double quote and JVM does not
    // wrap the argument with double quotes
    // Example: callArgs = ["\"C:\\New Folder\aa\"", "\"C:\\New Folder\aa\""]
    return '\"' + String.join(" ", preparedCallArgs) + "\"";
  }
}
