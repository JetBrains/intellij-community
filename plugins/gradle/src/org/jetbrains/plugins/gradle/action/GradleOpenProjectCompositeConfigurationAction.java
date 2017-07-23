/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.action.ExternalSystemAction;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.gradle.settings.CompositeDefinitionSource;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.ui.GradleProjectCompositeSelectorDialog;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Vladislav.Soroka
 * @since 5/12/2015
 */
public class GradleOpenProjectCompositeConfigurationAction extends ExternalSystemAction {

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    if (!super.isEnabled(e)) return false;
    if (getSystemId(e) == null) return false;

    return ExternalSystemDataKeys.SELECTED_PROJECT_NODE.getData(e.getDataContext()) != null;
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    final Project project = getProject(e);
    if (project == null) return false;
    ProjectSystemId systemId = getSystemId(e);
    if(!GradleConstants.SYSTEM_ID.equals(systemId)) return false;

    if (GradleSettings.getInstance(project).getLinkedProjectsSettings().size() > 1) {
      final ProjectNode projectNode = ExternalSystemDataKeys.SELECTED_PROJECT_NODE.getData(e.getDataContext());
      if (projectNode == null || projectNode.getData() == null) return false;

      GradleProjectSettings projectSettings =
        GradleSettings.getInstance(project).getLinkedProjectSettings(projectNode.getData().getLinkedExternalProjectPath());
      GradleProjectSettings.CompositeBuild compositeBuild = null;
      if (projectSettings != null) {
        compositeBuild = projectSettings.getCompositeBuild();
      }
      if (compositeBuild == null || compositeBuild.getCompositeDefinitionSource() == CompositeDefinitionSource.IDE) return true;
    }
    return false;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getProject(e);
    if (project == null) return;
    final ProjectNode projectNode = ExternalSystemDataKeys.SELECTED_PROJECT_NODE.getData(e.getDataContext());
    if (projectNode == null || projectNode.getData() == null) return;
    new GradleProjectCompositeSelectorDialog(project, projectNode.getData().getLinkedExternalProjectPath()).showAndGet();
  }
}