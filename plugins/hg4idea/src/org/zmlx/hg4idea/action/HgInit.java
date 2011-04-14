package org.zmlx.hg4idea.action;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgInitCommand;
import org.zmlx.hg4idea.ui.HgInitAlreadyUnderHgDialog;
import org.zmlx.hg4idea.ui.HgInitDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Action for initializing a Mercurial repository.
 * Command "hg init".
 * @author Kirill Likhodedov
 */
public class HgInit extends DumbAwareAction {

  private Project myProject;

  @Override
  public void actionPerformed(AnActionEvent e) {
    myProject = e.getData(PlatformDataKeys.PROJECT);
    if (myProject == null) {
      myProject = ProjectManager.getInstance().getDefaultProject();
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

    // check if the selected folder is not yet under mercurial and provide some options in that case
    final VirtualFile vcsRoot = HgUtil.getNearestHgRoot(selectedRoot);
    VirtualFile mapRoot = selectedRoot;
    boolean needToCreateRepo = false;
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
        needToCreateRepo = true;
      }
    } else { // no parent repository => creating the repository here.
      needToCreateRepo = true;
    }

    if (needToCreateRepo) {
      createRepository(selectedRoot, mapRoot); 
    } else {
      updateDirectoryMappings(mapRoot);
    }
  }

  // update vcs directory mappings if new repository was created inside the current project directory
  private void updateDirectoryMappings(VirtualFile mapRoot) {
    if (myProject != null && (! myProject.isDefault()) && myProject.getBaseDir() != null && VfsUtil
      .isAncestor(myProject.getBaseDir(), mapRoot, false)) {
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
  }

  private void createRepository(final VirtualFile selectedRoot, final VirtualFile mapRoot) {
    new HgInitCommand(myProject).execute(selectedRoot, new Consumer<Boolean>() {
      @Override
      public void consume(Boolean succeeded) {
        if (succeeded) {
          updateDirectoryMappings(mapRoot);
        }
        Notifications.Bus.notify(new Notification(HgVcs.NOTIFICATION_GROUP_ID,
                                                  HgVcsMessages.message(
                                                    succeeded ? "hg4idea.init.created.notification.title" : "hg4idea.init.error.title"),
                                                  HgVcsMessages.message(succeeded
                                                                        ? "hg4idea.init.created.notification.description"
                                                                        : "hg4idea.init.error.description",
                                                                        selectedRoot.getPresentableUrl()),
                                                  succeeded ? NotificationType.INFORMATION : NotificationType.ERROR),
                                 myProject.isDefault() ? null : myProject);
      }
    });
  }
  
}