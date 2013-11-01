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

import com.intellij.openapi.vcs.Executor;
import git4idea.repo.GitRepository;

import java.util.Arrays;
import java.util.List;

/**
 *
 * @author Kirill Likhodedov
 */
public class GitExecutor extends Executor {

  private static final String GIT_EXECUTABLE_ENV = "IDEA_TEST_GIT_EXECUTABLE";
  private static final String TEAMCITY_GIT_EXECUTABLE_ENV = "TEAMCITY_GIT_PATH";

  private static final int MAX_RETRIES = 3;
  private static boolean myVersionPrinted;
  public static final String GIT_EXECUTABLE = findGitExecutable();

  private static String findGitExecutable() {
    return findExecutable("Git", "git", "git.exe", Arrays.asList(GIT_EXECUTABLE_ENV, TEAMCITY_GIT_EXECUTABLE_ENV));
  }

  public static String git(String command) {
    printVersionTheFirstTime();
    List<String> split = splitCommandInParameters(command);
    split.add(0, GIT_EXECUTABLE);
    log("git " + command);
    for (int attempt = 0; attempt < 3; attempt++) {
      String stdout = run(split);
      if (stdout.contains("fatal") && stdout.contains("Unable to create") && stdout.contains(".git/index.lock")) {
        if (attempt > MAX_RETRIES) {
          throw new RuntimeException("fatal error during execution of Git command: $command");
        }
      }
      else {
        return stdout;
      }
    }
    throw new RuntimeException("fatal error during execution of Git command: $command");
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

  private static void printVersionTheFirstTime() {
    if (!myVersionPrinted) {
      myVersionPrinted = true;
      git("version");
    }
  }

}
