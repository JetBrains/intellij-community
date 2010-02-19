package com.intellij.appengine.actions;

import com.intellij.CommonBundle;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.artifacts.Artifact;

import java.util.List;

/**
 * @author nik
 */
public class UploadApplicationAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setVisible(project != null && !ProjectFacetManager.getInstance(project).getFacets(AppEngineFacet.ID).isEmpty());
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      final List<Artifact> artifacts = AppEngineUtil.collectWebArtifacts(project, true);
      if (artifacts.isEmpty()) {
        Messages.showErrorDialog(project, "No Web artifacts with AppEngine found in the project", CommonBundle.getErrorTitle());
        return;
      }
      Artifact artifact;
      if (artifacts.size() == 1) {
        artifact = artifacts.get(0);
      }
      else {
        final UploadApplicationDialog dialog = new UploadApplicationDialog(project);
        dialog.show();
        artifact = dialog.getSelectedArtifact();
        if (!dialog.isOK() || artifact == null) {
          return;
        }
      }
      final AppEngineUploader uploader = new AppEngineUploader(project);
      uploader.startUploading(artifact);
    }
  }


}
