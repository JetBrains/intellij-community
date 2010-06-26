package org.zmlx.hg4idea.action;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgInitCommand;
import org.zmlx.hg4idea.ui.HgInitAlreadyUnderHgDialog;
import org.zmlx.hg4idea.ui.HgInitDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Action for initializing Mercurial repository. Command "hg init".
 * @author Kirill Likhodedov
 */
public class HgInit extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(HgInit.class.getName());
  private Project myProject;

  public HgInit() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myProject = e.getData(PlatformDataKeys.PROJECT);
    if (myProject == null) {
      LOG.warn("[actionPerformed] project is null");
      return;
    }

    // provide window to select the root directory
    final HgInitDialog hgInitDialog = new HgInitDialog(myProject);
    hgInitDialog.show();
    if (!hgInitDialog.isOK()) {
      return;
    }
    final VirtualFile selectedRoot = hgInitDialog.getSelectedFolder();
    if (selectedRoot == null) {
      return;
    }

    // check if it the project is not yet under mercurial and provide some options in that case
    final VirtualFile vcsRoot = HgUtil.getNearestHgRoot(selectedRoot);
    VirtualFile mapRoot = selectedRoot;
    if (vcsRoot != null) {
      final HgInitAlreadyUnderHgDialog dialog = new HgInitAlreadyUnderHgDialog(myProject,
                                                   selectedRoot.getPresentableUrl(), vcsRoot.getPresentableUrl());
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }

      if (dialog.getAnswer() == HgInitAlreadyUnderHgDialog.Answer.USE_PARENT_REPO) {
        mapRoot = vcsRoot;
      } else if (dialog.getAnswer() == HgInitAlreadyUnderHgDialog.Answer.CREATE_REPO_HERE) {
        createRepository(selectedRoot);
      }
    } else { // no parent repository => creating the repository here.
       createRepository(selectedRoot);
    }

    // update vcs directory mappings
    mapRoot.refresh(false, false);
    final String path = mapRoot.equals(myProject.getBaseDir()) ? "" : mapRoot.getPath();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final List<VcsDirectoryMapping> vcsDirectoryMappings = new ArrayList<VcsDirectoryMapping>(vcsManager.getDirectoryMappings());
    VcsDirectoryMapping mapping = new VcsDirectoryMapping(path, HgVcs.VCS_NAME);
    for (int i = 0; i < vcsDirectoryMappings.size(); i++) {
      final VcsDirectoryMapping m = vcsDirectoryMappings.get(i);
      if (m.getDirectory().equals(path)) {
        if (m.getVcs().length() == 0) {
          vcsDirectoryMappings.set(i, mapping);
          mapping = null;
          break;
        }
        else if (m.getVcs().equals(mapping.getVcs())) {
          mapping = null;
          break;
        }
      }
    }
    if (mapping != null) {
      vcsDirectoryMappings.add(mapping);
    }
    vcsManager.setDirectoryMappings(vcsDirectoryMappings);
    vcsManager.updateActiveVcss();
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(project != null);
    presentation.setVisible(project != null);
  }

  private void createRepository(VirtualFile selectedRoot) {
    if ((new HgInitCommand(myProject)).execute(selectedRoot)) {
      Notifications.Bus.notify(new Notification(HgVcs.NOTIFICATION_GROUP_ID,
                                              HgVcsMessages.message("hg4idea.init.created.notification.title"),
                                              HgVcsMessages.message("hg4idea.init.created.notification.description", selectedRoot.getPresentableUrl()),
                                              NotificationType.INFORMATION), myProject);
    }
  }
  
}
