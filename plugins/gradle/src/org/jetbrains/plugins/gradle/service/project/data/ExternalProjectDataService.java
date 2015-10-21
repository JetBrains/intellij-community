/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 7/17/2014
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ExternalProjectDataService extends AbstractProjectDataService<ExternalProject, Project> {
  private static final Logger LOG = Logger.getInstance(ExternalProjectDataService.class);

  @NotNull public static final Key<ExternalProject> KEY = Key.create(ExternalProject.class, ProjectKeys.TASK.getProcessingWeight() + 1);

  @NotNull
  @Override
  public Key<ExternalProject> getTargetDataKey() {
    return KEY;
  }

  public void importData(@NotNull final Collection<DataNode<ExternalProject>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if(toImport.isEmpty()) return;
    if (toImport.size() != 1) {
      throw new IllegalArgumentException(
        String.format("Expected to get a single external project but got %d: %s", toImport.size(), toImport));
    }
    ExternalProjectDataCache.getInstance(project).saveExternalProject(toImport.iterator().next().getData());
  }
}
