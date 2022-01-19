// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgDisposable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgInitCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.ui.HgInitAlreadyUnderHgDialog;
import org.zmlx.hg4idea.ui.HgInitDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import static com.intellij.util.ObjectUtils.notNull;
import static java.util.Objects.requireNonNull;
import static org.zmlx.hg4idea.HgNotificationIdsHolder.REPO_CREATED;
import static org.zmlx.hg4idea.HgNotificationIdsHolder.REPO_CREATION_ERROR;

/**
 * Action for initializing a Mercurial repository.
 * Command "hg init".
 */
public class HgInit extends DumbAwareAction {

  private Project myProject;

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project == null || project.isDefault() || TrustedProjects.isTrusted(project));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myProject = notNull(e.getData(CommonDataKeys.PROJECT), ProjectManager.getInstance().getDefaultProject());

    // provide window to select the root directory
    final HgInitDialog hgInitDialog = new HgInitDialog(myProject);
    if (!hgInitDialog.showAndGet()) {
      return;
    }
    final VirtualFile selectedRoot = hgInitDialog.getSelectedFolder();
    if (selectedRoot == null) {
      return;
    }

    // check if the selected folder is not yet under mercurial and provide some options in that case
    final VirtualFile vcsRoot = HgUtil.getNearestHgRoot(selectedRoot);
    VirtualFile mapRoot = selectedRoot;
    boolean needToCreateRepo = false;
    if (vcsRoot != null) {
      final HgInitAlreadyUnderHgDialog dialog = new HgInitAlreadyUnderHgDialog(myProject,
                                                                               selectedRoot.getPresentableUrl(),
                                                                               vcsRoot.getPresentableUrl());
      if (!dialog.showAndGet()) {
        return;
      }

      if (dialog.getAnswer() == HgInitAlreadyUnderHgDialog.Answer.USE_PARENT_REPO) {
        mapRoot = vcsRoot;
      }
      else if (dialog.getAnswer() == HgInitAlreadyUnderHgDialog.Answer.CREATE_REPO_HERE) {
        needToCreateRepo = true;
      }
    }
    else { // no parent repository => creating the repository here.
      needToCreateRepo = true;
    }

    boolean finalNeedToCreateRepo = needToCreateRepo;
    VirtualFile finalMapRoot = mapRoot;
    BackgroundTaskUtil.executeOnPooledThread(HgDisposable.getInstance(myProject), () -> {
      if (!finalNeedToCreateRepo || createRepository(requireNonNull(myProject), selectedRoot)) {
        updateDirectoryMappings(finalMapRoot);
      }
    });
  }

  // update vcs directory mappings if new repository was created inside the current project directory
  private void updateDirectoryMappings(VirtualFile mapRoot) {
    if (myProject != null && (!myProject.isDefault()) && myProject.getBaseDir() != null &&
        VfsUtilCore.isAncestor(myProject.getBaseDir(), mapRoot, false)) {
      mapRoot.refresh(false, false);
      final String path = mapRoot.equals(myProject.getBaseDir()) ? "" : mapRoot.getPath();
      ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(myProject);
      manager.setDirectoryMappings(VcsUtil.addMapping(manager.getDirectoryMappings(), path, HgVcs.VCS_NAME));
    }
  }

  public static boolean createRepository(@NotNull Project project, @NotNull final VirtualFile selectedRoot) {
    HgCommandResult result = new HgInitCommand(project).execute(selectedRoot.getPath());
    if (!HgErrorUtil.hasErrorsInCommandExecution(result)) {
      VcsNotifier.getInstance(project)
        .notifySuccess(REPO_CREATED,
                       HgBundle.message("hg4idea.init.created.notification.title"),
                       HgBundle.message("hg4idea.init.created.notification.description", selectedRoot.getPresentableUrl()));
      return true;
    }
    else {
      new HgCommandResultNotifier(project.isDefault() ? null : project)
        .notifyError(REPO_CREATION_ERROR,
                     result,
                     HgBundle.message("hg4idea.init.error.title"),
                     HgBundle.message("hg4idea.init.error.description", selectedRoot.getPresentableUrl()));
      return false;
    }
  }
}