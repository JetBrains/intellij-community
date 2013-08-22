/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.actions;

import com.intellij.CommonBundle;
import com.intellij.appengine.facet.AppEngineFacet;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class UploadApplicationAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getProject();
    e.getPresentation().setVisible(project != null && !ProjectFacetManager.getInstance(project).getFacets(AppEngineFacet.ID).isEmpty());
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
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
      final AppEngineUploader uploader = AppEngineUploader.createUploader(project, artifact, null, new ServerRuntimeInstance.DeploymentOperationCallback() {
        @Override
        public void succeeded(@NotNull DeploymentRuntime deployment) {

        }

        @Override
        public void errorOccurred(@NotNull String errorMessage) {
          Messages.showErrorDialog(project, errorMessage, CommonBundle.getErrorTitle());
        }
      }, null);
      if (uploader != null) {
        uploader.startUploading();
      }
    }
  }


}
