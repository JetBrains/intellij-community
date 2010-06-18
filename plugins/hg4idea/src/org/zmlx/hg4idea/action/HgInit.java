package org.zmlx.hg4idea.action;

import com.intellij.ide.impl.NewProjectUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.VirtualFile;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgInitCommand;
import org.zmlx.hg4idea.ui.HgInitParentDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Action for initializing Mercurial repository. Command "hg init".
 * @author Kirill Likhodedov
 */
public class HgInit extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(HgInit.class.getName());

  public HgInit() {
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) {
      LOG.warn("[actionPerformed] project is null");
      return;
    }

    // provide window to select the root directory (TODO: an option to select the whole project)
    final FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false) {
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        if (HgUtil.isHgRoot(files[0])) {
          throw new ConfigurationException(HgVcsMessages.message("hg4idea.init.this.is.hg.root", files[0].getPresentableUrl()));
        }
      }
    };
    fcd.setTitle(HgVcsMessages.message("hg4idea.init.destination.directory.title"));
    fcd.setDescription(HgVcsMessages.message("hg4idea.init.destination.directory.description"));
    fcd.setHideIgnored(false);
    final VirtualFile projectDir = project.getBaseDir();
    final VirtualFile[] files = FileChooser.chooseFiles(project, fcd, projectDir);
    if (files.length == 0) {
      return;
    }
    final VirtualFile selectedRoot = files[0];

    // check if it is not yet under mercurial
    final VirtualFile vcsRoot = HgUtil.getNearestHgRoot(selectedRoot);
    VirtualFile mapRoot = selectedRoot;
    if (vcsRoot != null) {
      final HgInitParentDialog dialog = new HgInitParentDialog(project,
                                                   selectedRoot.getPresentableUrl(), vcsRoot.getPresentableUrl());
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }

      if (dialog.getAnswer() == HgInitParentDialog.Answer.CREATE_PROJECT_AT_PARENT) {
        NewProjectUtil.createNewProject(project, vcsRoot.getPath());
        return;
      } else if (dialog.getAnswer() == HgInitParentDialog.Answer.USE_PARENT_REPO_BUT_THIS_PROJECT) {
        mapRoot = vcsRoot;
      } else {
        (new HgInitCommand(project)).execute(selectedRoot);
      }
    }

    // update vcs directory mappings
    mapRoot.refresh(false, false);
    final String path = mapRoot.equals(projectDir) ? "" : mapRoot.getPath();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
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
  
}
