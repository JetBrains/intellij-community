// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package hg4idea.test;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.vcs.ExecutableHelper;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.ShellCommand;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vcs.Executor.*;

public class HgExecutor {

  private static final String HG_EXECUTABLE_ENV = "IDEA_TEST_HG_EXECUTABLE";

  @NotNull private static final String HG_EXECUTABLE = doFindExecutable();

  private static String doFindExecutable() {
    final String programName = "hg";
    final String unixExec = "hg";
    final String winExec = "hg.exe";
    String exec = ExecutableHelper.findEnvValue(programName, Collections.singletonList(HG_EXECUTABLE_ENV));
    if (exec != null) {
      return exec;
    }
    exec = findInSources(programName, unixExec, winExec);
    if (exec != null) {
      return exec;
    }
    throw new IllegalStateException(programName + " executable not found.");
  }

  private static String findInSources(String programName, String unixExec, String winExec) {
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("hg4idea"));
    File bin = new File(pluginRoot, FileUtil.toSystemDependentName("testData/bin"));
    File exec = new File(bin, SystemInfo.isWindows ? winExec : unixExec);
    if (exec.exists() && exec.canExecute()) {
      debug("Using " + programName + " from test data");
      return exec.getPath();
    }
    return null;
  }

  public static String hg(@NotNull String command) {
    return hg(command, false);
  }

  public static String hg(@NotNull String command, boolean ignoreNonZeroExitCode) {
    List<String> split = new ArrayList<>();
    ContainerUtil.addAll(split, HG_EXECUTABLE, "--config", "ui.timeout=10");
    split.addAll(splitCommandInParameters(command));
    debug("hg " + command);
    HgCommandResult result;
    try {
      int attempt = 0;
      do {
        result = new ShellCommand(split, pwd(), null).execute(false, false);
      }
      while ((HgErrorUtil.isWLockError(result) || isUsedByAnotherProcess(result)) && attempt++ < 2);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    int exitValue = result.getExitValue();
    if (!ignoreNonZeroExitCode && exitValue != 0) {
      debug("exit code: " + exitValue + " " + result.getRawOutput());
      throw new HgCommandFailedException(exitValue, result.getRawOutput(), result.getRawError());
    }
    return result.getRawOutput();
  }

  public static void updateProject() {
    hg("pull");
    hg("update", true);
    hgMergeWith("");
  }

  public static void hgMergeWith(@NotNull String mergeWith) {
    hg("merge " + mergeWith, true);
  }

  @NotNull
  public static String getHgExecutable() {
    return HG_EXECUTABLE;
  }

  private static boolean isUsedByAnotherProcess(@Nullable HgCommandResult result) {
    //abort: process cannot access the file because it is being used by another process
    if (result == null) return false;
    return HgErrorUtil.isAbort(result) && result.getRawError().contains("used by another process");
  }

  public static class HgCommandFailedException extends RuntimeException {
    private final int myExitCode;
    private final @NotNull String myRawOutput;
    private final @NotNull String myRawError;

    private HgCommandFailedException(int exitCode, @NotNull String rawOutput, @NotNull String rawError) {
      super(String.format("output: \"%s\"; error: \"%s\"", rawOutput, rawError));
      myExitCode = exitCode;
      myRawOutput = rawOutput;
      myRawError = rawError;
    }

    public int getExitCode() {
      return myExitCode;
    }

    public @NotNull String getRawOutput() {
      return myRawOutput;
    }

    public @NotNull String getRawError() {
      return myRawError;
    }
  }
}
