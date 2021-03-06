// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

@ApiStatus.Internal
public class EnvReader extends EnvironmentUtil.ShellEnvReader {
  public EnvReader() {
  }

  public EnvReader(long timeoutMillis) {
    super(timeoutMillis);
  }

  public @NotNull Map<String, String> readShellEnv(@Nullable Path file, @Nullable Map<String, String> additionalEnvironment) throws IOException {
    return doReadShellEnv(file, PathManager.findBinFileWithException(EnvironmentUtil.READER_FILE_NAME), additionalEnvironment);
  }

  public @NotNull Map<String, String> readBatEnv(@Nullable Path batchFile, List<String> args) throws IOException {
    return readBatOutputAndEnv(batchFile, args).second;
  }

  public @NotNull Pair<String, Map<String, String>> readBatOutputAndEnv(@Nullable Path batchFile, List<String> args) throws IOException {
    if (batchFile != null && !Files.exists(batchFile)) {
      throw new NoSuchFileException(batchFile.toString());
    }

    Path envFile = Files.createTempFile("intellij-cmd-env.", ".tmp");
    try {
      List<String> callArgs = new ArrayList<>();
      if (batchFile != null) {
        callArgs.add("call");
        callArgs.add(batchFile.toString());
        if (args != null) {
          callArgs.addAll(args);
        }
        callArgs.add("&&");
      }

      callArgs.add((System.getProperty("java.home") + "/bin/java").replace('/', File.separatorChar));
      callArgs.add("-cp");
      callArgs.add(PathManager.getJarPathForClass(ReadEnv.class));
      callArgs.add(ReadEnv.class.getCanonicalName());

      callArgs.add(envFile.toString());
      callArgs.add("||");
      callArgs.add("exit");
      callArgs.add("/B");
      //noinspection SpellCheckingInspection
      callArgs.add("%ERRORLEVEL%");

      List<@NonNls String> cl = new ArrayList<>();
      cl.add("cmd.exe");
      cl.add("/c");
      cl.add(prepareCallArgs(callArgs));
      return runProcessAndReadOutputAndEnvs(cl, batchFile != null ? batchFile.getParent() : null, null, envFile);
    }
    finally {
      try {
        Files.delete(envFile);
      }
      catch (NoSuchFileException ignore) {
      }
      catch (IOException e) {
        Logger.getInstance(EnvironmentUtil.class).warn("Cannot delete temporary file", e);
      }
    }
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
