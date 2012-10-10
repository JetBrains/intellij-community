/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

  private static final String GIT_EXECUTABLE_ENV = "IDEA_TEST_GIT_EXECUTABLE";
  private static final String TEAMCITY_GIT_EXECUTABLE_ENV = "TEAMCITY_GIT_PATH";
  private static String ourGitExecutable;

  private File myRootDir;

  private int myRetryCount;
  private static final int MAX_RETRIES = 3;


  public GitTestRunEnv(@NotNull File rootDir) {
    if (ourGitExecutable == null) {
      ourGitExecutable = findExecutable();
      outputGitVersion();
    }

    myRootDir = rootDir;
  }

  private void outputGitVersion() {
    try {
      run(false, "version");
    }
    catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Run Git command and return the output.
   * @param command Command to run with parameters
   * @return
   */
  public String run(@NotNull String command, String... params) throws IOException {
    return run(false, command, params);
  }

  public String run(boolean silent, @NotNull String command, String... params) throws IOException {
    String[] arguments = ArrayUtil.mergeArrays(new String[]{ourGitExecutable, command}, params);
    if (!silent) {
      log("# git " + command + " " + StringUtil.join(params, " "));
    }
    final ProcessBuilder builder = new ProcessBuilder().command(arguments);
    builder.directory(myRootDir);
    builder.redirectErrorStream(true);
    Process clientProcess = builder.start();

    CapturingProcessHandler handler = new CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset());
    ProcessOutput result = handler.runProcess(30*1000);
    if (result.isTimeout()) {
      throw new RuntimeException("Timeout waiting for Git execution");
    }

    if (result.getExitCode() != 0) {
      log("{" + result.getExitCode() + "}");
    }
    String stdout = result.getStdout();
    final String out = stdout.trim();
    if (out.length() > 0) {
      log(out);
    }
    if (stdout.contains("fatal") && stdout.contains("Unable to create") && stdout.contains(".git/index.lock")) {
      if (myRetryCount <= MAX_RETRIES) {// retry
        myRetryCount++;
        return run(silent, command, params);
      }
      myRetryCount = 0;
      throw new RuntimeException("fatal error during execution of Git command: " + StringUtil.join(arguments, " "));
    }
    myRetryCount = 0;
    return stdout;
  }

  protected static void log(String message) {
    System.out.println(message);
  }

  private static String findExecutable() {
    String exec = System.getenv(GIT_EXECUTABLE_ENV);
    if (exec != null && new File(exec).canExecute()) {
      log("Using Git from IDEA_TEST_GIT_EXECUTABLE: " + exec);
      return exec;
    }
    exec = System.getenv(TEAMCITY_GIT_EXECUTABLE_ENV);
    if (exec != null && new File(exec).canExecute()) {
      log("Using Git from TEAMCITY_GIT_PATH: " + exec);
      return exec;
    }

    String path = System.getenv(SystemInfo.isWindows ? "Path" : "PATH");
    if (path != null) {
      String name = SystemInfo.isWindows ? "git.exe" : "git";
      for (String dir : path.split(File.pathSeparator)) {
        File file = new File(dir, name);
        if (file.canExecute()) {
          log("Using Git from PATH: " + exec);
          return file.getPath();
        }
      }
    }

    throw new IllegalStateException("Git executable not found. " +
                                    "Please define IDEA_TEST_GIT_EXECUTABLE environment variable pointing to the Git executable.");
  }

}
