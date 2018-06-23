/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.testFramework.vcs;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Karashevich
 */
public class ExecutableHelper {

  private static final Logger LOG = Logger.getInstance(ExecutableHelper.class);

  private static final String GIT_EXECUTABLE_ENV = "IDEA_TEST_GIT_EXECUTABLE";

  public static String findGitExecutable() {
    return findExecutable("Git", "git", "git.exe", Collections.singletonList(GIT_EXECUTABLE_ENV));
  }

  @NotNull
  public static String findExecutable(@NotNull String programName,
                                      @NotNull String unixExec,
                                      @NotNull String winExec,
                                      @NotNull Collection<String> envs) {
    String exec = findEnvValue(programName, envs);
    if (exec != null) {
      return exec;
    }
    File fileExec = PathEnvironmentVariableUtil.findInPath(SystemInfo.isWindows ? winExec : unixExec);
    if (fileExec != null) {
      return fileExec.getAbsolutePath();
    }
    throw new IllegalStateException(programName + " executable not found. " + (envs.size() > 0 ?
                                                                               "Please define a valid environment variable " +
                                                                               envs.iterator().next() +
                                                                               " pointing to the " +
                                                                               programName +
                                                                               " executable." : ""));
  }

  @Nullable
  public static String findEnvValue(@NotNull String programNameForLog, @NotNull Collection<String> envs) {
    for (String env : envs) {
      String val = System.getenv(env);
      if (val != null && new File(val).canExecute()) {
        debug(String.format("Using %s from %s: %s", programNameForLog, env, val));
        return val;
      }
    }
    return null;
  }

  public static void debug(@NotNull String msg) {
    if (!StringUtil.isEmptyOrSpaces(msg)) {
      LOG.info(msg);
    }
  }
}
