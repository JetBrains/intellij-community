// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtilRt.newArrayList;

/**
 * Git merge tool for resolving conflicts. Use IDEA built-in 3-way merge tool.
 */
public class GitResolveConflictsAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = notNull(event.getProject());
    GitVcs vcs = GitVcs.getInstance(project);

    Set<VirtualFile> conflictedFiles = new TreeSet<>(Comparator.comparing(VirtualFile::getPresentableUrl));
    for (Change change : ChangeListManager.getInstance(project).getAllChanges()) {
      if (change.getFileStatus() != FileStatus.MERGED_WITH_CONFLICTS) {
        continue;
      }
      ContentRevision before = change.getBeforeRevision();
      ContentRevision after = change.getAfterRevision();
      if (before != null) {
        VirtualFile file = before.getFile().getVirtualFile();
        if (file != null) {
          conflictedFiles.add(file);
        }
      }
      if (after != null) {
        VirtualFile file = after.getFile().getVirtualFile();
        if (file != null) {
          conflictedFiles.add(file);
        }
      }
    }

    AbstractVcsHelper.getInstance(project).showMergeDialog(newArrayList(conflictedFiles), vcs.getMergeProvider());
  }

  private static boolean isEnabled(@NotNull Project project) {
    final Collection<Change> changes = ChangeListManager.getInstance(project).getAllChanges();
    if (changes.size() > 1000) {
      return true;
    }
    for (Change change : changes) {
      if (change.getFileStatus() == FileStatus.MERGED_WITH_CONFLICTS) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDisposed()) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setEnabled(isEnabled(project));
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(e.getPresentation().isEnabled());
    }
  }
}
