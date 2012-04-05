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
import git4idea.push.GitPushSpec
import git4idea.repo.GitRepository
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

/**
 * 
 * @author Kirill Likhodedov
 */
class MockGit implements Git {

  public static final GitCommandResult FAKE_SUCCESS_RESULT = new GitCommandResult(true, 0, Collections.emptyList(), Collections.emptyList())
  private final Map<GitRepository, GitLightRepository> myRepositories = new HashMap<GitRepository, GitLightRepository>();
  private final Map<OperationName, List<OperationExecutor>> myExecutors = new HashMap<OperationName, List<OperationExecutor>>()

  public static interface OperationExecutor {
    GitCommandResult execute();
    OperationName getName();
  }

  public enum OperationName {
    CHERRY_PICK,
    GET_UNMERGED_FILES;
  }

  GitLightRepository get(GitRepository repository) {
    return myRepositories.get(repository)
  }

  /**
   * Register executors for specific operations. These are put into queues, i.e. once operation is called, the executor is popped out of the
   * queue. If the queue is empty or certain operation, then it is executed as by default.
   */
  void registerOperationExecutors(OperationExecutor... executors) {
    for (OperationExecutor executor : executors) {
      OperationName name = executor.getName()
      List<OperationExecutor> exs = myExecutors.get(name)
      if (exs == null) {
        exs = new ArrayList<OperationExecutor>()
        myExecutors.put(name, exs)
      }
      exs.add(executor)
    }
  }

  @Override
  GitCommandResult init(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitLineHandlerListener... listeners) {
    new File(root.path, ".git").mkdir()
    FAKE_SUCCESS_RESULT
  }

  @Override
  Set<VirtualFile> untrackedFiles(Project project, VirtualFile root, Collection<VirtualFile> files) {
    throw new UnsupportedOperationException()
  }

  @Override
  Collection<VirtualFile> untrackedFilesNoChunk(Project project, VirtualFile root, List<String> relativePaths) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult clone(Project project, File parentDirectory, String url, String clonedDirectoryName) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult merge(GitRepository repository, String branchToMerge, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult checkout(GitRepository repository, String reference, String newBranch, boolean force, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult checkoutNewBranch(GitRepository repository, String branchName, GitLineHandlerListener listener) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult createNewTag(GitRepository repository, String tagName, GitLineHandlerListener listener, String reference) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult branchDelete(GitRepository repository, String branchName, boolean force, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult branchContains(GitRepository repository, String commit) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult branchCreate(GitRepository repository, String branchName) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult resetHard(GitRepository repository, String revision) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult resetMerge(GitRepository repository, String revision) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult tip(GitRepository repository, String branchName) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult push(GitRepository repository, String remote, String spec, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult push(GitRepository repository, GitPushSpec pushSpec, GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException()
  }

  @Override
  GitCommandResult cherryPick(GitRepository repository, String hash, boolean autoCommit, GitLineHandlerListener... listeners) {
    GitCommandResult result = callExecutor(OperationName.CHERRY_PICK)
    if (result != null) {
      produceOutput(result.getErrorOutputAsJoinedString(), listeners)
      return result;
    }
    myRepositories.get(repository).cherryPick(hash, "message")
    return FAKE_SUCCESS_RESULT
  }

  @Override
  GitCommandResult getUnmergedFiles(GitRepository repository) {
    GitCommandResult result = callExecutor(OperationName.CHERRY_PICK)
    if (result != null) {
      return result;
    }
    return FAKE_SUCCESS_RESULT
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

  public static class SimpleErrorOperationExecutor implements MockGit.OperationExecutor {

    String myOutput
    MockGit.OperationName myOperationName

    SimpleErrorOperationExecutor(MockGit.OperationName operationName, String output) {
      myOutput = output;
      myOperationName = operationName
    }

    @Override
    GitCommandResult execute() {
      return errorResult(myOutput);
    }

    @Override
    MockGit.OperationName getName() {
      return myOperationName
    }
  }

  private static GitCommandResult errorResult(String output) {
    new GitCommandResult(false, 127, Collections.emptyList(), Collections.singletonList(output))
  }

  public static class SuccessfulOperationExecutor implements OperationExecutor {
    OperationName myOperationName

    SuccessfulOperationExecutor(OperationName operationName) {
      myOperationName = operationName
    }

    @Override
    GitCommandResult execute() {
      return FAKE_SUCCESS_RESULT
    }

    @Override
    OperationName getName() {
      return myOperationName
    }
  }

}
