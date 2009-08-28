package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.actionSystem.Presentation;

import java.util.ArrayList;


public class CommonCheckinProjectAction extends AbstractCommonCheckinAction {

  protected FilePath[] getRoots(final VcsContext context) {
    Project project = context.getProject();
    ArrayList<FilePath> virtualFiles = new ArrayList<FilePath>();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    for(AbstractVcs vcs: vcsManager.getAllActiveVcss()) {
      if (vcs.getCheckinEnvironment() != null) {
        VirtualFile[] roots = vcsManager.getRootsUnderVcs(vcs);
        for (VirtualFile root : roots) {
          virtualFiles.add(new FilePathImpl(root));
        }
      }
    }
    return virtualFiles.toArray(new FilePath[virtualFiles.size()]);
  }

  @Override
  protected boolean approximatelyHasRoots(VcsContext dataContext) {
    Project project = dataContext.getProject();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    return vcsManager.hasAnyMappings();
  }

  protected void update(VcsContext vcsContext, Presentation presentation) {
    Project project = vcsContext.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }
    final ProjectLevelVcsManager plVcsManager = ProjectLevelVcsManager.getInstance(project);
    if (plVcsManager.getAllActiveVcss().length == 0) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    String actionName = getActionName(vcsContext) + "...";
    presentation.setText(actionName);

    presentation.setEnabled(! plVcsManager.isBackgroundVcsOperationRunning());
    presentation.setVisible(true);
  }

  protected String getActionName(VcsContext dataContext) {
    return VcsBundle.message("action.name.commit.project");
  }

  protected boolean filterRootsBeforeAction() {
    return false;
  }
}
