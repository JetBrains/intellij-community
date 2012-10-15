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
package git4idea.test

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.hash.HashMap
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.history.browser.GitCommit
import git4idea.push.GitPushSpec
import git4idea.repo.GitRepository
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import static git4idea.test.MockGit.OperationName.CHERRY_PICK
import static git4idea.test.MockGit.OperationName.GET_UNMERGED_FILES

/**
 * 
 * @author Kirill Likhodedov
 *
 * @deprecated Use {@link GitTestImpl} - prefer fair Git to the simulation.
 */
@Deprecated
class MockGit implements Git {

  public static final GitCommandResult FAKE_SUCCESS_RESULT = new GitCommandResult(true, 0, Collections.emptyList(), Collections.emptyList())
  private final Map<OperationName, Queue<OperationExecutor>> myExecutors = new HashMap<OperationName, Queue<OperationExecutor>>()

  public enum OperationName {
    CHERRY_PICK,
    GET_UNMERGED_FILES;
  }

  /**
   * Register executors for specific operations. These are put into queues, i.e. once operation is called, the executor is popped out of the
   * queue. If the queue is empty or certain operation, then it is executed as by default.
   */
  void registerOperationExecutors(OperationExecutor... executors) {
    for (OperationExecutor executor : executors) {
      OperationName name = executor.getName()
      Queue<OperationExecutor> exs = myExecutors.get(name)
      if (exs == null) {
        exs = new ArrayDeque<OperationExecutor>()
        myExecutors.put(name, exs)
      }
      exs.add(executor)
    }
  }

  @NotNull
  @Override
  GitCommandResult init(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitLineHandlerListener... listeners) {
    new File(root.path, ".git").mkdir()
    FAKE_SUCCESS_RESULT
  }

  @NotNull
  @Override
  Set<VirtualFile> untrackedFiles(@NotNull Project project, @NotNull VirtualFile root, Collection<VirtualFile> files) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  Collection<VirtualFile> untrackedFilesNoChunk(@NotNull Project project, @NotNull VirtualFile root, List<String> relativePaths) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult clone(@NotNull Project project, @NotNull File parentDirectory, @NotNull String url, @NotNull String clonedDirectoryName) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult config(@NotNull GitRepository repository, String... params) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult diff(GitRepository repository, List<String> parameters, String range) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult merge(@NotNull GitRepository repository, @NotNull String branchToMerge, @Nullable List<String> additionalParams,
                         @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult checkout(@NotNull GitRepository repository, @NotNull String reference, String newBranch, boolean force, @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult checkoutNewBranch(@NotNull GitRepository repository, @NotNull String branchName, GitLineHandlerListener listener) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult createNewTag(@NotNull GitRepository repository, @NotNull String tagName, GitLineHandlerListener listener, @NotNull String reference) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult branchDelete(@NotNull GitRepository repository, @NotNull String branchName, boolean force, @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult branchContains(@NotNull GitRepository repository, @NotNull String commit) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult resetHard(@NotNull GitRepository repository, @NotNull String revision) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult resetMerge(@NotNull GitRepository repository, String revision) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult tip(@NotNull GitRepository repository, @NotNull String branchName) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult push(@NotNull GitRepository repository, @NotNull String remote, @NotNull String spec, @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult push(@NotNull GitRepository repository, @NotNull GitPushSpec pushSpec, @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult show(@NotNull GitRepository repository, @NotNull String... params) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult cherryPick(@NotNull GitRepository repository, @NotNull String hash, boolean autoCommit, @NotNull GitLineHandlerListener... listeners) {
    GitCommandResult result = callExecutor(CHERRY_PICK)
    if (result != null) {
      produceOutput(result.getOutputAsJoinedString(), listeners)
      return result;
    }
    ((GitLightRepository)repository).cherryPick("cherry-pick from $hash")
    return FAKE_SUCCESS_RESULT
  }

  @NotNull
  @Override
  GitCommandResult getUnmergedFiles(@NotNull GitRepository repository) {
    GitCommandResult result = callExecutor(GET_UNMERGED_FILES)
    if (result != null) {
      return result;
    }
    return FAKE_SUCCESS_RESULT
  }

  @NotNull
  @Override
  GitCommandResult checkAttr(@NotNull GitRepository repository, @NotNull Collection<String> attributes, @NotNull Collection<VirtualFile> files) {

  }

  @NotNull
  @Override
  GitCommandResult stashSave(@NotNull GitRepository repository, @NotNull String message) {
    throw new UnsupportedOperationException()
  }

  @NotNull
  @Override
  GitCommandResult stashPop(@NotNull GitRepository repository, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  List<GitCommit> history(GitRepository repository, String range) {
    throw new UnsupportedOperationException()
  }

  private void produceOutput(String output, GitLineHandlerListener... listeners) {
    for (String line : output.split("\n")) { // for simplicity all output goes to OUTPUT, no ERROR
      listeners.each { it.onLineAvailable(line, ProcessOutputTypes.STDOUT) }
    }
  }

  @Nullable
  private GitCommandResult callExecutor(OperationName operationName) {
    Queue<OperationExecutor> cherryPickExecutors = myExecutors.get(operationName)
    if (cherryPickExecutors != null && !cherryPickExecutors.isEmpty()) {
      OperationExecutor executor = cherryPickExecutors.poll()
      return executor.execute()
    }
    return null;
  }

  public static class SimpleErrorOperationExecutor implements OperationExecutor {

    String myOutput
    MockGit.OperationName myOperationName

    SimpleErrorOperationExecutor(MockGit.OperationName operationName, String output) {
      myOutput = output;
      myOperationName = operationName
    }

    @Override
    GitCommandResult execute() {
      return result(myOutput, false);
    }

    @Override
    MockGit.OperationName getName() {
      return myOperationName
    }
  }

  public static class SimpleSuccessOperationExecutor implements OperationExecutor {

    String myOutput
    MockGit.OperationName myOperationName

    SimpleSuccessOperationExecutor(MockGit.OperationName operationName, String output) {
      myOutput = output;
      myOperationName = operationName
    }

    @Override
    GitCommandResult execute() {
      return result(myOutput, true);
    }

    @Override
    MockGit.OperationName getName() {
      return myOperationName
    }
  }

  private static GitCommandResult result(String output, boolean success) {
    new GitCommandResult(success, success ? 0 : 127, Collections.emptyList(), Collections.singletonList(output))
  }

  public static class SuccessfulCherryPickExecutor implements OperationExecutor {

    GitRepository myRepository
    GitCommit myOriginalCommit

    SuccessfulCherryPickExecutor(GitRepository repository, GitCommit originalCommit) {
      myRepository = repository;
      myOriginalCommit = originalCommit
    }

    @Override
    GitCommandResult execute() {
      ((GitLightRepository)myRepository).cherryPick(commitMessageForCherryPick(myOriginalCommit))
      return FAKE_SUCCESS_RESULT
    }

    @Override
    OperationName getName() {
      return CHERRY_PICK
    }
  }

  static String commitMessageForCherryPick(GitCommit commit) {
    "$commit.subject\n(cherry-picked from ${commit.shortHash.getString()})"
  }

}

interface OperationExecutor {
  GitCommandResult execute();
  MockGit.OperationName getName();
}
