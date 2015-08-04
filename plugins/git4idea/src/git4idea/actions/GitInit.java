/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.components.ServiceManager;
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
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

/**
 * Initialize git repository action
 */
public class GitInit extends DumbAwareAction {

  @Override
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(GitBundle.getString("init.destination.directory.title"));
    fcd.setDescription(GitBundle.getString("init.destination.directory.description"));
    fcd.setHideIgnored(false);
    VirtualFile baseDir = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (baseDir == null) {
      baseDir = project.getBaseDir();
    }
    doInit(project, fcd, baseDir, baseDir);
  }

  private static void doInit(final Project project, FileChooserDescriptor fcd, VirtualFile baseDir, final VirtualFile finalBaseDir) {
    FileChooser.chooseFile(fcd, project, baseDir, new Consumer<VirtualFile>() {
      @Override
      public void consume(final VirtualFile root) {
        if (GitUtil.isUnderGit(root) && Messages.showYesNoDialog(project,
                                                                 GitBundle.message("init.warning.already.under.git",
                                                                                   StringUtil.escapeXml(root.getPresentableUrl())),
                                                                 GitBundle.getString("init.warning.title"),
                                                                 Messages.getWarningIcon()) != Messages.YES) {
          return;
        }

        GitCommandResult result = ServiceManager.getService(Git.class).init(project, root);
        if (!result.success()) {
          GitVcs vcs = GitVcs.getInstance(project);
          if (vcs != null && vcs.getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
            VcsNotifier.getInstance(project).notifyError("Git init failed", result.getErrorOutputAsHtmlString());
          }
          return;
        }

        if (project.isDefault()) {
          return;
        }
        final String path = root.equals(finalBaseDir) ? "" : root.getPath();
        GitVcs.runInBackground(new Task.Backgroundable(project, GitBundle.getString("common.refreshing")) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            refreshAndConfigureVcsMappings(project, root, path);
          }
        });
      }
    });
  }

  public static void refreshAndConfigureVcsMappings(final Project project, final VirtualFile root, final String path) {
    VfsUtil.markDirtyAndRefresh(false, true, false, root);
    ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
    manager.setDirectoryMappings(VcsUtil.addMapping(manager.getDirectoryMappings(), path, GitVcs.NAME));
    manager.updateActiveVcss();
    VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(root);
  }

}
