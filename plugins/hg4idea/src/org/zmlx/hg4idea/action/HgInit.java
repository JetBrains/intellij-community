package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgInitCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Action for initializing Mercurial repository. Command "hg init".
 * @author Kirill Likhodedov
 */
public class HgInit extends DumbAwareAction {

  public HgInit() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      // TODO: log
      return;
    }

    // 1. provide window to select the root directory (possibly with an option to select project directory
    // TODO: dialog: probably the user wants to put the whole project under git, so the root dir of the project is git repo root. No need to select the folder in that case.
    final FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    fcd.setTitle(HgVcsMessages.message("hg4idea.init.destination.directory.title"));
    fcd.setDescription(HgVcsMessages.message("hg4idea.init.destination.directory.description"));
    fcd.setHideIgnored(false);
    final VirtualFile baseDir = project.getBaseDir();
    final VirtualFile[] files = FileChooser.chooseFiles(project, fcd, baseDir);
    if (files.length == 0) {
      return;
    }
    final VirtualFile root = files[0];

    // 2. check if it is not yet under mercurial
    final HgVcs hgVcs = HgVcs.getInstance(project);
    if (hgVcs.isVersionedDirectory(root)) {
      Messages.showErrorDialog(project, HgVcsMessages.message("hg4idea.init.error.already.under.hg", root.getPresentableUrl()),
                               HgVcsMessages.message("hg4idea.init.error.title"));
      return;
    }


    // 3. Execute hg init
    (new HgInitCommand(project)).execute(root);
    //TODO: handle errors here

    // 4. update vcs directory mappings
    root.refresh(false, false);
    final String path = root.equals(baseDir) ? "" : root.getPath();
    ProjectLevelVcsManager vcs = ProjectLevelVcsManager.getInstance(project);
    final List<VcsDirectoryMapping> vcsDirectoryMappings = new ArrayList<VcsDirectoryMapping>(vcs.getDirectoryMappings());
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
    vcs.setDirectoryMappings(vcsDirectoryMappings);
    vcs.updateActiveVcss();
  }

  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(project != null);
    presentation.setVisible(project != null);
  }
  
}
