// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author irengrig
 */
public final class ShowAllAffectedGenericAction extends AnAction implements DumbAware {

  private static final String ACTION_ID = "VcsHistory.ShowAllAffected";

 // use getInstance()
  private ShowAllAffectedGenericAction() {
  }

  public static ShowAllAffectedGenericAction getInstance() {
    return (ShowAllAffectedGenericAction)ActionManager.getInstance().getAction(ACTION_ID);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    final VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    if (vcsKey == null) return;
    final VcsFileRevision revision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    final Boolean isNonLocal = e.getData(VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION);
    if ((revision != null) && (revisionVirtualFile != null)) {
      showSubmittedFiles(project, revision.getRevisionNumber(), revisionVirtualFile, vcsKey, revision.getChangedRepositoryPath(),
                         Boolean.TRUE.equals(isNonLocal));
    }
  }

  public static void showSubmittedFiles(@NotNull Project project,
                                        @NotNull VcsRevisionNumber revision,
                                        @NotNull VirtualFile virtualFile,
                                        @NotNull VcsKey vcsKey) {
    showSubmittedFiles(project, revision, virtualFile, vcsKey, null, false);
  }

  public static void showSubmittedFiles(@NotNull Project project,
                                        @NotNull VcsRevisionNumber revision,
                                        @NotNull VirtualFile virtualFile,
                                        @NotNull VcsKey vcsKey,
                                        @Nullable RepositoryLocation location,
                                        boolean isNonLocal) {
    AbstractVcsHelper.getInstance(project).loadAndShowCommittedChangesDetails(project, revision, virtualFile, vcsKey, location, isNonLocal);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VcsKey vcsKey = e.getData(VcsDataKeys.VCS);
    if (project == null || vcsKey == null) {
      e.getPresentation().setEnabled(false);
      return;
    }
    final Boolean isNonLocal = e.getData(VcsDataKeys.VCS_NON_LOCAL_HISTORY_SESSION);
    final VirtualFile revisionVirtualFile = e.getData(VcsDataKeys.VCS_VIRTUAL_FILE);
    boolean enabled = (e.getData(VcsDataKeys.VCS_FILE_REVISION) != null) && (revisionVirtualFile != null);
    enabled = enabled && (! Boolean.TRUE.equals(isNonLocal) || canPresentNonLocal(project, vcsKey, revisionVirtualFile));
    e.getPresentation().setEnabled(enabled);
  }

  private static boolean canPresentNonLocal(@NotNull Project project, @NotNull VcsKey key, @NotNull VirtualFile file) {
    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(key.getName());
    if (vcs == null) return false;
    final CommittedChangesProvider provider = vcs.getCommittedChangesProvider();
    if (provider == null) return false;
    return provider.getForNonLocal(file) != null;
  }
}
