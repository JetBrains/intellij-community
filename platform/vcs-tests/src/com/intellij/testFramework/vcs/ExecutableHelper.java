// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.vcs;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.bazel.runfiles.BazelRunfiles;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Karashevich
 */
public final class ExecutableHelper {

  private static final Logger LOG = Logger.getInstance(ExecutableHelper.class);

  private static final String GIT_EXECUTABLE_ENV = "IDEA_TEST_GIT_EXECUTABLE";

  public static String findGitExecutable() {
    return findExecutable("Git", "git", "git.exe", Collections.singletonList(GIT_EXECUTABLE_ENV));
  }

  public static @NotNull String findExecutable(@NotNull String programName,
                                               @NotNull String unixExec,
                                               @NotNull String winExec,
                                               @NotNull Collection<String> envs) {
    String exec = findEnvValue(programName, envs);
    if (exec != null) {
      return exec;
    }
    var fileExec = PathEnvironmentVariableUtil.findFirst(OS.CURRENT == OS.Windows ? winExec : unixExec);
    if (fileExec != null) {
      return fileExec.toString();
    }
    throw new IllegalStateException(programName + " executable not found. " + (!envs.isEmpty() ?
                                                                               "Please define a valid environment variable " +
                                                                               envs.iterator().next() +
                                                                               " pointing to the " +
                                                                               programName +
                                                                               " executable." : ""));
  }

  /**
   * The executable of the first of {@code envs} that holds a path to it. In a Bazel test, the value can also be the
   * {@code $(rlocationpath)} of the executable.
   */
  public static @Nullable String findEnvValue(@NotNull String programNameForLog, @NotNull Collection<String> envs) {
    for (String env : envs) {
      String val = System.getenv(env);
      if (val == null) continue;
      String exec = new File(val).canExecute() ? val : findRunfile(val);
      if (exec != null) {
        debug(String.format("Using %s from %s: %s", programNameForLog, env, exec));
        return exec;
      }
    }
    return null;
  }

  /**
   * A Bazel test on Windows has only the runfiles manifest, so the {@code $(rlocationpath)} of a {@code data} file is
   * no path on the disk. The runfiles give the path. The result is null outside Bazel, and for a value that names no
   * executable.
   */
  private static @Nullable String findRunfile(@NotNull String rlocationPath) {
    if (!BazelRunfiles.INSTANCE.isRunningFromBazel()) return null;
    File file = BazelRunfiles.resolveRunfilePath(rlocationPath).toFile();
    return file.canExecute() ? file.getPath() : null;
  }

  public static void debug(@NotNull String msg) {
    if (!StringUtil.isEmptyOrSpaces(msg)) {
      LOG.info(msg);
    }
  }
}
