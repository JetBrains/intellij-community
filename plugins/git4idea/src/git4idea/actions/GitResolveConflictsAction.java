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

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import static com.intellij.util.containers.ContainerUtilRt.newArrayList;

/**
 * Git merge tool for resolving conflicts. Use IDEA built-in 3-way merge tool.
 */
public class GitResolveConflictsAction extends GitAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = ObjectUtils.assertNotNull(event.getProject());
    GitVcs vcs = ObjectUtils.assertNotNull(GitVcs.getInstance(project));

    final Set<VirtualFile> conflictedFiles = new TreeSet<>(new Comparator<VirtualFile>() {
      @Override
      public int compare(@NotNull VirtualFile f1, @NotNull VirtualFile f2) {
        return f1.getPresentableUrl().compareTo(f2.getPresentableUrl());
      }
    });
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
    for (GitRepository repository : GitUtil.getRepositoriesForFiles(project, conflictedFiles)) {
      repository.update();
    }
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent event) {
    final Collection<Change> changes = ChangeListManager.getInstance(event.getProject()).getAllChanges();
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
    super.update(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setVisible(e.getPresentation().isEnabled());
    }
  }
}
