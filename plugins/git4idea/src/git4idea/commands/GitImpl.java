// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.config.GitVersionSpecialty;
import git4idea.push.GitPushParams;
import git4idea.rebase.GitInteractiveRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorService;
import git4idea.rebase.GitRebaseResumeMode;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.reset.GitResetMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
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
  private static final List<String> REBASE_CONFIG_PARAMS = singletonList("core.commentChar=" + COMMENT_CHAR);

  public GitImpl() {
  }

  /**
   * Calls 'git init' on the specified directory.
   */
  @NotNull
  @Override
  public GitCommandResult init(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitLineHandlerListener... listeners) {
    GitLineHandler h = new GitLineHandler(project, root, GitCommand.INIT);
    for (GitLineHandlerListener listener : listeners) {
      h.addLineListener(listener);
    }
    h.setSilent(false);
    h.setStdoutSuppressed(false);
    return runCommand(h);
  }

  @NotNull
  @Override
  public Set<VirtualFile> ignoredFiles(@NotNull Project project, @NotNull VirtualFile root, @Nullable Collection<FilePath> paths)
    throws VcsException {
    Set<VirtualFile> ignoredFiles = new HashSet<>();

    if (paths == null) {
      ignoredFiles.addAll(ignoredFilesNoChunk(project, root, null));
    }
    else {
      for (List<String> relativePaths : VcsFileUtil.chunkPaths(root, paths)) {
        ignoredFiles.addAll(ignoredFilesNoChunk(project, root, relativePaths));
      }
    }
    return ignoredFiles;
  }

  @NotNull
  @Override
  public Set<VirtualFile> ignoredFilesNoChunk(@NotNull Project project, @NotNull VirtualFile root, @Nullable List<String> paths)
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
  private static Set<VirtualFile> parseFiles(@NotNull VirtualFile root, @Nullable String output, @NotNull String fileStatusPrefix) {
    if (StringUtil.isEmptyOrSpaces(output)) return emptySet();

    final Set<VirtualFile> files = new HashSet<>();
    for (String relPath : output.split("\u0000")) {
      if (!fileStatusPrefix.isEmpty() && !relPath.startsWith(fileStatusPrefix)) continue;

      String relativePath = relPath.substring(fileStatusPrefix.length());
      VirtualFile f = VfsUtil.findFileByIoFile(new File(root.getPath(), relativePath), true);
      if (f == null) {
        // files was created on disk, but VirtualFile hasn't yet been created,
        // when the GitChangeProvider has already been requested about changes.
        LOG.info(String.format("VirtualFile for path [%s] is null", relPath));
      }
      else {
        files.add(f);
      }
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
                                         @Nullable Collection<VirtualFile> files) throws VcsException {
    final Set<VirtualFile> untrackedFiles = new HashSet<>();

    if (files == null) {
      untrackedFiles.addAll(untrackedFilesNoChunk(project, root, null));
    }
    else {
      for (List<String> relativePaths : VcsFileUtil.chunkFiles(root, files)) {
        untrackedFiles.addAll(untrackedFilesNoChunk(project, root, relativePaths));
      }
    }

    return untrackedFiles;
  }

  // relativePaths are guaranteed to fit into command line length limitations.
  @Override
  @NotNull
  public Collection<VirtualFile> untrackedFilesNoChunk(@NotNull Project project,
                                                       @NotNull VirtualFile root,
                                                       @Nullable List<String> relativePaths)
    throws VcsException {
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
  public GitCommandResult clone(@NotNull final Project project, @NotNull final File parentDirectory, @NotNull final String url,
                                @NotNull final String clonedDirectoryName, @NotNull final GitLineHandlerListener... listeners) {
    return runCommand(() -> {
      GitLineHandler handler = new GitLineHandler(project, parentDirectory, GitCommand.CLONE);
      handler.setSilent(false);
      handler.setStderrSuppressed(false);
      handler.setUrl(url);
      handler.addParameters("--progress");
      if (GitVersionSpecialty.CLONE_RECURSE_SUBMODULES.existsIn(project) && Registry.is("git.clone.recurse.submodules")) {
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
                                    @NotNull Collection<VirtualFile> files) {
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
  public GitCommandResult stashPop(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.STASH);
    handler.addParameters("pop");
    addListeners(handler, listeners);
    return runCommand(handler);
  }

  @Override
  @NotNull
  public GitCommandResult merge(@NotNull GitRepository repository, @NotNull String branchToMerge,
                                @Nullable List<String> additionalParams, @NotNull GitLineHandlerListener... listeners) {
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
                                   @NotNull GitLineHandlerListener... listeners) {
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
      h.addParameters("-b", newBranch, reference);
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
                                    @NotNull GitLineHandlerListener... listeners) {
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
                                       @NotNull GitLineHandlerListener... listeners) {
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

  /**
   * Get branches containing the commit.
   * {@code git branch --contains <commit>}
   */
  @Override
  @NotNull
  public GitCommandResult branchContains(@NotNull GitRepository repository, @NotNull String commit) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.addParameters("--contains", commit);
    return runCommand(h);
  }

  @Override
  @NotNull
  public GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName, @NotNull String startPoint) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.setStdoutSuppressed(false);
    h.addParameters(branchName);
    h.addParameters(startPoint);
    return runCommand(h);
  }

  @NotNull
  @Override
  public GitCommandResult renameBranch(@NotNull GitRepository repository,
                                       @NotNull String currentName,
                                       @NotNull String newName,
                                       @NotNull GitLineHandlerListener... listeners) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.setSilent(false);
    h.setStdoutSuppressed(false);
    h.addParameters("-m", currentName, newName);
    return runCommand(h);
  }

  @Override
  @NotNull
  public GitCommandResult reset(@NotNull GitRepository repository, @NotNull GitResetMode mode, @NotNull String target,
                                @NotNull GitLineHandlerListener... listeners) {
    return reset(repository, mode.getArgument(), target, listeners);
  }

  @Override
  @NotNull
  public GitCommandResult resetMerge(@NotNull GitRepository repository, @Nullable String revision) {
    return reset(repository, "--merge", revision);
  }

  @NotNull
  private GitCommandResult reset(@NotNull GitRepository repository, @NotNull String argument, @Nullable String target,
                                 @NotNull GitLineHandlerListener... listeners) {
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
                               @NotNull GitLineHandlerListener... listeners) {
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
                                  final List<GitPushParams.ForceWithLease> forceWithLease,
                                  @Nullable final String tagMode,
                                  @NotNull final GitLineHandlerListener... listeners) {
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
  public GitCommandResult show(@NotNull GitRepository repository, @NotNull String... params) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.SHOW);
    handler.addParameters(params);
    return runCommand(handler);
  }

  @Override
  @NotNull
  public GitCommandResult cherryPick(@NotNull GitRepository repository, @NotNull String hash, boolean autoCommit,
                                     @NotNull GitLineHandlerListener... listeners) {
    return cherryPick(repository, hash, autoCommit, true, listeners);
  }

  @NotNull
  @Override
  public GitCommandResult cherryPick(@NotNull GitRepository repository,
                                     @NotNull String hash,
                                     boolean autoCommit,
                                     boolean addCherryPickedFromSuffix,
                                     @NotNull GitLineHandlerListener... listeners) {
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
                                @NotNull final List<GitLineHandlerListener> listeners,
                                final String... params) {
    return fetch(repository, remote, listeners, null, params);
  }

  @NotNull
  public GitCommandResult fetch(@NotNull final GitRepository repository,
                                @NotNull final GitRemote remote,
                                @NotNull final List<GitLineHandlerListener> listeners,
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
                                       @NotNull GitLineHandlerListener... listeners) {
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
  public GitRebaseCommandResult rebaseAbort(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REBASE);
    handler.addParameters("--abort");
    addListeners(handler, listeners);
    return GitRebaseCommandResult.normal(runCommand(handler));
  }

  @NotNull
  @Override
  public GitRebaseCommandResult rebaseContinue(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    return rebaseResume(repository, GitRebaseResumeMode.CONTINUE, listeners);
  }

  @NotNull
  @Override
  public GitRebaseCommandResult rebaseSkip(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    return rebaseResume(repository, GitRebaseResumeMode.SKIP, listeners);
  }

  @NotNull
  private GitRebaseCommandResult rebaseResume(@NotNull GitRepository repository,
                                              @NotNull GitRebaseResumeMode rebaseMode,
                                              @NotNull GitLineHandlerListener[] listeners) {
    Project project = repository.getProject();
    VirtualFile root = repository.getRoot();
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.REBASE, REBASE_CONFIG_PARAMS);
    handler.addParameters(rebaseMode.asCommandLineArgument());
    addListeners(handler, listeners);
    return runWithEditor(handler, createEditor(project, root, handler, false));
  }

  @NotNull
  private GitRebaseCommandResult runWithEditor(@NotNull GitLineHandler handler, @NotNull GitRebaseEditorHandler editorHandler) {
    GitRebaseEditorService service = GitRebaseEditorService.getInstance();
    service.configureHandler(handler, editorHandler.getHandlerNo());
    try {
      GitCommandResult result = runCommand(handler);
      if (editorHandler.wasCommitListEditorCancelled()) return GitRebaseCommandResult.cancelledInCommitList(result);
      if (editorHandler.wasUnstructuredEditorCancelled()) return GitRebaseCommandResult.cancelledInCommitMessage(result);
      return GitRebaseCommandResult.normal(result);
    }
    finally {
      service.unregisterHandler(editorHandler.getHandlerNo());
    }
  }

  @VisibleForTesting
  @NotNull
  protected GitInteractiveRebaseEditorHandler createEditor(@NotNull Project project,
                                                           @NotNull VirtualFile root,
                                                           @NotNull GitLineHandler handler,
                                                           boolean commitListAware) {
    GitInteractiveRebaseEditorHandler editor = new GitInteractiveRebaseEditorHandler(GitRebaseEditorService.getInstance(), project, root);
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
                                 @NotNull GitLineHandlerListener... listeners) {
    GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REVERT);
    handler.addParameters(commit);
    if (!autoCommit) {
      handler.addParameters("--no-commit");
    }
    addListeners(handler, listeners);
    return runCommand(handler);
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

  private static void addListeners(@NotNull GitLineHandler handler, @NotNull GitLineHandlerListener... listeners) {
    addListeners(handler, asList(listeners));
  }

  private static void addListeners(@NotNull GitLineHandler handler, @NotNull List<GitLineHandlerListener> listeners) {
    for (GitLineHandlerListener listener : listeners) {
      handler.addLineListener(listener);
    }
  }
}
