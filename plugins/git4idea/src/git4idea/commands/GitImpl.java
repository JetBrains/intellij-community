// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.externalProcessAuthHelper.GitAuthenticationGate;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitContentRevision;
import git4idea.branch.GitRebaseParams;
import git4idea.config.GitExecutable;
import git4idea.config.GitExecutableManager;
import git4idea.config.GitVersionSpecialty;
import git4idea.push.GitPushParams;
import git4idea.rebase.GitHandlerRebaseEditorManager;
import git4idea.rebase.GitInteractiveRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorHandler;
import git4idea.rebase.GitRebaseResumeMode;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static git4idea.GitUtil.COMMENT_CHAR;
import static java.util.Arrays.asList;
import static java.util.Collections.*;

/**
 * Easy-to-use wrapper of common native Git commands.
 * Most of them return result as {@link GitCommandResult}.
 */
public class GitImpl extends GitImplBase {

  private static final Logger LOG = Logger.getInstance(Git.class);
  public static final List<String> REBASE_CONFIG_PARAMS = singletonList("core.commentChar=" + COMMENT_CHAR);

  public GitImpl() {
  }

  /**
   * Calls 'git init' on the specified directory.
   */
  @NotNull
  @Override
  public GitCommandResult init(@NotNull Project project, @NotNull VirtualFile root, GitLineHandlerListener @NotNull ... listeners) {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.INIT);
    for (GitLineHandlerListener listener : listeners) {
      h.addLineListener(listener);
    }
    h.setSilent(false);
    h.setStdoutSuppressed(false);
    return runCommand(h);
  }

  @Override
  public Set<FilePath> ignoredFilePaths(@NotNull Project project, @NotNull VirtualFile root, @Nullable Collection<? extends FilePath> paths)
    throws VcsException {
    Set<FilePath> ignoredFiles = new HashSet<>();

    if (paths == null) {
      ignoredFiles.addAll(ignoredFilePathsNoChunk(project, root, null));
    }
    else {
      for (List<String> relativePaths : VcsFileUtil.chunkPaths(root, paths)) {
        ignoredFiles.addAll(ignoredFilePathsNoChunk(project, root, relativePaths));
      }
    }
    return ignoredFiles;
  }

  @Override
  public Set<FilePath> ignoredFilePathsNoChunk(@NotNull Project project, @NotNull VirtualFile root, @Nullable List<String> paths)
    throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.STATUS);
    h.setSilent(true);
    h.addParameters("--ignored", "--porcelain", "-z");
    if (paths != null) {
      h.addParameters(paths);
    }
    h.endOptions();

    final String output = runCommand(h).getOutputOrThrow();
    return parseFiles(root, output, "!! ");
  }

  @NotNull
  private static Set<FilePath> parseFiles(@NotNull VirtualFile root,
                                          @Nullable String output,
                                          @NotNull String fileStatusPrefix) throws VcsException {
    if (StringUtil.isEmptyOrSpaces(output)) return emptySet();

    final Set<FilePath> files = new HashSet<>();
    for (String relPath : output.split("\u0000")) {
      ProgressManager.checkCanceled();
      if (!relPath.startsWith(fileStatusPrefix)) continue;

      String relativePath = relPath.substring(fileStatusPrefix.length());
      files.add(GitContentRevision.createPath(root, relativePath, relativePath.endsWith("/")));
    }

    return files;
  }

  /**
   * <p>Queries Git for the unversioned files in the given paths. </p>
   * <p>Ignored files are left ignored, i. e. no information is returned about them (thus this method may also be used as a
   * ignored files checker.</p>
   *
   * @param files files that are to be checked for the unversioned files among them.
   *              <b>Pass {@code null} to query the whole repository.</b>
   * @return Unversioned not ignored files from the given scope.
   */
  @Override
  @NotNull
  public Set<VirtualFile> untrackedFiles(@NotNull Project project, @NotNull VirtualFile root,
                                         @Nullable Collection<? extends VirtualFile> files) throws VcsException {
    return ContainerUtil.map2SetNotNull(
      untrackedFilePaths(project, root,
                         files != null ? ContainerUtil.mapNotNull(files, VcsUtil::getFilePath) : null), FilePath::getVirtualFile);
  }

  @Override
  public Set<FilePath> untrackedFilePaths(@NotNull Project project, @NotNull VirtualFile root, @Nullable Collection<FilePath> files)
    throws VcsException {
    final Set<FilePath> untrackedFiles = new HashSet<>();

    if (files == null) {
      untrackedFiles.addAll(untrackedFilePathsNoChunk(project, root, null));
    }
    else {
      for (List<String> relativePaths : VcsFileUtil.chunkPaths(root, files)) {
        untrackedFiles.addAll(untrackedFilePathsNoChunk(project, root, relativePaths));
      }
    }

    return untrackedFiles;
  }

  @NotNull
  @Override
  public Collection<FilePath> untrackedFilePathsNoChunk(@NotNull Project project,
                                                        @NotNull VirtualFile root,
                                                        @Nullable List<String> relativePaths) throws VcsException {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.LS_FILES);
    h.setSilent(true);
    h.addParameters("--exclude-standard", "--others", "-z");
    h.endOptions();
    if (relativePaths != null) {
      h.addParameters(relativePaths);
    }

    final String output = runCommand(h).getOutputOrThrow();
    return parseFiles(root, output, "");
  }

  @Override
  @NotNull
  public GitCommandResult clone(@Nullable final Project project, @NotNull final File parentDirectory, @NotNull final String url,
                                @NotNull final String clonedDirectoryName, final GitLineHandlerListener @NotNull ... listeners) {
    return runCommand(() -> {
      // do not use per-project executable for 'clone' command
      Project defaultProject = ProjectManager.getInstance().getDefaultProject();
      GitExecutable executable = GitExecutableManager.getInstance().getExecutable(defaultProject, parentDirectory);
      GitLineHandler handler = new GitLineHandler(defaultProject, parentDirectory, executable, GitCommand.CLONE, emptyList());
      handler.setSilent(false);
      handler.setStderrSuppressed(false);
      handler.setUrl(url);
      handler.addParameters("--progress");
      if (GitVersionSpecialty.CLONE_RECURSE_SUBMODULES.existsIn(project, handler.getExecutable()) &&
          AdvancedSettings.getBoolean("git.clone.recurse.submodules")) {
        handler.addParameters("--recurse-submodules");
      }
      handler.addParameters(url);
      handler.endOptions();
      handler.addParameters(clonedDirectoryName);
      addListeners(handler, listeners);
      return handler;
    });
  }

  @NotNull
  @Override
  public GitCommandResult config(@NotNull GitRepository repository, String... params) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CONFIG);
    h.addParameters(params);
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult diff(@NotNull GitRepository repository, @NotNull List<String> parameters, @NotNull String range) {
    final GitLineHandler diff = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.DIFF);
    diff.addParameters(parameters);
    diff.addParameters(range);
    diff.setStdoutSuppressed(true);
    diff.setStderrSuppressed(true);
    diff.setSilent(true);
    return runCommand(diff);
  }

  @NotNull
  @Override
  public GitCommandResult checkAttr(@NotNull final GitRepository repository,
                                    @NotNull final Collection<String> attributes,
                                    @NotNull Collection<? extends VirtualFile> files) {
    List<String> relativeFilePaths = ContainerUtil.map(files, file -> VcsFileUtil.relativePath(repository.getRoot(), file));

    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECK_ATTR);
    h.addParameters("--stdin");
    h.addParameters(new ArrayList<>(attributes));
    h.endOptions();
    h.setInputProcessor(GitHandlerInputProcessorUtil.writeLines(relativeFilePaths, h.getCharset()));
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult stashSave(@NotNull GitRepository repository, @NotNull String message) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.STASH);
    h.addParameters("save");
    h.addParameters(message);
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult stashPop(@NotNull GitRepository repository, GitLineHandlerListener @NotNull ... listeners) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.STASH);
    handler.addParameters("pop");
    addListeners(handler, listeners);
    return runCommand(handler);
  }

  @Override
  @NotNull
  public GitCommandResult merge(@NotNull GitRepository repository, @NotNull String branchToMerge,
                                @Nullable List<String> additionalParams, GitLineHandlerListener @NotNull ... listeners) {
    final GitLineHandler mergeHandler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.MERGE);
    mergeHandler.setSilent(false);
    mergeHandler.addParameters(branchToMerge);
    if (additionalParams != null) {
      mergeHandler.addParameters(additionalParams);
    }
    for (GitLineHandlerListener listener : listeners) {
      mergeHandler.addLineListener(listener);
    }
    return runCommand(mergeHandler);
  }


  /**
   * {@code git checkout &lt;reference&gt;} <br/>
   * {@code git checkout -b &lt;newBranch&gt; &lt;reference&gt;}
   */
  @NotNull
  @Override
  public GitCommandResult checkout(@NotNull GitRepository repository,
                                   @NotNull String reference,
                                   @Nullable String newBranch,
                                   boolean force,
                                   boolean detach,
                                   GitLineHandlerListener @NotNull ... listeners) {
    return checkout(repository, reference, newBranch, force, detach, false, listeners);
  }

  /**
   * {@code git checkout <reference>} <br/>
   * {@code git checkout -b <newBranch> <reference>} <br/>
   * or withReset<br/>
   * {@code git checkout -B <newBranch> <reference>}
   */
  @NotNull
  @Override
  public GitCommandResult checkout(@NotNull GitRepository repository,
                                   @NotNull String reference,
                                   @Nullable String newBranch,
                                   boolean force,
                                   boolean detach,
                                   boolean withReset,
                                   GitLineHandlerListener @NotNull ... listeners) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECKOUT);
    h.setSilent(false);
    h.setStdoutSuppressed(false);
    if (force) {
      h.addParameters("--force");
    }
    if (newBranch == null) { // simply checkout
      h.addParameters(detach ? reference + "^0" : reference); // we could use `--detach` here, but it is supported only since 1.7.5.
    }
    else { // checkout reference as new branch
      h.addParameters(withReset ? "-B" : "-b", newBranch, reference);
    }
    h.endOptions();
    for (GitLineHandlerListener listener : listeners) {
      h.addLineListener(listener);
    }
    return runCommand(h);
  }

  /**
   * {@code git checkout -b &lt;branchName&gt;}
   */
  @NotNull
  @Override
  public GitCommandResult checkoutNewBranch(@NotNull GitRepository repository, @NotNull String branchName,
                                            @Nullable GitLineHandlerListener listener) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECKOUT);
    h.setSilent(false);
    h.setStdoutSuppressed(false);
    h.addParameters("-b");
    h.addParameters(branchName);
    if (listener != null) {
      h.addLineListener(listener);
    }
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult createNewTag(@NotNull GitRepository repository, @NotNull String tagName,
                                       @Nullable GitLineHandlerListener listener, @NotNull String reference) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.TAG);
    h.setSilent(false);
    h.addParameters(tagName);
    if (!reference.isEmpty()) {
      h.addParameters(reference);
    }
    if (listener != null) {
      h.addLineListener(listener);
    }
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult deleteTag(@NotNull GitRepository repository,
                                    @NotNull String tagName,
                                    GitLineHandlerListener @NotNull ... listeners) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.TAG);
    h.setSilent(false);
    h.addParameters("-d");
    h.addParameters(tagName);
    for (GitLineHandlerListener listener : listeners) {
      h.addLineListener(listener);
    }
    return runCommand(h);
  }

  /**
   * {@code git branch -d <reference>} or {@code git branch -D <reference>}
   */
  @NotNull
  @Override
  public GitCommandResult branchDelete(@NotNull GitRepository repository,
                                       @NotNull String branchName,
                                       boolean force,
                                       GitLineHandlerListener @NotNull ... listeners) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.setSilent(false);
    h.setStdoutSuppressed(false);
    h.addParameters(force ? "-D" : "-d");
    h.addParameters(branchName);
    for (GitLineHandlerListener listener : listeners) {
      h.addLineListener(listener);
    }
    return runCommand(h);
  }

  @Override
  @NotNull
  public GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName, @NotNull String startPoint) {
    return branchCreate(repository, branchName, startPoint, false);
  }

  @Override
  @NotNull
  public GitCommandResult branchCreate(@NotNull GitRepository repository,
                                       @NotNull String branchName,
                                       @NotNull String startPoint,
                                       boolean force) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.setStdoutSuppressed(false);
    if (force) {
      h.addParameters("-f");
    }
    h.addParameters(branchName);
    h.addParameters(startPoint);
    return runCommand(h);
  }

  @Override
  public @NotNull GitCommandResult setUpstream(@NotNull GitRepository repository,
                                               @NotNull String upstreamBranchName,
                                               @NotNull String branchName) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.setStdoutSuppressed(false);
    if (GitVersionSpecialty.KNOWS_SET_UPSTREAM_TO.existsIn(repository)) {
      h.addParameters("--set-upstream-to", upstreamBranchName, branchName);
    }
    else {
      h.addParameters("--set-upstream", branchName, upstreamBranchName);
    }
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult renameBranch(@NotNull GitRepository repository,
                                       @NotNull String currentName,
                                       @NotNull String newName,
                                       GitLineHandlerListener @NotNull ... listeners) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.setSilent(false);
    h.setStdoutSuppressed(false);
    h.addParameters("-m", currentName, newName);
    return runCommand(h);
  }

  @Override
  @NotNull
  public GitCommandResult reset(@NotNull GitRepository repository, @NotNull GitResetMode mode, @NotNull String target,
                                GitLineHandlerListener @NotNull ... listeners) {
    return reset(repository, mode.getArgument(), target, listeners);
  }

  @Override
  @NotNull
  public GitCommandResult resetMerge(@NotNull GitRepository repository, @Nullable String revision) {
    return reset(repository, "--merge", revision);
  }

  @NotNull
  private GitCommandResult reset(@NotNull GitRepository repository, @NotNull String argument, @Nullable String target,
                                 GitLineHandlerListener @NotNull ... listeners) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.RESET);
    handler.addParameters(argument);
    if (target != null) {
      handler.addParameters(target);
    }
    addListeners(handler, listeners);
    return runCommand(handler);
  }

  /**
   * Returns the last (tip) commit on the given branch.<br/>
   * {@code git rev-list -1 <branchName>}
   */
  @NotNull
  @Override
  public GitCommandResult tip(@NotNull GitRepository repository, @NotNull String branchName) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REV_LIST);
    h.addParameters("-1");
    h.addParameters(branchName);
    return runCommand(h);
  }

  @Override
  @NotNull
  public GitCommandResult push(@NotNull GitRepository repository,
                               @NotNull String remote,
                               @Nullable String url,
                               @NotNull String spec,
                               boolean updateTracking,
                               GitLineHandlerListener @NotNull ... listeners) {
    return doPush(repository, remote, singleton(url), spec, false, updateTracking, false, Collections.emptyList(), null, listeners);
  }

  @Override
  @NotNull
  public GitCommandResult push(@NotNull GitRepository repository,
                               @NotNull GitPushParams pushParams,
                               GitLineHandlerListener... listeners) {
    return doPush(repository, pushParams.getRemote().getName(), pushParams.getRemote().getPushUrls(), pushParams.getSpec(),
                  pushParams.isForce(), pushParams.shouldSetupTracking(), pushParams.shouldSkipHooks(), pushParams.getForceWithLease(),
                  pushParams.getTagMode(), listeners);
  }

  @NotNull
  private GitCommandResult doPush(@NotNull final GitRepository repository,
                                  @NotNull final String remoteName,
                                  @NotNull final Collection<String> remoteUrls,
                                  @NotNull final String spec,
                                  final boolean force,
                                  final boolean updateTracking,
                                  final boolean skipHook,
                                  final List<? extends GitPushParams.ForceWithLease> forceWithLease,
                                  @Nullable final String tagMode,
                                  final GitLineHandlerListener @NotNull ... listeners) {
    return runCommand(() -> {
      final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.PUSH);
      h.setUrls(remoteUrls);
      h.setSilent(false);
      h.setStdoutSuppressed(false);
      addListeners(h, listeners);
      if(GitVersionSpecialty.ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS.existsIn(repository)) {
        h.addParameters("--progress");
      }
      h.addParameters("--porcelain");
      h.addParameters(remoteName);
      h.addParameters(spec);
      if (updateTracking) {
        h.addParameters("--set-upstream");
      }
      if (GitVersionSpecialty.SUPPORTS_FORCE_PUSH_WITH_LEASE.existsIn(repository) &&
          !forceWithLease.isEmpty()) {
        for (GitPushParams.ForceWithLease lease : forceWithLease) {
          String parameter = lease.getParameter();
          if (parameter != null) {
            h.addParameters("--force-with-lease=" + parameter);
          }
          else {
            h.addParameters("--force-with-lease");
          }
        }
      }
      else if (force) {
        h.addParameters("--force");
      }
      if (tagMode != null) {
        h.addParameters(tagMode);
      }
      if (skipHook) {
        h.addParameters("--no-verify");
      }
      return h;
    });
  }

  @NotNull
  @Override
  public GitCommandResult show(@NotNull GitRepository repository, String @NotNull ... params) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.SHOW);
    handler.addParameters(params);
    return runCommand(handler);
  }

  @NotNull
  @Override
  public GitCommandResult cherryPick(@NotNull GitRepository repository,
                                     @NotNull String hash,
                                     boolean autoCommit,
                                     boolean addCherryPickedFromSuffix,
                                     GitLineHandlerListener @NotNull ... listeners) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHERRY_PICK);
    if (addCherryPickedFromSuffix) {
      handler.addParameters("-x");
    }
    if (!autoCommit) {
      handler.addParameters("-n");
    }
    handler.addParameters(hash);
    addListeners(handler, listeners);
    handler.setSilent(false);
    handler.setStdoutSuppressed(false);
    return runCommand(handler);
  }

  @NotNull
  @Override
  public GitCommandResult getUnmergedFiles(@NotNull GitRepository repository) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.LS_FILES);
    h.addParameters("--unmerged");
    h.setSilent(true);
    return runCommand(h);
  }

  /**
   * Fetch remote branch
   * {@code git fetch <remote> <params>}
   */
  @Override
  @NotNull
  public GitCommandResult fetch(@NotNull final GitRepository repository,
                                @NotNull final GitRemote remote,
                                @NotNull final List<? extends GitLineHandlerListener> listeners,
                                final String... params) {
    return fetch(repository, remote, listeners, null, params);
  }

  @NotNull
  public GitCommandResult fetch(@NotNull final GitRepository repository,
                                @NotNull final GitRemote remote,
                                @NotNull final List<? extends GitLineHandlerListener> listeners,
                                @Nullable GitAuthenticationGate authenticationGate,
                                final String... params) {
    return runCommand(() -> {
      GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.FETCH);
      if (authenticationGate != null) {
        h.setAuthenticationGate(authenticationGate);
      }
      h.setSilent(false);
      h.setStdoutSuppressed(false);
      h.setUrls(remote.getUrls());
      h.addParameters(remote.getName());
      h.addParameters(params);
      if(GitVersionSpecialty.ABLE_TO_USE_PROGRESS_IN_REMOTE_COMMANDS.existsIn(repository)) {
        h.addParameters("--progress");
      }
      if (GitVersionSpecialty.SUPPORTS_FETCH_PRUNE.existsIn(repository)) {
        h.addParameters("--prune");
      }
      addListeners(h, listeners);
      return h;
    });
  }

  @NotNull
  @Override
  public GitCommandResult addRemote(@NotNull GitRepository repository, @NotNull String name, @NotNull String url) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REMOTE);
    h.addParameters("add", name, url);
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult removeRemote(@NotNull GitRepository repository, @NotNull GitRemote remote) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REMOTE);
    h.addParameters("remove", remote.getName());
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult renameRemote(@NotNull GitRepository repository, @NotNull String oldName, @NotNull String newName) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REMOTE);
    h.addParameters("rename", oldName, newName);
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult setRemoteUrl(@NotNull GitRepository repository, @NotNull String remoteName, @NotNull String newUrl) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REMOTE);
    h.addParameters("set-url", remoteName, newUrl);
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult lsRemote(@NotNull final Project project,
                                   @NotNull final File workingDir,
                                   @NotNull final String url) {
    return doLsRemote(project, workingDir, url, singleton(url), emptyList());
  }

  @NotNull
  @Override
  public GitCommandResult lsRemote(@NotNull Project project,
                                   @NotNull VirtualFile workingDir,
                                   @NotNull GitRemote remote,
                                   String... additionalParameters) {
    return lsRemoteRefs(project, workingDir, remote, emptyList(), additionalParameters);
  }

  @NotNull
  @Override
  public GitCommandResult lsRemoteRefs(@NotNull Project project,
                                       @NotNull VirtualFile workingDir,
                                       @NotNull GitRemote remote,
                                       @NotNull List<String> refs,
                                       String... additionalParameters) {
    return doLsRemote(project, VfsUtilCore.virtualToIoFile(workingDir), remote.getName(), remote.getUrls(), refs, additionalParameters);
  }

  @NotNull
  @Override
  public GitCommandResult remotePrune(@NotNull final GitRepository repository, @NotNull final GitRemote remote) {
    return runCommand(() -> {
      GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REMOTE.writeLockingCommand());
      h.setStdoutSuppressed(false);
      h.addParameters("prune");
      h.addParameters(remote.getName());
      h.setUrls(remote.getUrls());
      return h;
    });
  }

  @NotNull
  @Override
  public GitRebaseCommandResult rebase(@NotNull GitRepository repository,
                                       @NotNull GitRebaseParams parameters,
                                       GitLineHandlerListener @NotNull ... listeners) {
    Project project = repository.getProject();
    VirtualFile root = repository.getRoot();
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.REBASE, REBASE_CONFIG_PARAMS);
    handler.addParameters(parameters.asCommandLineArguments());
    addListeners(handler, listeners);

    if (parameters.isInteractive()) {
      GitRebaseEditorHandler editorHandler = parameters.getEditorHandler();
      if (editorHandler == null) {
        editorHandler = createEditor(project, root, handler, true);
      }
      return runWithEditor(handler, editorHandler);
    }
    return GitRebaseCommandResult.normal(runCommand(handler));
  }

  @NotNull
  @Override
  public GitRebaseCommandResult rebaseAbort(@NotNull GitRepository repository, GitLineHandlerListener @NotNull ... listeners) {
    GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REBASE);
    handler.addParameters("--abort");
    addListeners(handler, listeners);
    return GitRebaseCommandResult.normal(runCommand(handler));
  }

  @NotNull
  @Override
  public GitRebaseCommandResult rebaseContinue(@NotNull GitRepository repository, GitLineHandlerListener @NotNull ... listeners) {
    return rebaseResume(repository, GitRebaseResumeMode.CONTINUE, listeners);
  }

  @NotNull
  @Override
  public GitRebaseCommandResult rebaseSkip(@NotNull GitRepository repository, GitLineHandlerListener @NotNull ... listeners) {
    return rebaseResume(repository, GitRebaseResumeMode.SKIP, listeners);
  }

  @NotNull
  private GitRebaseCommandResult rebaseResume(@NotNull GitRepository repository,
                                              @NotNull GitRebaseResumeMode rebaseMode,
                                              GitLineHandlerListener @NotNull [] listeners) {
    Project project = repository.getProject();
    VirtualFile root = repository.getRoot();
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.REBASE, REBASE_CONFIG_PARAMS);
    handler.addParameters(rebaseMode.asCommandLineArgument());
    addListeners(handler, listeners);
    return runWithEditor(handler, createEditor(project, root, handler, false));
  }

  @NotNull
  private GitRebaseCommandResult runWithEditor(@NotNull GitLineHandler handler, @NotNull GitRebaseEditorHandler editorHandler) {
    try (GitHandlerRebaseEditorManager ignored = GitHandlerRebaseEditorManager.prepareEditor(handler, editorHandler)) {
      GitCommandResult result = runCommand(handler);
      if (editorHandler.wasCommitListEditorCancelled()) return GitRebaseCommandResult.cancelledInCommitList(result);
      if (editorHandler.wasUnstructuredEditorCancelled()) return GitRebaseCommandResult.cancelledInCommitMessage(result);
      return GitRebaseCommandResult.normal(result);
    }
  }

  @VisibleForTesting
  @NotNull
  protected GitInteractiveRebaseEditorHandler createEditor(@NotNull Project project,
                                                           @NotNull VirtualFile root,
                                                           @NotNull GitLineHandler handler,
                                                           boolean commitListAware) {
    GitInteractiveRebaseEditorHandler editor = new GitInteractiveRebaseEditorHandler(project, root);
    if (!commitListAware) {
      editor.setRebaseEditorShown();
    }
    return editor;
  }

  @NotNull
  @Override
  public GitCommandResult revert(@NotNull GitRepository repository,
                                 @NotNull String commit,
                                 boolean autoCommit,
                                 GitLineHandlerListener @NotNull ... listeners) {
    GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REVERT);
    handler.addParameters(commit);
    if (!autoCommit) {
      handler.addParameters("--no-commit");
    }
    addListeners(handler, listeners);
    return runCommand(handler);
  }

  @Nullable
  @Override
  public Hash resolveReference(@NotNull GitRepository repository, @NotNull String ref) {
    VirtualFile root = repository.getRoot();
    GitLineHandler handler = new GitLineHandler(repository.getProject(), root, GitCommand.REV_PARSE);
    handler.addParameters("--verify");
    handler.addParameters(ref + "^{commit}");
    GitCommandResult result = Git.getInstance().runCommand(handler);
    String output = result.getOutputAsJoinedString();
    if (result.success()) {
      if (VcsLogUtil.HASH_REGEX.matcher(output).matches()) {
        return HashImpl.build(output);
      }
      else {
        LOG.error("Invalid output for git rev-parse " + ref + " in " + root + ": " + output);
        return null;
      }
    }
    else {
      LOG.debug("Reference [" + ref + "] is unknown to Git in " + root);
      return null;
    }
  }

  @NotNull
  private GitCommandResult doLsRemote(@NotNull final Project project,
                                      @NotNull final File workingDir,
                                      @NotNull final String remoteId,
                                      @NotNull final Collection<String> authenticationUrls,
                                      @NotNull final List<String> refs,
                                      final String... additionalParameters) {
    return runCommand(() -> {
      GitLineHandler h = new GitLineHandler(project, workingDir, GitCommand.LS_REMOTE);
      h.addParameters(additionalParameters);
      h.addParameters(remoteId);
      h.addParameters(refs);
      h.setUrls(authenticationUrls);
      return h;
    });
  }

  @Override
  @NotNull
  public GitCommandResult getObjectType(@NotNull GitRepository repository, @NotNull String object) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CAT_FILE);
    h.setSilent(true);
    h.addParameters("-t", object);
    return runCommand(h);
  }

  @Override
  @Nullable
  public GitObjectType getObjectTypeEnum(@NotNull GitRepository repository, @NotNull String object) {
    GitCommandResult result = getObjectType(repository, object);
    if (!result.success()) return null;
    String string = result.getOutputAsJoinedString();
    try {
      return GitObjectType.valueOf(StringUtil.toUpperCase(string));
    }
    catch (IllegalArgumentException e) {
      LOG.warn(e);
      return null;
    }
  }

  private static void addListeners(@NotNull GitLineHandler handler, GitLineHandlerListener @NotNull ... listeners) {
    addListeners(handler, asList(listeners));
  }

  private static void addListeners(@NotNull GitLineHandler handler, @NotNull List<? extends GitLineHandlerListener> listeners) {
    for (GitLineHandlerListener listener : listeners) {
      handler.addLineListener(listener);
    }
  }

  @NotNull
  public static String runBundledCommand(@Nullable Project project, String... args) throws VcsException {
    if (project != null && !TrustedProjects.isTrusted(project)) {
      throw new IllegalStateException("Shouldn't be possible to run a Git command in the safe mode");
    }

    try {
      GitExecutable gitExecutable = GitExecutableManager.getInstance().getExecutable(project);
      GeneralCommandLine command = gitExecutable.createBundledCommandLine(project, args);
      command.setCharset(StandardCharsets.UTF_8);

      StringBuilder output = new StringBuilder();
      OSProcessHandler handler = new OSProcessHandler(command);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          if (outputType == ProcessOutputTypes.STDOUT) {
            output.append(event.getText());
          }
        }
      });
      handler.startNotify();
      handler.waitFor();
      return output.toString();
    }
    catch (ExecutionException e) {
      throw new VcsException(e);
    }
  }
}
