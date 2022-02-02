// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.execution.CommandLineUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@ApiStatus.Internal
public class EnvReader extends EnvironmentUtil.ShellEnvReader {
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
                                                                        @NotNull Consumer<@NotNull Map<String, String>> scriptEnvironmentProcessor) throws IOException {
    if (batchFile != null && !Files.exists(batchFile)) {
      throw new NoSuchFileException(batchFile.toString());
    }

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

    callArgs.add((System.getProperty("java.home") + "/bin/java").replace('/', File.separatorChar)); // NON-NLS
    callArgs.add("-cp"); // NON-NLS
    callArgs.add(PathManager.getJarPathForClass(ReadEnv.class));
    callArgs.add(ReadEnv.class.getCanonicalName());

    callArgs.add("");
    callArgs.add("||");
    callArgs.add("exit"); // NON-NLS
    callArgs.add("/B"); // NON-NLS
    callArgs.add("%ERRORLEVEL%"); // NON-NLS

    final List<@NonNls String> cl = new ArrayList<>();
    cl.add(cmdExePath);
    cl.add("/c");
    cl.add(prepareCallArgs(callArgs));
    Map.Entry<String, Map<String, String>> entry =
      runProcessAndReadOutputAndEnvs(cl, batchFile != null ? batchFile.getParent() : null, scriptEnvironmentProcessor);
    return new Pair<>(entry.getKey(), entry.getValue());
  }

  @NotNull
  private static String prepareCallArgs(@NotNull List<String> callArgs) {
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
