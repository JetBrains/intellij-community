/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.config.GitVcsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Git update environment implementation. The environment does
 * {@code git pull -v} for each vcs root. Rebase variant is detected
 * and processed as well.
 */
public class GitUpdateEnvironment implements UpdateEnvironment {
  /**
   * The vcs instance
   */
  private final GitVcs myVcs;
  /**
   * The context project
   */
  private final Project myProject;
  /**
   * The project settings
   */
  private final GitVcsSettings mySettings;

  /**
   * A constructor from settings
   *
   * @param project a project
   */
  public GitUpdateEnvironment(@NotNull Project project, @NotNull GitVcs vcs, GitVcsSettings settings) {
    myVcs = vcs;
    myProject = project;
    mySettings = settings;
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
    Set<VirtualFile> roots = GitUtil.gitRoots(Arrays.asList(filePaths));
    List<VcsException> exceptions = new ArrayList<VcsException>();
    new GitUpdateProcess(myProject, mySettings, myVcs, updatedFiles, exceptions).doUpdate( progressIndicator, roots);
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
    return new GitUpdateConfigurable(mySettings);
  }

}
