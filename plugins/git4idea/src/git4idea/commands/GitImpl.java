/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitVcs;
import git4idea.branch.GitRebaseParams;
import git4idea.config.GitVersionSpecialty;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static git4idea.GitUtil.COMMENT_CHAR;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

/**
 * Easy-to-use wrapper of common native Git commands.
 * Most of them return result as {@link GitCommandResult}.
 *
 * @author Kirill Likhodedov
 */
@SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
public class GitImpl implements Git {

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
    return run(h);
  }

  /**
   * <p>Queries Git for the unversioned files in the given paths. </p>
   * <p>Ignored files are left ignored, i. e. no information is returned about them (thus this method may also be used as a
   *    ignored files checker.</p>
   *
   * @param files files that are to be checked for the unversioned files among them.
   *              <b>Pass <code>null</code> to query the whole repository.</b>
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
    final Set<VirtualFile> untrackedFiles = new HashSet<>();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LS_FILES);
    h.setSilent(true);
    h.addParameters("--exclude-standard", "--others", "-z");
    h.endOptions();
    if (relativePaths != null) {
      h.addParameters(relativePaths);
    }

    final String output = h.run();
    if (StringUtil.isEmptyOrSpaces(output)) {
      return untrackedFiles;
    }

    for (String relPath : output.split("\u0000")) {
      VirtualFile f = root.findFileByRelativePath(relPath);
      if (f == null) {
        // files was created on disk, but VirtualFile hasn't yet been created,
        // when the GitChangeProvider has already been requested about changes.
        LOG.info(String.format("VirtualFile for path [%s] is null", relPath));
      } else {
        untrackedFiles.add(f);
      }
    }

    return untrackedFiles;
  }

  @Override
  @NotNull
  public GitCommandResult clone(@NotNull final Project project, @NotNull final File parentDirectory, @NotNull final String url,
                                @NotNull final String clonedDirectoryName, @NotNull final GitLineHandlerListener... listeners) {
    return run(() -> {
      GitLineHandler handler = new GitLineHandler(project, parentDirectory, GitCommand.CLONE);
      handler.setStdoutSuppressed(false);
      handler.setUrl(url);
      handler.addParameters("--progress");
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
    return run(h);
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
    return run(diff);
  }

  @NotNull
  @Override
  public GitCommandResult checkAttr(@NotNull final GitRepository repository,
                                    @NotNull final Collection<String> attributes,
                                    @NotNull Collection<VirtualFile> files) {
    List<List<String>> listOfPaths = VcsFileUtil.chunkFiles(repository.getRoot(), files);
    return runAll(ContainerUtil.map(listOfPaths, relativePaths -> () -> {
      final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECK_ATTR);
      h.addParameters(new ArrayList<>(attributes));
      h.endOptions();
      h.addParameters(relativePaths);
      return run(h);
    }));
  }

  @NotNull
  @Override
  public GitCommandResult stashSave(@NotNull GitRepository repository, @NotNull String message) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.STASH);
    h.addParameters("save");
    h.addParameters(message);
    return run(h);
  }

  @NotNull
  @Override
  public GitCommandResult stashPop(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.STASH);
    handler.addParameters("pop");
    addListeners(handler, listeners);
    return run(handler);
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
    return run(mergeHandler);
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
    return run(h);
  }

  /**
   * {@code git checkout -b &lt;branchName&gt;}
   */
  @NotNull
  @Override
  public GitCommandResult checkoutNewBranch(@NotNull GitRepository repository, @NotNull String branchName,
                                                   @Nullable GitLineHandlerListener listener) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECKOUT.readLockingCommand());
    h.setSilent(false);
    h.setStdoutSuppressed(false);
    h.addParameters("-b");
    h.addParameters(branchName);
    if (listener != null) {
      h.addLineListener(listener);
    }
    return run(h);
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
    return run(h);
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
    return run(h);
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
    return run(h);
  }

  @Override
  @NotNull
  public GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName, @NotNull String startPoint) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.setStdoutSuppressed(false);
    h.addParameters(branchName);
    h.addParameters(startPoint);
    return run(h);
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
    return run(h);
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
  private static GitCommandResult reset(@NotNull GitRepository repository, @NotNull String argument, @Nullable String target,
                                        @NotNull GitLineHandlerListener... listeners) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.RESET);
    handler.addParameters(argument);
    if (target != null) {
      handler.addParameters(target);
    }
    addListeners(handler, listeners);
    return run(handler);
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
    return run(h);
  }

  @Override
  @NotNull
  public GitCommandResult push(@NotNull GitRepository repository,
                               @NotNull String remote,
                               @Nullable String url,
                               @NotNull String spec,
                               boolean updateTracking,
                               @NotNull GitLineHandlerListener... listeners) {
    return doPush(repository, remote, singleton(url), spec, false, updateTracking, false, null, listeners);
  }

  @Override
  @NotNull
  public GitCommandResult push(@NotNull GitRepository repository,
                               @NotNull GitRemote remote,
                               @NotNull String spec,
                               boolean force,
                               boolean updateTracking,
                               boolean skipHook,
                               @Nullable String tagMode,
                               GitLineHandlerListener... listeners) {
    return doPush(repository, remote.getName(), remote.getPushUrls(), spec, force, updateTracking, skipHook, tagMode, listeners);
  }

  @NotNull
  private GitCommandResult doPush(@NotNull final GitRepository repository,
                                  @NotNull final String remoteName,
                                  @NotNull final Collection<String> remoteUrls,
                                  @NotNull final String spec,
                                  final boolean force,
                                  final boolean updateTracking,
                                  final boolean skipHook,
                                  @Nullable final String tagMode,
                                  @NotNull final GitLineHandlerListener... listeners) {
    return runCommand(() -> {
      final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.PUSH);
      h.setUrls(remoteUrls);
      h.setSilent(false);
      h.setStdoutSuppressed(false);
      addListeners(h, listeners);
      h.addProgressParameter();
      h.addParameters("--porcelain");
      h.addParameters(remoteName);
      h.addParameters(spec);
      if (updateTracking) {
        h.addParameters("--set-upstream");
      }
      if (force) {
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
    return run(handler);
  }

  @Override
  @NotNull
  public GitCommandResult cherryPick(@NotNull GitRepository repository, @NotNull String hash, boolean autoCommit,
                                     @NotNull GitLineHandlerListener... listeners) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHERRY_PICK);
    handler.addParameters("-x");
    if (!autoCommit) {
      handler.addParameters("-n");
    }
    handler.addParameters(hash);
    addListeners(handler, listeners);
    handler.setSilent(false);
    handler.setStdoutSuppressed(false);
    return run(handler);
  }

  @NotNull
  @Override
  public GitCommandResult getUnmergedFiles(@NotNull GitRepository repository) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.LS_FILES);
    h.addParameters("--unmerged");
    h.setSilent(true);
    return run(h);
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
    return runCommand(() -> {
      final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.FETCH);
      h.setSilent(false);
      h.setStdoutSuppressed(false);
      h.setUrls(remote.getUrls());
      h.addParameters(remote.getName());
      h.addParameters(params);
      h.addProgressParameter();
      GitVcs vcs = GitVcs.getInstance(repository.getProject());
      if (vcs != null && GitVersionSpecialty.SUPPORTS_FETCH_PRUNE.existsIn(vcs.getVersion())) {
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
    return run(h);
  }

  @NotNull
  @Override
  public GitCommandResult removeRemote(@NotNull GitRepository repository, @NotNull GitRemote remote) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REMOTE);
    h.addParameters("remove", remote.getName());
    return run(h);
  }

  @NotNull
  @Override
  public GitCommandResult renameRemote(@NotNull GitRepository repository, @NotNull String oldName, @NotNull String newName) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REMOTE);
    h.addParameters("rename", oldName, newName);
    return run(h);
  }

  @NotNull
  @Override
  public GitCommandResult setRemoteUrl(@NotNull GitRepository repository, @NotNull String remoteName, @NotNull String newUrl) {
    GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REMOTE);
    h.addParameters("set-url", remoteName, newUrl);
    return run(h);
  }

  @NotNull
  @Override
  public GitCommandResult lsRemote(@NotNull final Project project,
                                   @NotNull final File workingDir,
                                   @NotNull final String url) {
    return doLsRemote(project, workingDir, url, singleton(url));
  }

  @NotNull
  @Override
  public GitCommandResult lsRemote(@NotNull Project project,
                                   @NotNull VirtualFile workingDir,
                                   @NotNull GitRemote remote,
                                   String... additionalParameters) {
    return doLsRemote(project, VfsUtilCore.virtualToIoFile(workingDir), remote.getName(), remote.getUrls(), additionalParameters);
  }

  @NotNull
  @Override
  public GitCommandResult remotePrune(@NotNull final GitRepository repository, @NotNull final GitRemote remote) {
    return run(() -> {
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
  public GitCommandResult rebase(@NotNull GitRepository repository,
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
    return run(handler);
  }

  @NotNull
  @Override
  public GitCommandResult rebaseAbort(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REBASE);
    handler.addParameters("--abort");
    addListeners(handler, listeners);
    return run(handler);
  }

  @NotNull
  @Override
  public GitCommandResult rebaseContinue(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    return rebaseResume(repository, GitRebaseResumeMode.CONTINUE, listeners);
  }

  @NotNull
  @Override
  public GitCommandResult rebaseSkip(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    return rebaseResume(repository, GitRebaseResumeMode.SKIP, listeners);
  }

  @NotNull
  private GitCommandResult rebaseResume(@NotNull GitRepository repository,
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
  private static GitCommandResult runWithEditor(@NotNull GitLineHandler handler, @NotNull GitRebaseEditorHandler editorHandler) {
    GitRebaseEditorService service = GitRebaseEditorService.getInstance();
    service.configureHandler(handler, editorHandler.getHandlerNo());
    try {
      GitCommandResult result = run(handler);
      return editorHandler.wasEditorCancelled() ? toCancelledResult(result) : result;
    }
    finally {
      service.unregisterHandler(editorHandler.getHandlerNo());
    }
  }

  @NotNull
  private static GitCommandResult toCancelledResult(@NotNull GitCommandResult result) {
    int exitCode = result.getExitCode() == 0 ? 1 : result.getExitCode();
    return new GitCommandResult(false, exitCode, result.getErrorOutput(), result.getOutput(), result.getException()) {
      @Override
      public boolean cancelled() {
        return true;
      }
    };
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
    return run(handler);
  }

  @NotNull
  private static GitCommandResult doLsRemote(@NotNull final Project project,
                                             @NotNull final File workingDir,
                                             @NotNull final String remoteId,
                                             @NotNull final Collection<String> authenticationUrls,
                                             final String... additionalParameters) {
    return run(() -> {
      GitLineHandler h = new GitLineHandler(project, workingDir, GitCommand.LS_REMOTE);
      h.addParameters(additionalParameters);
      h.addParameters(remoteId);
      h.setUrls(authenticationUrls);
      return h;
    });
  }

  private static void addListeners(@NotNull GitLineHandler handler, @NotNull GitLineHandlerListener... listeners) {
    addListeners(handler, asList(listeners));
  }

  private static void addListeners(@NotNull GitLineHandler handler, @NotNull List<GitLineHandlerListener> listeners) {
    for (GitLineHandlerListener listener : listeners) {
      handler.addLineListener(listener);
    }
  }

  @NotNull
  private static GitCommandResult run(@NotNull Computable<GitLineHandler> handlerConstructor) {
    final List<String> errorOutput = new ArrayList<>();
    final List<String> output = new ArrayList<>();
    final AtomicInteger exitCode = new AtomicInteger();
    final AtomicBoolean startFailed = new AtomicBoolean();
    final AtomicReference<Throwable> exception = new AtomicReference<>();

    int authAttempt = 0;
    boolean authFailed;
    boolean success;
    do {
      errorOutput.clear();
      output.clear();
      exitCode.set(0);
      startFailed.set(false);
      exception.set(null);

      GitLineHandler handler = handlerConstructor.compute();
      handler.addLineListener(new GitLineHandlerListener() {
        @Override public void onLineAvailable(String line, Key outputType) {
          if (looksLikeError(line)) {
            synchronized (errorOutput) {
              errorOutput.add(line);
            }
          } else {
            synchronized (output) {
              output.add(line);
            }
          }
        }

        @Override public void processTerminated(int code) {
          exitCode.set(code);
        }

        @Override public void startFailed(Throwable t) {
          startFailed.set(true);
          errorOutput.add("Failed to start Git process");
          exception.set(t);
        }
      });

      handler.runInCurrentThread(null);
      authFailed = handler.hasHttpAuthFailed();
      success = !startFailed.get() && (handler.isIgnoredErrorCode(exitCode.get()) || exitCode.get() == 0);
    }
    while (authFailed && authAttempt++ < 2);
    return new GitCommandResult(success, exitCode.get(), errorOutput, output, null);
  }

  /**
   * Runs the given {@link GitLineHandler} in the current thread and returns the {@link GitCommandResult}.
   */
  @NotNull
  private static GitCommandResult run(@NotNull GitLineHandler handler) {
    return run(new Computable.PredefinedValueComputable<>(handler));
  }

  @Override
  @NotNull
  public GitCommandResult runCommand(@NotNull Computable<GitLineHandler> handlerConstructor) {
    return run(handlerConstructor);
  }

  @NotNull
  @Override
  public GitCommandResult runCommand(@NotNull final GitLineHandler handler) {
    return runCommand(() -> handler);
  }

  @NotNull
  private static GitCommandResult runAll(@NotNull List<Computable<GitCommandResult>> commands) {
    if (commands.isEmpty()) {
      LOG.error("List of commands should not be empty", new Exception());
      return GitCommandResult.error("Internal error");
    }
    GitCommandResult compoundResult = null;
    for (Computable<GitCommandResult> command : commands) {
      compoundResult = GitCommandResult.merge(compoundResult, command.compute());
    }
    return ObjectUtils.assertNotNull(compoundResult);
  }

  private static boolean looksLikeError(@NotNull final String text) {
    return ContainerUtil.exists(ERROR_INDICATORS, indicator -> StringUtil.startsWithIgnoreCase(text.trim(), indicator));
  }

  // could be upper-cased, so should check case-insensitively
  public static final String[] ERROR_INDICATORS = {
    "error:", "remote: error", "fatal:",
    "Cannot", "Could not", "Interactive rebase already started", "refusing to pull", "cannot rebase:", "conflict",
    "unable"
  };
}
