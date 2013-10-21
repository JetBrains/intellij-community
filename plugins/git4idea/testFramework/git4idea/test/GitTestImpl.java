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
package git4idea.test;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitCommit;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitImpl;
import git4idea.commands.GitLineHandlerListener;
import git4idea.push.GitPushSpec;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.join;
import static git4idea.test.GitExecutor.cd;
import static git4idea.test.GitExecutor.git;
import static java.lang.String.format;

/**
 * @author Kirill Likhodedov
 */
public class GitTestImpl implements Git {

  @NotNull
  @Override
  public GitCommandResult init(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitLineHandlerListener... listeners) {
    return execute(root.getPath(), "init", listeners);
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
                                @NotNull String clonedDirectoryName, @NotNull GitLineHandlerListener... progressListeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult config(@NotNull GitRepository repository, String... params) {
    cd(repository);
    String output = git("config " + join(params, " "));
    int exitCode = output.trim().isEmpty() ? 1 : 0;
    return new GitCommandResult(!output.contains("fatal") && exitCode == 0, exitCode, Collections.<String>emptyList(),
                                Arrays.asList(StringUtil.splitByLines(output)), null);
  }

  @NotNull
  @Override
  public GitCommandResult diff(@NotNull GitRepository repository, @NotNull List<String> parameters, @NotNull String range) {
    return execute(repository, format("diff %s %s", join(parameters, " "), range));
  }

  @NotNull
  @Override
  public GitCommandResult checkAttr(@NotNull final GitRepository repository, @NotNull Collection<String> attributes,
                                    @NotNull Collection<VirtualFile> files) {
    cd(repository);
    Collection<String> relativePaths = Collections2.transform(files, new Function<VirtualFile, String>() {
      @Override
      public String apply(VirtualFile input) {
        return FileUtil.getRelativePath(repository.getRoot().getPath(), input.getPath(), '/');
      }
    });
    String output = git("check-attr %s -- %s", join(attributes, " "), join(relativePaths, " "));
    return commandResult(output);
  }

  @NotNull
  @Override
  public GitCommandResult stashSave(@NotNull GitRepository repository, @NotNull String message) {
    return execute(repository, "stash save " + message);
  }

  @NotNull
  @Override
  public GitCommandResult stashPop(@NotNull GitRepository repository, @NotNull GitLineHandlerListener... listeners) {
    return execute(repository, "stash pop");
  }

  @NotNull
  @Override
  public List<GitCommit> history(@NotNull GitRepository repository, @NotNull String range) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public GitCommandResult merge(@NotNull GitRepository repository, @NotNull String branchToMerge, @Nullable List<String> additionalParams,
                                @NotNull GitLineHandlerListener... listeners) {
    String addParams = additionalParams == null ? "" : join(additionalParams, " ");
    return execute(repository, format("merge %s %s", addParams, branchToMerge), listeners);
  }

  @NotNull
  @Override
  public GitCommandResult checkout(@NotNull GitRepository repository, @NotNull String reference, @Nullable String newBranch, boolean force,
                                   @NotNull GitLineHandlerListener... listeners) {
    return execute(repository, format("checkout %s %s %s",
                                      force ? "--force" : "",
                                      newBranch != null ? " -b " + newBranch : "", reference), listeners);
  }

  @NotNull
  @Override
  public GitCommandResult checkoutNewBranch(@NotNull GitRepository repository, @NotNull String branchName,
                                            @Nullable GitLineHandlerListener listener) {
    return execute(repository, "checkout -b " + branchName, listener);
  }

  @NotNull
  @Override
  public GitCommandResult branchDelete(@NotNull GitRepository repository, @NotNull String branchName, boolean force,
                                       @NotNull GitLineHandlerListener... listeners) {
    return execute(repository, format("branch %s %s", force ? "-D" : "-d", branchName), listeners);
  }

  @NotNull
  @Override
  public GitCommandResult branchContains(@NotNull GitRepository repository, @NotNull String commit) {
    return execute(repository, "branch --contains " + commit);
  }

  @NotNull
  @Override
  public GitCommandResult branchCreate(@NotNull GitRepository repository, @NotNull String branchName) {
    return execute(repository, "branch " + branchName);
  }

  @NotNull
  @Override
  public GitCommandResult resetHard(@NotNull GitRepository repository, @NotNull String revision) {
    return execute(repository, "reset --hard " + revision);
  }

  @NotNull
  @Override
  public GitCommandResult resetMerge(@NotNull GitRepository repository, @Nullable String revision) {
    return execute(repository, "reset --merge " + revision);
  }

  @NotNull
  @Override
  public GitCommandResult tip(@NotNull GitRepository repository, @NotNull String branchName) {
    return execute(repository, "rev-list -1 " + branchName);
  }

  @NotNull
  @Override
  public GitCommandResult push(@NotNull GitRepository repository, @NotNull String remote, @NotNull String url, @NotNull String spec,
                               boolean updateTracking, @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult push(@NotNull GitRepository repository, @NotNull String remote, @NotNull String url, @NotNull String spec,
                               @NotNull GitLineHandlerListener... listeners) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult push(@NotNull GitRepository repository,
                               @NotNull GitPushSpec spec, @NotNull String url, @NotNull GitLineHandlerListener... listeners) {
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
    return execute(repository, format("cherry-pick -x %s %s", autoCommit ? "" : "-n", hash), listeners);
  }

  @NotNull
  @Override
  public GitCommandResult fetch(@NotNull GitRepository repository, @NotNull String url, @NotNull String remote, String... params) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public GitCommandResult getUnmergedFiles(@NotNull GitRepository repository) {
    return execute(repository, "ls-files --unmerged");
  }

  @NotNull
  @Override
  public GitCommandResult createNewTag(@NotNull GitRepository repository,
                                       @NotNull String tagName,
                                       @Nullable GitLineHandlerListener listener,
                                       @NotNull String reference) {
    throw new UnsupportedOperationException();
  }

  private static GitCommandResult commandResult(String output) {
    List<String> err = new ArrayList<String>();
    List<String> out = new ArrayList<String>();
    for (String line : output.split("\n")) {
      if (isError(line)) {
        err.add(line);
      }
      else {
        out.add(line);
      }
    }
    boolean success = err.isEmpty();
    return new GitCommandResult(success, 0, err, out, null);
  }

  private static boolean isError(String s) {
    // we don't want to make that method public, since it is reused only in the test.
    try {
      Method m = GitImpl.class.getDeclaredMethod("isError", String.class);
      m.setAccessible(true);
      return (Boolean) m.invoke(null, s);
    }
    catch (NoSuchMethodException e) {
      e.printStackTrace();
    }
    catch (InvocationTargetException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return true;
  }

  private static void feedOutput(String output, GitLineHandlerListener... listeners) {
    for (GitLineHandlerListener listener : listeners) {
      String[] split = output.split("\n");
      for (String line : split) {
        listener.onLineAvailable(line, ProcessOutputTypes.STDERR);
      }
    }
  }

  private static GitCommandResult execute(GitRepository repository, String operation, GitLineHandlerListener... listeners) {
    return execute(repository.getRoot().getPath(), operation, listeners);
  }

  private static GitCommandResult execute(String workingDir, String operation, GitLineHandlerListener... listeners) {
    cd(workingDir);
    String out = git(operation);
    feedOutput(out, listeners);
    return commandResult(out);
  }

}
