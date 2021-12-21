// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import static git4idea.GitNotificationIdsHolder.INIT_FAILED;

public class GitInit extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(project == null || project.isDefault() || TrustedProjects.isTrusted(project));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(GitBundle.message("init.destination.directory.title"));
    fcd.setDescription(GitBundle.message("init.destination.directory.description"));
    fcd.setHideIgnored(false);
    VirtualFile baseDir = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (baseDir == null || !baseDir.isDirectory()) {
      baseDir = project.getBaseDir();
    }
    doInit(project, fcd, baseDir);
  }

  private static void doInit(@NotNull Project project, @NotNull FileChooserDescriptor fcd, VirtualFile baseDir) {
    FileChooser.chooseFile(fcd, project, baseDir, root -> {
      if (GitUtil.isUnderGit(root) && Messages.showYesNoDialog(project,
                                                               XmlStringUtil.wrapInHtml(
                                                                 GitBundle.message("init.warning.already.under.git",
                                                                                   StringUtil.escapeXmlEntities(root.getPresentableUrl()))),
                                                               GitBundle.message("init.warning.title"),
                                                               Messages.getWarningIcon()) != Messages.YES) {
        return;
      }

      new Task.Backgroundable(project, GitBundle.message("common.refreshing")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          GitCommandResult result = Git.getInstance().init(project, root);
          if (!result.success()) {
            VcsNotifier.getInstance(project).notifyError(INIT_FAILED, GitBundle.message("action.Git.Init.error"), result.getErrorOutputAsHtmlString(), true);
            return;
          }

          if (project.isDefault()) {
            return;
          }

          refreshAndConfigureVcsMappings(project, root, root.getPath());
        }
      }.queue();
    });
  }

  public static void refreshAndConfigureVcsMappings(@NotNull Project project, @NotNull VirtualFile root, @NotNull String path) {
    GitUtil.refreshVfsInRoot(root);
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
    manager.setDirectoryMappings(VcsUtil.addMapping(manager.getDirectoryMappings(), path, GitVcs.NAME));
    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(root);
  }
}
