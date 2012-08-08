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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.push.GitPushSpec
import git4idea.repo.GitRepository
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
/**
 * @author Kirill Likhodedov
 */
@Mixin(GitExecutor)
public class GitTestImpl implements Git {

  @NotNull
  @Override
  public GitCommandResult init(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Set<VirtualFile> untrackedFiles(@NotNull Project project, @NotNull VirtualFile root, @Nullable Collection<VirtualFile> files)
    throws VcsException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public Collection<VirtualFile> untrackedFilesNoChunk(@NotNull Project project,
                                                       @NotNull VirtualFile root,
                                                       @Nullable List<String> relativePaths) throws VcsException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult clone(@NotNull Project project,
                                @NotNull File parentDirectory,
                                @NotNull String url,
                                @NotNull String clonedDirectoryName) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult config(@NotNull GitRepository repository, String... params) {
    cd repository.getRoot().getPath()
    String output = git("config " + params.join(" "))
    int exitCode = output.trim().isEmpty() ? 1 : 0
    return new GitCommandResult(!output.contains("fatal") && exitCode == 0, exitCode, Collections.<String>emptyList(),
                                Arrays.asList(StringUtil.splitByLines(output)))
  }

  @NotNull
  @Override
  GitCommandResult checkAttr(@NotNull GitRepository repository, @NotNull Collection<String> attributes, @NotNull Collection<VirtualFile> files) {
    String root = repository.getRoot().getPath()
    cd root
    String output = git("check-attr " + attributes.join(" ") + " -- " +
                        files.collect({it -> FileUtil.getRelativePath(root, it.path, (char)'/')}).join(" "))
    return new GitCommandResult(!output.contains("fatal"), 0, Collections.<String>emptyList(),
                                Arrays.asList(StringUtil.splitByLines(output)))
  }

  @NotNull
  @Override
  public GitCommandResult merge(@NotNull GitRepository repository,
                                @NotNull String branchToMerge,
                                @Nullable List<String> additionalParams,
                                @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult checkout(@NotNull GitRepository repository,
                                   @NotNull String reference,
                                   @Nullable String newBranch,
                                   boolean force,
                                   @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult checkoutNewBranch(@NotNull GitRepository repository,
                                            @NotNull String branchName,
                                            @Nullable GitLineHandlerListener listener) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult createNewTag(@NotNull GitRepository repository,
                                       @NotNull String tagName,
                                       @Nullable GitLineHandlerListener listener,
                                       @NotNull String reference) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult branchDelete(@NotNull GitRepository repository,
                                       @NotNull String branchName,
                                       boolean force,
                                       @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult branchContains(@NotNull GitRepository repository, @NotNull String commit) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult resetHard(@NotNull GitRepository repository, @NotNull String revision) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult resetMerge(@NotNull GitRepository repository, @Nullable String revision) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult tip(@NotNull GitRepository repository, @NotNull String branchName) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult push(@NotNull GitRepository repository,
                               @NotNull String remote,
                               @NotNull String spec,
                               @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult push(@NotNull GitRepository repository,
                               @NotNull GitPushSpec pushSpec,
                               @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult show(@NotNull GitRepository repository, @NotNull String... params) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult cherryPick(@NotNull GitRepository repository,
                                     @NotNull String hash,
                                     boolean autoCommit,
                                     @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult getUnmergedFiles(@NotNull GitRepository repository) {
    throw new UnsupportedOperationException();
  }

}
