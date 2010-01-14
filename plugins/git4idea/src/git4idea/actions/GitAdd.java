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
package git4idea.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitFileUtils;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Git "add" action
 */
public class GitAdd extends BasicAction {

  @Override
  public boolean perform(@NotNull final Project project,
                         final GitVcs vcs,
                         @NotNull final List<VcsException> exceptions,
                         @NotNull final VirtualFile[] affectedFiles) {
    saveAll();
    if (!ProjectLevelVcsManager.getInstance(project).checkAllFilesAreUnder(GitVcs.getInstance(project), affectedFiles)) return false;
    return toBackground(project, vcs, affectedFiles, exceptions, new Consumer<ProgressIndicator>() {
      public void consume(ProgressIndicator indicator) {
        try {
          addFiles(project, affectedFiles, indicator);
        }
        catch (VcsException e) {
          exceptions.add(e);
        }
      }
    });

  }

  /**
   * Add the specified files to the project.
   *
   * @param project The project to add files to
   * @param files   The files to add  @throws VcsException If an error occurs
   * @param pi      progress indicator
   */
  public static void addFiles(@NotNull final Project project, @NotNull final VirtualFile[] files, ProgressIndicator pi)
    throws VcsException {
    final Map<VirtualFile, List<VirtualFile>> roots = GitUtil.sortFilesByGitRoot(Arrays.asList(files));
    for (Map.Entry<VirtualFile, List<VirtualFile>> entry : roots.entrySet()) {
      pi.setText(entry.getKey().getPresentableUrl());
      GitFileUtils.addFiles(project, entry.getKey(), entry.getValue());
    }
  }

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("add.action.name");
  }

  @Override
  protected boolean isEnabled(@NotNull Project project, @NotNull GitVcs vcs, @NotNull VirtualFile... vFiles) {
    for (VirtualFile file : vFiles) {
      FileStatus fileStatus = FileStatusManager.getInstance(project).getStatus(file);
      if (fileStatus == FileStatus.NOT_CHANGED || fileStatus == FileStatus.DELETED) return false;
    }
    return true;
  }
}
