/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.test;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

/**
 * Environment to run Git.
 * @author Kirill Likhodedov
 */
public class GitTestRunEnv {

  public static final String GIT_EXECUTABLE_ENV = "IDEA_TEST_GIT_EXECUTABLE";
  private String gitExecutable;
  private File myRootDir;

  public GitTestRunEnv(@NotNull File rootDir) {
    gitExecutable = getExecutable();
    myRootDir = rootDir;
  }

  /**
   * Run Git command and return the output.
   * @param command Command to run with parameters
   * @return
   */
  public String run(@NotNull String command, String... params) throws IOException {
    String[] arguments = ArrayUtil.mergeArrays(new String[]{gitExecutable, command}, params);
    log("# " + StringUtil.join(arguments, " "));
    final ProcessBuilder builder = new ProcessBuilder().command(arguments);
    builder.directory(myRootDir);
    builder.redirectErrorStream(true);
    Process clientProcess = builder.start();

    CapturingProcessHandler handler = new CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset());
    ProcessOutput result = handler.runProcess(30*1000);
    if (result.isTimeout()) {
      throw new RuntimeException("Timeout waiting for Git execution");
    }

    log("{ " + result.getExitCode() + "}");
    final String out = result.getStdout().trim();
    if (out.length() > 0) {
      log(out);
    }
    return result.getStdout();
  }

  protected void log(String message) {
    System.out.println(message);
  }

  private String getExecutable() {
    String exec = System.getenv(GIT_EXECUTABLE_ENV);
    if (exec != null && new File(exec).exists()) {
      log("Using Git from GIT_EXECUTABLE_ENV: " + exec);
      return exec;
    }
    File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
    File dir = new File(pluginRoot, "tests/git4idea/tests/data/bin");
    File git = new File(dir, SystemInfo.isWindows ? "git.exe" : "git");
    if (!git.exists()) {
      throw new IllegalStateException("git executable not found");
    }
    log("Using Git from IDEA sources: " + git.getPath());
    return git.getPath();
  }

}
