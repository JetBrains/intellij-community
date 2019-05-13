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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.UtilKt;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitVcs;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.Functions.identity;
import static com.intellij.util.containers.UtilKt.isEmpty;

public class GitAdd extends ScheduleForAdditionAction {
  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;

    if (!isEmpty(getUnversionedFiles(e, project))) return true;

    Stream<Change> changeStream = UtilKt.stream(e.getData(VcsDataKeys.CHANGES));
    if (!isEmpty(collectPathsFromChanges(project, changeStream))) return true;

    Stream<VirtualFile> filesStream = UtilKt.notNullize(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM));
    if (!isEmpty(collectPathsFromFiles(project, filesStream))) return true;

    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);

    Set<FilePath> toAdd = new HashSet<>();

    Stream<Change> changeStream = UtilKt.stream(e.getData(VcsDataKeys.CHANGES));
    ContainerUtil.addAll(toAdd, collectPathsFromChanges(project, changeStream).iterator());

    Stream<VirtualFile> filesStream = UtilKt.notNullize(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM));
    ContainerUtil.addAll(toAdd, collectPathsFromFiles(project, filesStream).iterator());

    List<VirtualFile> unversionedFiles = getUnversionedFiles(e, project).collect(Collectors.toList());

    addUnversioned(project, unversionedFiles, e.getData(ChangesBrowserBase.DATA_KEY),
                   !toAdd.isEmpty() ? (indicator, exceptions) -> addPathsToVcs(project, toAdd, exceptions) : null);
  }

  private static void addPathsToVcs(@NotNull Project project, @NotNull Collection<FilePath> toAdd, @NotNull List<VcsException> exceptions) {
    VcsUtil.groupByRoots(project, toAdd, identity()).forEach((vcsRoot, paths) -> {
      try {
        if (!(vcsRoot.getVcs() instanceof GitVcs)) return;

        VirtualFile root = vcsRoot.getPath();
        if (root == null) return;

        GitFileUtils.addPaths(project, root, paths);
        VcsFileUtil.markFilesDirty(project, paths);
      }
      catch (VcsException ex) {
        exceptions.add(ex);
      }
    });
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

  private static boolean isStatusForAddition(FileStatus status) {
    return status == FileStatus.MODIFIED ||
           status == FileStatus.MERGED_WITH_CONFLICTS ||
           status == FileStatus.ADDED ||
           status == FileStatus.DELETED;
  }
}
