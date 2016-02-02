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
package git4idea.commands;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitCommit;
import git4idea.branch.GitRebaseParams;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface Git {

  /**
   * A generic method to run a Git command, when existing methods like {@link #fetch(GitRepository, String, String, List, String...)}
   * are not sufficient.
   * @param handlerConstructor this is needed, since the operation may need to repeat (e.g. in case of authentication failure).
   *                           make sure to supply a stateless constructor.
   */
  @NotNull
  GitCommandResult runCommand(@NotNull Computable<GitLineHandler> handlerConstructor);

  /**
   * A generic method to run a Git command, when existing methods are not sufficient. <br/>
   * Can be used instead of {@link #runCommand(Computable)} if the operation will not need to be repeated for sure
   * (e.g. it is a completely local operation).
   */
  @NotNull
  GitCommandResult runCommand(@NotNull GitLineHandler handler);

  @NotNull
  GitCommandResult init(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitLineHandlerListener... listeners);

  @NotNull
  Set<VirtualFile> untrackedFiles(@NotNull Project project, @NotNull VirtualFile root,
                                  @Nullable Collection<VirtualFile> files) throws VcsException;

  // relativePaths are guaranteed to fit into command line length limitations.
  @NotNull
  Collection<VirtualFile> untrackedFilesNoChunk(@NotNull Project project, @NotNull VirtualFile root,
                                                @Nullable List<String> relativePaths) throws VcsException;

  @NotNull
  GitCommandResult clone(@NotNull Project project, @NotNull File parentDirectory, @NotNull String url, @NotNull String clonedDirectoryName,
                         @NotNull GitLineHandlerListener... progressListeners);

  @NotNull
  GitCommandResult config(@NotNull GitRepository repository, String... params);

  @NotNull
  GitCommandResult diff(@NotNull GitRepository repository, @NotNull List<String> parameters, @NotNull String range);

  @NotNull
  GitCommandResult merge(@NotNull GitRepository repository, @NotNull String branchToMerge, @Nullable List<String> additionalParams,
                         @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult checkout(@NotNull GitRepository repository,
                            @NotNull String reference,
                            @Nullable String newBranch,
                            boolean force,
                            boolean detach,
                            @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult checkoutNewBranch(@NotNull GitRepository repository, @NotNull String branchName,
                                     @Nullable GitLineHandlerListener listener);

  @NotNull
  GitCommandResult createNewTag(@NotNull GitRepository repository, @NotNull String tagName,
                                @Nullable GitLineHandlerListener listener, @NotNull String reference);

  @NotNull
  GitCommandResult branchDelete(@NotNull GitRepository repository, @NotNull String branchName, boolean force,
                                @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult branchContains(@NotNull GitRepository repository, @NotNull String commit);

  @NotNull
  GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName);

  @NotNull
  GitCommandResult renameBranch(@NotNull GitRepository repository,
                                @NotNull String currentName,
                                @NotNull String newName,
                                @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult reset(@NotNull GitRepository repository, @NotNull GitResetMode mode, @NotNull String target,
                         @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult resetMerge(@NotNull GitRepository repository, @Nullable String revision);

  @NotNull
  GitCommandResult tip(@NotNull GitRepository repository, @NotNull String branchName);

  @NotNull
  GitCommandResult push(@NotNull GitRepository repository, @NotNull String remote, @Nullable String url, @NotNull String spec,
                        boolean updateTracking, @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult push(@NotNull GitRepository repository,
                        @NotNull GitRemote remote,
                        @NotNull String spec,
                        boolean force,
                        boolean updateTracking,
                        @Nullable String tagMode,
                        GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult show(@NotNull GitRepository repository, @NotNull String... params);

  @NotNull
  GitCommandResult cherryPick(@NotNull GitRepository repository, @NotNull String hash, boolean autoCommit,
                              @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult getUnmergedFiles(@NotNull GitRepository repository);

  @NotNull
  GitCommandResult checkAttr(@NotNull GitRepository repository, @NotNull Collection<String> attributes,
                             @NotNull Collection<VirtualFile> files);

  @NotNull
  GitCommandResult stashSave(@NotNull GitRepository repository, @NotNull String message);

  @NotNull
  GitCommandResult stashPop(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners);

  @NotNull
  List<GitCommit> history(@NotNull GitRepository repository, @NotNull String range);

  @NotNull
  GitCommandResult fetch(@NotNull GitRepository repository,
                         @NotNull GitRemote remote,
                         @NotNull List<GitLineHandlerListener> listeners,
                         String... params);

  @NotNull
  GitCommandResult addRemote(@NotNull GitRepository repository, @NotNull String name, @NotNull String url);

  @NotNull
  GitCommandResult lsRemote(@NotNull Project project, @NotNull File workingDir, @NotNull String url);

  @NotNull
  GitCommandResult lsRemote(@NotNull Project project,
                            @NotNull VirtualFile workingDir,
                            @NotNull GitRemote remote,
                            String... additionalParameters);

  @NotNull
  GitCommandResult remotePrune(@NotNull GitRepository repository, @NotNull GitRemote remote);

  @NotNull
  GitCommandResult rebase(@NotNull GitRepository repository,
                          @NotNull GitRebaseParams parameters,
                          @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult rebaseAbort(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult rebaseContinue(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult rebaseSkip(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners);

}
