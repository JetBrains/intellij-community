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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitBranch;
import git4idea.GitCommit;
import git4idea.GitExecutionException;
import git4idea.history.GitHistoryUtils;
import git4idea.push.GitPushSpec;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Easy-to-use wrapper of common native Git commands.
 * Most of them return result as {@link GitCommandResult}.
 *
 * @author Kirill Likhodedov
 */
public class GitImpl implements Git {

  private final Logger LOG = Logger.getInstance(Git.class);

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
    final Set<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();

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
    final Set<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();
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
  public GitCommandResult clone(@NotNull Project project, @NotNull File parentDirectory, @NotNull String url,
                                @NotNull String clonedDirectoryName, @NotNull GitLineHandlerListener... listeners) {
    GitLineHandlerPasswordRequestAware handler = new GitLineHandlerPasswordRequestAware(project, parentDirectory, GitCommand.CLONE);
    handler.setUrl(url);
    handler.addParameters("--progress");
    handler.addParameters(url);
    handler.addParameters(clonedDirectoryName);
    addListeners(handler, listeners);
    return run(handler);
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
  public GitCommandResult checkAttr(@NotNull GitRepository repository, @NotNull Collection<String> attributes,
                                    @NotNull Collection<VirtualFile> files) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECK_ATTR);
    h.addParameters(new ArrayList<String>(attributes));
    h.endOptions();
    h.addRelativeFiles(files);
    return run(h);
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

  @NotNull
  @Override
  public List<GitCommit> history(@NotNull GitRepository repository, @NotNull String range) {
    try {
      return GitHistoryUtils.history(repository.getProject(), repository.getRoot(), range);
    }
    catch (VcsException e) {
      // this is critical, because we need to show the list of unmerged commits, and it shouldn't happen => inform user and developer
      throw new GitExecutionException("Couldn't get [git log " + range + "] on repository [" + repository.getRoot() + "]", e);
    }
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
                                          @NotNull GitLineHandlerListener... listeners) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECKOUT);
    h.setSilent(false);
    if (force) {
      h.addParameters("--force");
    }
    if (newBranch == null) { // simply checkout
      h.addParameters(reference);
    } 
    else { // checkout reference as new branch
      h.addParameters("-b", newBranch, reference);
    }
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

  /**
   * Create branch without checking it out.
   * {@code git branch <branchName>}
   */
  @Override
  @NotNull
  public GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.addParameters(branchName);
    return run(h);
  }

  @Override
  @NotNull
  public GitCommandResult resetHard(@NotNull GitRepository repository, @NotNull String revision) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.RESET);
    handler.addParameters("--hard", revision);
    return run(handler);
  }

  @Override
  @NotNull
  public GitCommandResult resetMerge(@NotNull GitRepository repository, @Nullable String revision) {
    final GitLineHandler handler = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.RESET);
    handler.addParameters("--merge");
    if (revision != null) {
      handler.addParameters(revision);
    }
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
  public GitCommandResult push(@NotNull GitRepository repository, @NotNull String remote, @NotNull String url, @NotNull String spec,
                               boolean updateTracking, @NotNull GitLineHandlerListener... listeners) {
    final GitLineHandlerPasswordRequestAware h = new GitLineHandlerPasswordRequestAware(repository.getProject(), repository.getRoot(),
                                                                                        GitCommand.PUSH);
    h.setUrl(url);
    h.setSilent(false);
    addListeners(h, listeners);
    h.addProgressParameter();
    h.addParameters(remote);
    h.addParameters(spec);
    if (updateTracking) {
      h.addParameters("--set-upstream");
    }
    return run(h);
  }

  @Override
  @NotNull
  public GitCommandResult push(@NotNull GitRepository repository, @NotNull String remote, @NotNull String url, @NotNull String spec,
                               @NotNull GitLineHandlerListener... listeners) {
    return push(repository, remote, url, spec, false, listeners);
  }

  @Override
  @NotNull
  public GitCommandResult push(@NotNull GitRepository repository, @NotNull GitPushSpec pushSpec, @NotNull String url,
                               @NotNull GitLineHandlerListener... listeners) {
    GitRemote remote = pushSpec.getRemote();
    GitBranch remoteBranch = pushSpec.getDest();
    String destination = remoteBranch.getName().replaceFirst(remote.getName() + "/", "");
    return push(repository, remote.getName(), url, pushSpec.getSource().getName() + ":" + destination, listeners);
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
  public GitCommandResult fetch(@NotNull GitRepository repository, @NotNull String url, @NotNull String remote, String... params) {
    final GitLineHandlerPasswordRequestAware h =
      new GitLineHandlerPasswordRequestAware(repository.getProject(), repository.getRoot(), GitCommand.FETCH);
    h.setUrl(url);
    h.addParameters(remote);
    h.addParameters(params);
    h.addProgressParameter();
    return run(h);
  }

  private static void addListeners(@NotNull GitLineHandler handler, @NotNull GitLineHandlerListener... listeners) {
    for (GitLineHandlerListener listener : listeners) {
      handler.addLineListener(listener);
    }
  }

  /**
   * Runs the given {@link GitLineHandler} in the current thread and returns the {@link GitCommandResult}.
   */
  private static GitCommandResult run(@NotNull GitLineHandler handler) {
    final List<String> errorOutput = new ArrayList<String>();
    final List<String> output = new ArrayList<String>();
    final AtomicInteger exitCode = new AtomicInteger();
    final AtomicBoolean startFailed = new AtomicBoolean();
    final AtomicReference<Throwable> exception = new AtomicReference<Throwable>();
    
    handler.addLineListener(new GitLineHandlerListener() {
      @Override public void onLineAvailable(String line, Key outputType) {
        if (isError(line)) {
          errorOutput.add(line);
        } else {
          output.add(line);
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

    if (handler instanceof GitLineHandlerPasswordRequestAware && ((GitLineHandlerPasswordRequestAware)handler).hadAuthRequest()) {
      errorOutput.add("Authentication failed");
    }

    final boolean success = !startFailed.get() && errorOutput.isEmpty() &&
                            (handler.isIgnoredErrorCode(exitCode.get()) || exitCode.get() == 0);
    return new GitCommandResult(success, exitCode.get(), errorOutput, output, null);
  }
  
  /**
   * Check if the line looks line an error message
   */
  private static boolean isError(String text) {
    for (String indicator : ERROR_INDICATORS) {
      if (text.startsWith(indicator.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  // could be upper-cased, so should check case-insensitively
  public static final String[] ERROR_INDICATORS = {
    "error", "remote: error", "fatal",
    "Cannot apply", "Could not", "Interactive rebase already started", "refusing to pull", "cannot rebase:", "conflict",
    "unable"
  };

}
