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
package git4idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.commands.*;
import git4idea.push.GitPushSpec;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collection of common native Git commands.
 *
 * @author Kirill Likhodedov
 */
public class Git {

  private static final Logger LOG = Logger.getInstance(Git.class);

  private Git() {
  }

  /**
   * Calls 'git init' on the specified directory.
   * // TODO use common format
   */
  public static void init(Project project, VirtualFile root) throws VcsException {
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.INIT);
    h.setSilent(false);
    h.setNoSSH(true);
    h.run();
    if (!h.errors().isEmpty()) {
      throw h.errors().get(0);
    }
  }

  /**
   * <p>Queries Git for the unversioned files in the given paths.</p>
   * <p>
   *   <b>Note:</b> this method doesn't check for ignored files. You have to check if the file is ignored afterwards, if needed.
   * </p>
   *
   * @param files files that are to be checked for the unversioned files among them.
   *              <b>Pass <code>null</code> to query the whole repository.</b>
   * @return Unversioned files from the given scope.
   */
  @NotNull
  public static Set<VirtualFile> untrackedFiles(@NotNull Project project,
                                                @NotNull VirtualFile root,
                                                @Nullable Collection<VirtualFile> files) throws VcsException {
    final Set<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();

    if (files == null) {
      untrackedFiles.addAll(untrackedFilesNoChunk(project, root, null));
    } else {
      for (List<String> relativePaths : VcsFileUtil.chunkFiles(root, files)) {
        untrackedFiles.addAll(untrackedFilesNoChunk(project, root, relativePaths));
      }
    }

    return untrackedFiles;
  }

  // relativePaths are guaranteed to fit into command line length limitations.
  @NotNull
  private static Collection<VirtualFile> untrackedFilesNoChunk(@NotNull Project project,
                                                               @NotNull VirtualFile root,
                                                               @Nullable List<String> relativePaths)
    throws VcsException {
    final Set<VirtualFile> untrackedFiles = new HashSet<VirtualFile>();
    GitSimpleHandler h = new GitSimpleHandler(project, root, GitCommand.LS_FILES);
    h.setNoSSH(true);
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

  /**
   * {@code git checkout &lt;reference&gt;} <br/>
   * {@code git checkout -b &lt;newBranch&gt; &lt;reference&gt;}
   */
  public static GitCommandResult checkout(@NotNull GitRepository repository,
                                          @NotNull String reference,
                                          @Nullable String newBranch,
                                          @NotNull GitLineHandlerListener... listeners) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECKOUT);
    h.setSilent(false);
    if (newBranch == null) { // simply checkout
      h.addParameters(reference);
    } else { // checkout reference as new branch
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
  public static GitCommandResult checkoutNewBranch(@NotNull GitRepository repository, @NotNull String branchName,
                                                   @Nullable GitLineHandlerListener listener) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.CHECKOUT);
    h.setSilent(false);
    h.addParameters("-b");
    h.addParameters(branchName);
    if (listener != null) {
      h.addLineListener(listener);
    }
    return run(h);
  }

  public static GitCommandResult createNewTag(@NotNull GitRepository repository, @NotNull String tagName,
                                                     @Nullable GitLineHandlerListener listener, String reference) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.TAG);
    h.setSilent(false);
    h.addParameters(tagName);
    if (reference != null && ! reference.isEmpty()) {
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
  public static GitCommandResult branchDelete(@NotNull GitRepository repository,
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
  @NotNull
  public static GitCommandResult branchContains(@NotNull GitRepository repository, @NotNull String commit) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.BRANCH);
    h.addParameters("--contains", commit);
    return run(h);
  }

  /**
   * Returns the last (tip) commit on the given branch.<br/>
   * {@code git rev-list -1 <branchName>}
   */
  public static GitCommandResult tip(@NotNull GitRepository repository, @NotNull String branchName) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.REV_LIST);
    h.addParameters("-1");
    h.addParameters(branchName);
    return run(h);
  }

  public static GitCommandResult push(@NotNull GitRepository repository, @NotNull GitPushSpec pushSpec, @NotNull GitLineHandlerListener... listeners) {
    final GitLineHandler h = new GitLineHandler(repository.getProject(), repository.getRoot(), GitCommand.PUSH);
    h.setSilent(false);

    for (GitLineHandlerListener listener : listeners) {
      h.addLineListener(listener);
    }
    if (!pushSpec.isPushAll()) {
      GitRemote remote = pushSpec.getRemote();
      LOG.assertTrue(remote != null, "Remote can't be null: " + pushSpec);
      h.addParameters(remote.getName());
      GitBranch remoteBranch = pushSpec.getDest();
      String destination = remoteBranch.getName().replaceFirst(remote.getName() + "/", "");
      h.addParameters(pushSpec.getSource().getName() + ":" + destination);
    }
    return run(h, true);
  }

  private static GitCommandResult run(@NotNull GitLineHandler handler) {
    return run(handler, false);
  } 

  /**
   * Runs the given {@link GitLineHandler} in the current thread and returns the {@link GitCommandResult}.
   */
  private static GitCommandResult run(@NotNull GitLineHandler handler, boolean remote) {
    handler.setNoSSH(!remote);

    final List<String> errorOutput = new ArrayList<String>();
    final List<String> output = new ArrayList<String>();
    final AtomicInteger exitCode = new AtomicInteger();
    final AtomicBoolean startFailed = new AtomicBoolean();
    
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

      @Override public void startFailed(Throwable exception) {
        startFailed.set(true);
        errorOutput.add("Failed to start Git process");
        errorOutput.add(ExceptionUtil.getThrowableText(exception));
      }
    });
    
    handler.runInCurrentThread(null);
    final boolean success = !startFailed.get() && errorOutput.isEmpty() && (handler.isIgnoredErrorCode(exitCode.get()) || exitCode.get() == 0);
    return new GitCommandResult(success, exitCode.get(), errorOutput, output);
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
  private static final String[] ERROR_INDICATORS = {
    "error", "fatal", "Cannot apply", "Could not", "Interactive rebase already started", "refusing to pull", "cannot rebase:", "conflict"
  };

}
