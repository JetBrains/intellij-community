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
package com.intellij.openapi.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
@ApiStatus.Experimental
public interface ExternalProjectSystemRegistry {
  static ExternalProjectSystemRegistry getInstance() {
    return ServiceManager.getService(ExternalProjectSystemRegistry.class);
  }

  @NotNull
  ProjectModelExternalSource getSourceById(String id);

  @Nullable
  ProjectModelExternalSource getExternalSource(@NotNull Module module);

  /**
   * These fields are temporary added to API until we have proper extension points for different external systems.
   */
  String MAVEN_EXTERNAL_SOURCE_ID = "Maven";
  String EXTERNAL_SYSTEM_ID_KEY = "external.system.id";
  String IS_MAVEN_MODULE_KEY = "org.jetbrains.idea.maven.project.MavenProjectsManager.isMavenModule";
}
