/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package hg4idea.test;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.ShellCommand;

import java.io.File;
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
    String exec = findEnvValue(programName, Collections.singletonList(HG_EXECUTABLE_ENV));
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
    List<String> split = splitCommandInParameters(command);
    split.add(0, HG_EXECUTABLE);
    debug("hg " + command);
    HgCommandResult result;
    try {
      result = new ShellCommand(split, pwd(), null).execute(false, false);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    int exitValue = result.getExitValue();
    if (!ignoreNonZeroExitCode && exitValue != 0) {
      throw new RuntimeException(result.getRawError());
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
}
