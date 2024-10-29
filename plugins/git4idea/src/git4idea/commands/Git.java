// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import git4idea.branch.GitRebaseParams;
import git4idea.push.GitPushParams;
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
  static @NotNull Git getInstance() {
    return ApplicationManager.getApplication().getService(Git.class);
  }

  /**
   * A generic method to run a Git command, when existing methods like {@link #fetch(GitRepository, GitRemote, List, String...)}
   * are not sufficient.
   *
   * @param handlerConstructor this is needed, since the operation may need to repeat (e.g. in case of authentication failure).
   *                           make sure to supply a stateless constructor.
   */
  @NotNull
  GitCommandResult runCommand(@NotNull Computable<? extends GitLineHandler> handlerConstructor);

  /**
   * A generic method to run a Git command, when existing methods are not sufficient. <br/>
   * Can be used instead of {@link #runCommand(Computable)} if the operation will not need to be repeated for sure
   * (e.g. it is a completely local operation).
   */
  @NotNull
  GitCommandResult runCommand(@NotNull GitLineHandler handler);

  /**
   * A generic method to run a Git command without collecting result, when existing methods are not sufficient. <br/>
   * <p>
   * Prefer this method to the standard {@link #runCommand(GitLineHandler)} if a large amount of output is expected,
   * e.g. when reading a large block of the {@code git log} output: collecting results would lead to huge memory allocation,
   * so it's better to add a separate {@link GitLineHandlerListener} and process the output line by line instead. <br/>
   */
  @NotNull
  GitCommandResult runCommandWithoutCollectingOutput(@NotNull GitLineHandler handler);

  @NotNull
  GitCommandResult init(@NotNull Project project, @NotNull VirtualFile root, GitLineHandlerListener @NotNull ... listeners);

  Set<FilePath> ignoredFilePaths(@NotNull Project project, @NotNull VirtualFile root, @Nullable Collection<? extends FilePath> paths) throws VcsException;

  Set<FilePath> ignoredFilePathsNoChunk(@NotNull Project project, @NotNull VirtualFile root, @Nullable List<String> paths) throws VcsException;

  @Deprecated
  @NotNull
  Set<VirtualFile> untrackedFiles(@NotNull Project project, @NotNull VirtualFile root,
                                  @Nullable Collection<? extends VirtualFile> files) throws VcsException;

  Set<FilePath> untrackedFilePaths(@NotNull Project project, @NotNull VirtualFile root,
                                  @Nullable Collection<FilePath> files) throws VcsException;

  @NotNull
  Collection<FilePath> untrackedFilePathsNoChunk(@NotNull Project project, @NotNull VirtualFile root,
                                                @Nullable List<String> relativePaths) throws VcsException;

  @NotNull
  default GitCommandResult clone(@Nullable Project project,
                                 @NotNull File parentDirectory,
                                 @NotNull String url,
                                 @NotNull String clonedDirectoryName,
                                 GitLineHandlerListener @NotNull ... progressListeners) {
    return clone(project, parentDirectory, url, clonedDirectoryName, null, progressListeners);
  }

  @NotNull
  GitCommandResult clone(@Nullable Project project,
                         @NotNull File parentDirectory,
                         @NotNull String url,
                         @NotNull String clonedDirectoryName,
                         @Nullable GitShallowCloneOptions shallowCloneOptions,
                         GitLineHandlerListener @NotNull ... progressListeners);

  @NotNull
  GitCommandResult config(@NotNull GitRepository repository, String... params);

  @NotNull
  GitCommandResult diff(@NotNull GitRepository repository, @NotNull List<String> parameters, @NotNull String range);

  @NotNull
  GitCommandResult merge(@NotNull GitRepository repository, @NotNull String branchToMerge, @Nullable List<String> additionalParams,
                         GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitCommandResult checkout(@NotNull GitRepository repository,
                            @NotNull String reference,
                            @Nullable String newBranch,
                            boolean force,
                            boolean detach,
                            GitLineHandlerListener @NotNull ... listeners);

  @NotNull
   GitCommandResult checkout(@NotNull GitRepository repository,
                                    @NotNull String reference,
                                    @Nullable String newBranch,
                                    boolean force,
                                    boolean detach,
                                    boolean withReset,
                                    GitLineHandlerListener @NotNull ... listeners);
  @NotNull
  GitCommandResult checkoutNewBranch(@NotNull GitRepository repository, @NotNull String branchName,
                                     @Nullable GitLineHandlerListener listener);

  @NotNull
  GitCommandResult createNewTag(@NotNull GitRepository repository, @NotNull String tagName,
                                @Nullable GitLineHandlerListener listener, @NotNull String reference);

  @NotNull
  GitCommandResult deleteTag(@NotNull GitRepository repository, @NotNull String tagName,
                             GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitCommandResult branchDelete(@NotNull GitRepository repository, @NotNull String branchName, boolean force,
                                GitLineHandlerListener @NotNull ... listeners);

  /**
   * Create branch without checking it out: <br/>
   * <pre>    git branch &lt;branchName&gt; &lt;startPoint&gt;</pre>
   */
  @NotNull
  GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName, @NotNull String startPoint);


  @NotNull
  GitCommandResult setUpstream(@NotNull GitRepository repository,
                               @NotNull String upstreamBranchName,
                               @NotNull String branchName);
  @NotNull
  GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName, @NotNull String startPoint, boolean force);

  @NotNull
  GitCommandResult renameBranch(@NotNull GitRepository repository,
                                @NotNull String currentName,
                                @NotNull String newName,
                                GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitCommandResult reset(@NotNull GitRepository repository, @NotNull GitResetMode mode, @NotNull String target,
                         GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitCommandResult resetMerge(@NotNull GitRepository repository, @Nullable String revision);

  @NotNull
  GitCommandResult tip(@NotNull GitRepository repository, @NotNull String branchName);

  @NotNull
  GitCommandResult push(@NotNull GitRepository repository, @NotNull String remote, @Nullable String url, @NotNull String spec,
                        boolean updateTracking, GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitCommandResult push(@NotNull GitRepository repository,
                        @NotNull GitPushParams pushParams,
                        GitLineHandlerListener... listeners);

  @NotNull
  GitCommandResult show(@NotNull GitRepository repository, String @NotNull ... params);

  @NotNull
  GitCommandResult cherryPick(@NotNull GitRepository repository,
                              @NotNull String hash,
                              boolean autoCommit,
                              boolean addCherryPickedFromSuffix,
                              GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitCommandResult getUnmergedFiles(@NotNull GitRepository repository);

  @NotNull
  GitCommandResult checkAttr(@NotNull GitRepository repository, @NotNull Collection<String> attributes,
                             @NotNull Collection<? extends VirtualFile> files);

  @NotNull
  GitCommandResult stashSave(@NotNull GitRepository repository, @NotNull String message);

  @NotNull
  GitCommandResult stashPop(@NotNull GitRepository repository, GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitCommandResult fetch(@NotNull GitRepository repository,
                         @NotNull GitRemote remote,
                         @NotNull List<? extends GitLineHandlerListener> listeners,
                         String... params);

  @NotNull
  GitCommandResult addRemote(@NotNull GitRepository repository, @NotNull String name, @NotNull String url);

  @NotNull
  GitCommandResult removeRemote(@NotNull GitRepository repository, @NotNull GitRemote remote);

  @NotNull
  GitCommandResult renameRemote(@NotNull GitRepository repository, @NotNull String oldName, @NotNull String newName);

  @NotNull
  GitCommandResult setRemoteUrl(@NotNull GitRepository repository, @NotNull String remoteName, @NotNull String newUrl);

  @NotNull
  GitCommandResult lsRemote(@NotNull Project project, @NotNull File workingDir, @NotNull String url);

  @NotNull
  GitCommandResult lsRemote(@NotNull Project project,
                            @NotNull VirtualFile workingDir,
                            @NotNull GitRemote remote,
                            String... additionalParameters);

  @NotNull
  GitCommandResult lsRemoteRefs(@NotNull Project project,
                                @NotNull VirtualFile workingDir,
                                @NotNull GitRemote remote,
                                @NotNull List<String> refs,
                                String... additionalParameters);

  @NotNull
  GitCommandResult remotePrune(@NotNull GitRepository repository, @NotNull GitRemote remote);

  @NotNull
  GitRebaseCommandResult rebase(@NotNull GitRepository repository,
                                @NotNull GitRebaseParams parameters,
                                GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitRebaseCommandResult rebaseAbort(@NotNull GitRepository repository, GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitRebaseCommandResult rebaseContinue(@NotNull GitRepository repository, GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitRebaseCommandResult rebaseSkip(@NotNull GitRepository repository, GitLineHandlerListener @NotNull ... listeners);

  @NotNull
  GitCommandResult revert(@NotNull GitRepository repository,
                          @NotNull String commit,
                          boolean autoCommit,
                          GitLineHandlerListener @NotNull ... listeners);

  @Nullable
  Hash resolveReference(@NotNull GitRepository repository, @NotNull String reference);

  @NotNull
  GitCommandResult getObjectType(@NotNull GitRepository repository, @NotNull String object);

  @Nullable
  GitObjectType getObjectTypeEnum(@NotNull GitRepository repository, @NotNull String object);
}
