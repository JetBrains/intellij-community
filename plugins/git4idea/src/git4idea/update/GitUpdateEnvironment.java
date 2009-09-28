/*
 * Copyright 2000-2008 JetBrains s.r.o.
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
package git4idea.update;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitHandler;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.merge.MergeChangeCollector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Git update environment implementation. The environment does just a simple
 * {@code git pull -v} for each content root.
 */
public class GitUpdateEnvironment implements UpdateEnvironment {
  /**
   * The context project
   */
  private final Project myProject;

  /**
   * A constructor from settings
   *
   * @param project a project
   */
  public GitUpdateEnvironment(@NotNull Project project) {
    myProject = project;
  }

  /**
   * {@inheritDoc}
   */
  public void fillGroups(UpdatedFiles updatedFiles) {
    //unused, there are no custom categories yet
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public UpdateSession updateDirectories(@NotNull FilePath[] filePaths,
                                         UpdatedFiles updatedFiles,
                                         ProgressIndicator progressIndicator,
                                         @NotNull Ref<SequentialUpdatesContext> sequentialUpdatesContextRef)
    throws ProcessCanceledException {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    for (VirtualFile root : GitUtil.gitRoots(Arrays.asList(filePaths))) {
      try {
        // check if there is a remote for the branch
        final GitBranch branch = GitBranch.current(myProject, root);
        if (branch == null) {
          continue;
        }
        final String value = GitConfigUtil.getValue(myProject, root, "branch." + branch.getName() + ".remote");
        if (value == null || value.length() == 0) {
          continue;
        }
        // remember the current position
        GitRevisionNumber before = GitRevisionNumber.resolve(myProject, root, "HEAD");
        // do pull
        GitLineHandler h = new GitLineHandler(myProject, root, GitHandler.PULL);
        // ignore merge failure for the pull
        h.ignoreErrorCode(1);
        h.addParameters("--no-stat");
        h.addParameters("-v");
        try {
          GitHandlerUtil.doSynchronouslyWithExceptions(h, progressIndicator);
        }
        finally {
          exceptions.addAll(h.errors());
          // find out what have changed
          MergeChangeCollector collector = new MergeChangeCollector(myProject, root, before, updatedFiles);
          collector.collect(exceptions);
        }
      }
      catch (VcsException ex) {
        exceptions.add(ex);
      }
    }
    return new GitUpdateSession(exceptions);
  }


  /**
   * {@inheritDoc}
   */
  public boolean validateOptions(Collection<FilePath> filePaths) {
    for (FilePath p : filePaths) {
      if (!GitUtil.isUnderGit(p)) {
        return false;
      }
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public Configurable createConfigurable(Collection<FilePath> files) {
    return null;
  }
}
