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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectCoordinate;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemWorkspaceContributor;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Vladislav.Soroka
 */
public class GradleWorkspaceContributor implements ExternalSystemWorkspaceContributor {

  @Nullable
  @Override
  public ProjectCoordinate findProjectId(Module module) {
    if (!ExternalSystemApiUtil.isExternalSystemAwareModule(GradleConstants.SYSTEM_ID, module)) {
      return null;
    }
    DataNode<? extends ModuleData> moduleData = CachedModuleDataFinder.getInstance(module.getProject()).findModuleData(module);
    return moduleData != null ? moduleData.getData().getPublication() : null;
  }
}
