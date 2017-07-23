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
package org.jetbrains.plugins.gradle.service.project.data;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.data.BuildParticipant;
import org.jetbrains.plugins.gradle.model.data.CompositeBuildData;
import org.jetbrains.plugins.gradle.settings.CompositeDefinitionSource;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 2/17/2017
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class CompositeBuildDataService extends AbstractProjectDataService<CompositeBuildData, Project> {

  @NotNull
  @Override
  public Key<CompositeBuildData> getTargetDataKey() {
    return CompositeBuildData.KEY;
  }

  public void importData(@NotNull final Collection<DataNode<CompositeBuildData>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty()) {
      if (projectData != null) {
        GradleProjectSettings projectSettings =
          GradleSettings.getInstance(project).getLinkedProjectSettings(projectData.getLinkedExternalProjectPath());
        if (projectSettings != null &&
            projectSettings.getCompositeBuild() != null &&
            projectSettings.getCompositeBuild().getCompositeDefinitionSource() == CompositeDefinitionSource.SCRIPT) {
          projectSettings.setCompositeBuild(null);
        }
      }
      return;
    }
    if (toImport.size() != 1) {
      throw new IllegalArgumentException(
        String.format("Expected to get a single composite data node but got %d: %s", toImport.size(), toImport));
    }
    CompositeBuildData compositeBuildData = toImport.iterator().next().getData();
    GradleProjectSettings projectSettings =
      GradleSettings.getInstance(project).getLinkedProjectSettings(compositeBuildData.getRootProjectPath());
    if (projectSettings != null) {
      GradleProjectSettings.CompositeBuild compositeBuild = new GradleProjectSettings.CompositeBuild();
      compositeBuild.setCompositeDefinitionSource(CompositeDefinitionSource.SCRIPT);
      for (BuildParticipant buildParticipant : compositeBuildData.getCompositeParticipants()) {
        compositeBuild.getCompositeParticipants().add(buildParticipant.copy());
      }
      projectSettings.setCompositeBuild(compositeBuild);
    }
  }
}
