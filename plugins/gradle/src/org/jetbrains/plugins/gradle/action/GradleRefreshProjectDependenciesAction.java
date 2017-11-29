/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.externalSystem.action.RefreshExternalProjectAction;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 3/1/2017
 */
public class GradleRefreshProjectDependenciesAction extends RefreshExternalProjectAction {

  public GradleRefreshProjectDependenciesAction() {
    getTemplatePresentation().setText("Refresh dependencies");
    getTemplatePresentation().setDescription("Refresh dependencies in the Gradle cache using --refresh-dependencies argument");
  }

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    if (!GradleConstants.SYSTEM_ID.equals(getSystemId(e))) return false;
    return super.isEnabled(e);
  }

  @Override
  protected boolean isVisible(AnActionEvent e) {
    if (!GradleConstants.SYSTEM_ID.equals(getSystemId(e))) return false;
    return super.isVisible(e);
  }

  @Override
  public void perform(@NotNull final Project project,
                      @NotNull ProjectSystemId projectSystemId,
                      @NotNull AbstractExternalEntityData externalEntityData,
                      @NotNull AnActionEvent e) {

    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    final ExternalSystemNode<?> externalSystemNode = ContainerUtil.getFirstItem(selectedNodes);
    assert externalSystemNode != null;

    final ExternalConfigPathAware externalConfigPathAware =
      externalSystemNode.getData() instanceof ExternalConfigPathAware ? (ExternalConfigPathAware)externalSystemNode.getData() : null;
    assert externalConfigPathAware != null;

    // We save all documents because there is a possible case that there is an external system config file changed inside the ide.
    FileDocumentManager.getInstance().saveAllDocuments();

    final ExternalProjectSettings linkedProjectSettings = ExternalSystemApiUtil.getSettings(
      project, projectSystemId).getLinkedProjectSettings(externalConfigPathAware.getLinkedExternalProjectPath());
    final String externalProjectPath = linkedProjectSettings == null
                                       ? externalConfigPathAware.getLinkedExternalProjectPath()
                                       : linkedProjectSettings.getExternalProjectPath();


    ExternalSystemUtil.refreshProject(externalProjectPath,
                                      new ImportSpecBuilder(project, projectSystemId)
                                        .useDefaultCallback()
                                        .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
                                        .withArguments("--refresh-dependencies")
                                        .build());
  }
}