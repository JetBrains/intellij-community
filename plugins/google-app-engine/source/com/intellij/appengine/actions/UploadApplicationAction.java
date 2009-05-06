package com.intellij.appengine.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.CommonBundle;

import java.util.List;

/**
 * @author nik
 */
public class UploadApplicationAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    if (project != null) {
      final List<? extends AppEngineFacet> facets = AppEngineUtil.collectAllFacets(project);
      if (facets.isEmpty()) {
        Messages.showErrorDialog(project, "No App Engine facets found in the project", CommonBundle.getErrorTitle());
        return;
      }
      AppEngineFacet facet;
      if (facets.size() == 1) {
        facet = facets.get(0);
      }
      else {
        final UploadApplicationDialog dialog = new UploadApplicationDialog(project);
        dialog.show();
        facet = dialog.getSelectedFacet();
        if (!dialog.isOK() || facet == null) {
          return;
        }
      }
      final AppEngineUploader uploader = new AppEngineUploader(project);
      uploader.startUploading(facet);
    }
  }


}
