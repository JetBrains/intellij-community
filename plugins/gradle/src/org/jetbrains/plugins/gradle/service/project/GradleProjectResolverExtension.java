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
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.ParametersEnhancer;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemExecutionSettings;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.GradleManager;

/**
 * Allows to enhance {@link GradleProjectResolver} processing.
 * <p/>
 * Every extension is expected to have a no-args constructor because they are used at external process and we need a simple way
 * to instantiate it.
 * 
 * @author Denis Zhdanov
 * @since 4/17/13 11:24 AM
 * @see GradleManager#enhanceParameters(SimpleJavaParameters)   sample enhanceParameters() implementation
 */
public interface GradleProjectResolverExtension extends ParametersEnhancer {
  
  ExtensionPointName<GradleProjectResolverExtension> EP_NAME = ExtensionPointName.create("org.jetbrains.plugins.gradle.projectResolve");

  /**
   * Is expected to be called during gradle project
   * {@link ExternalSystemProjectResolver#resolveProjectInfo(ExternalSystemTaskId, String, boolean, ExternalSystemExecutionSettings) parsing}.
   * <p/>
   * The general idea is to allow to store specific data at given project node for {@link ProjectDataService further processing}.
   * 
   * @param project            target project built from the gradle file
   * @param connection         gradle connection for the target gradle file
   * @param quick              flag which indicates whether the processing should be quick (e.g. don't download binary dependencies
   *                           if the flag is <code>true</code>)
   */
  void enhanceProject(@NotNull DataNode<ProjectData> project, @NotNull ProjectConnection connection, boolean quick);
}
