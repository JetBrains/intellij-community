/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.Executor;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;

/**
 * @author Kirill Likhodedov
 */
public class GitExecutor extends Executor {

  private static final String GIT_EXECUTABLE_ENV = "IDEA_TEST_GIT_EXECUTABLE";
  private static final String TEAMCITY_GIT_EXECUTABLE_ENV = "TEAMCITY_GIT_PATH";

  private static final int MAX_RETRIES = 3;
  private static boolean myVersionPrinted;

  private static String findGitExecutable() {
    return findExecutable("Git", "git", "git.exe", Arrays.asList(GIT_EXECUTABLE_ENV, TEAMCITY_GIT_EXECUTABLE_ENV));
  }

  //using inner class to avoid extra work during class loading of unrelated tests
  public static class PathHolder {
    public static final String GIT_EXECUTABLE = findGitExecutable();
  }

  public static String git(String command) {
    return git(command, false);
  }

  public static String git(String command, boolean ignoreNonZeroExitCode) {
    printVersionTheFirstTime();
    return doCallGit(command, ignoreNonZeroExitCode);
  }

  @NotNull
  private static String doCallGit(String command, boolean ignoreNonZeroExitCode) {
    List<String> split = splitCommandInParameters(command);
    split.add(0, PathHolder.GIT_EXECUTABLE);
    File workingDir = ourCurrentDir();
    debug("[" + workingDir.getName() + "] # git " + command);
    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      String stdout;
      try {
        stdout = run(workingDir, split, ignoreNonZeroExitCode);
        if (!isIndexLockFileError(stdout)) {
          return stdout;
        }
      }
      catch (ExecutionException e) {
        stdout = e.getOutput();
        if (!isIndexLockFileError(stdout)) {
          throw e;
        }
      }
      LOG.info("Index lock file error, attempt #" + attempt + ": " + stdout);
    }
    throw new RuntimeException("fatal error during execution of Git command: $command");
  }

  private static boolean isIndexLockFileError(@NotNull String stdout) {
    return stdout.contains("fatal") && stdout.contains("Unable to create") && stdout.contains(".git/index.lock");
  }

  public static String git(GitRepository repository, String command) {
    if (repository != null) {
      cd(repository);
    }
    return git(command);
  }

  public static String git(String formatString, String... args) {
    return git(String.format(formatString, args));
  }

  public static void cd(GitRepository repository) {
    cd(repository.getRoot().getPath());
  }

  public static void add() {
    add(".");
  }

  public static void add(@NotNull String path) {
    git("add --verbose " + path);
  }

  @NotNull
  public static String addCommit(@NotNull String message) {
    add();
    return commit(message);
  }

  public static void checkout(@NotNull String... params) {
    git("checkout " + StringUtil.join(params, " "));
  }

  public static String commit(@NotNull String message) {
    git("commit -m '" + message + "'");
    return last();
  }

  @NotNull
  public static String tac(@NotNull String file) {
    touch(file, "content" + Math.random());
    return addCommit("touched " + file);
  }

  @NotNull
  public static String modify(@NotNull String file) throws IOException {
    overwrite(file, "content" + Math.random());
    return addCommit("modified " + file);
  }

  @NotNull
  public static String last() {
    return git("log -1 --pretty=%H");
  }

  @NotNull
  public static String log(String... params) {
    return git("log " + StringUtil.join(params, " "));
  }

  public static void mv(String fromPath, String toPath) {
    git("mv " + fromPath + " " + toPath);
  }

  public static void mv(File from, File to) {
    mv(from.getPath(), to.getPath());
  }

  private static void printVersionTheFirstTime() {
    if (!myVersionPrinted) {
      myVersionPrinted = true;
      doCallGit("version", false);
    }
  }

  @NotNull
  public static TestFile file(@NotNull String fileName) {
    File f = child(fileName);
    return new TestFile(f);
  }

  public static class TestFile {
    @NotNull private final File myFile;

    private TestFile(@NotNull File file) {
      myFile = file;
    }

    public TestFile append(@NotNull String content) throws IOException {
      FileUtil.writeToFile(myFile, content.getBytes(), true);
      return this;
    }

    public TestFile write(@NotNull String content) throws IOException {
      FileUtil.writeToFile(myFile, content.getBytes(), false);
      return this;
    }

    public TestFile create(@NotNull String content) throws IOException {
      assertNotExists();
      FileUtil.writeToFile(myFile, content.getBytes(), false);
      return this;
    }

    public TestFile assertNotExists() {
      assertFalse(myFile.exists());
      return this;
    }

    public TestFile add() {
      GitExecutor.add(myFile.getPath());
      return this;
    }

    public TestFile commit() {
      return commit("Some message");
    }

    public TestFile commit(String message) {
      GitExecutor.commit(message);
      return this;
    }

    public boolean exists() {
      return myFile.exists();
    }
  }
}
