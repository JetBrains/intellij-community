/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

import static com.intellij.util.containers.UtilKt.isEmpty;
import static com.intellij.util.containers.UtilKt.notNullize;

public class GitAdd extends ScheduleForAdditionAction {
  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    if (super.isEnabled(e)) return true;

    Project project = e.getProject();
    if (project == null) return false;

    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes != null && !isEmpty(collectPathsFromChanges(project, Stream.of(changes)))) return true;

    Stream<VirtualFile> files = e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM);
    if (files != null && !isEmpty(collectPathsFromFiles(project, files))) return true;

    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    Set<FilePath> toAdd = new HashSet<>();

    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Stream<Change> changeStream = changes != null ? Stream.of(changes) : Stream.empty();
    ContainerUtil.addAll(toAdd, collectPathsFromChanges(project, changeStream).iterator());

    Stream<VirtualFile> files = notNullize(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM));
    ContainerUtil.addAll(toAdd, collectPathsFromFiles(project, files).iterator());

    // add unversioned (can show dialog inside - so we can't use DataContext after this call)
    super.actionPerformed(e);

    List<VcsException> exceptions = new ArrayList<>();
    ProgressManager.getInstance().run(new Task.Modal(project, "Adding Files to VCS...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);

        try {
          Map<VirtualFile, List<FilePath>> pathsByRoot = GitUtil.sortFilePathsByGitRoot(toAdd);
          for (Map.Entry<VirtualFile, List<FilePath>> e : pathsByRoot.entrySet()) {
            VirtualFile root = e.getKey();
            GitFileUtils.addPaths(project, root, e.getValue());
            dirtyScopeManager.dirDirtyRecursively(root);
          }
        }
        catch (VcsException ex) {
          exceptions.add(ex);
        }
      }
    });

    if (!exceptions.isEmpty()) {
      StringBuilder message = new StringBuilder(VcsBundle.message("error.adding.files.prompt"));
      for (VcsException ex : exceptions) {
        message.append("\n").append(ex.getMessage());
      }
      Messages.showErrorDialog(project, message.toString(), VcsBundle.message("error.adding.files.title"));
    }
  }

  @NotNull
  private static Stream<FilePath> collectPathsFromChanges(@NotNull Project project, @NotNull Stream<Change> allChanges) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);

    return allChanges
      .filter(change -> {
        FilePath filePath = ChangesUtil.getFilePath(change);
        return vcsManager.getVcsFor(filePath) instanceof GitVcs &&
               isStatusForAddition(change.getFileStatus());
      })
      .map(ChangesUtil::getFilePath);
  }

  @NotNull
  private static Stream<FilePath> collectPathsFromFiles(@NotNull Project project, @NotNull Stream<VirtualFile> allFiles) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);

    return allFiles
      .filter(file -> {
        return vcsManager.getVcsFor(file) instanceof GitVcs &&
               (file.isDirectory() || isStatusForAddition(changeListManager.getStatus(file)));
      })
      .map(VcsUtil::getFilePath);
  }

  private static boolean isStatusForAddition(@NotNull FileStatus status) {
    return status == FileStatus.MODIFIED ||
           status == FileStatus.MERGED_WITH_CONFLICTS ||
           status == FileStatus.ADDED ||
           status == FileStatus.DELETED;
  }
}
