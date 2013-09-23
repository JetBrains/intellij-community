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
package org.jetbrains.plugins.gradle.service.resolve;

import org.jetbrains.annotations.NonNls;

/**
 * @author Vladislav.Soroka
 * @since 9/3/13
 */
public final class GradleCommonClassNames {
  @NonNls public static final String GRADLE_API_SCRIPT = "org.gradle.api.Script";
  @NonNls public static final String GRADLE_API_PROJECT = "org.gradle.api.Project";
  @NonNls public static final String GRADLE_API_CONFIGURATION_CONTAINER = "org.gradle.api.artifacts.ConfigurationContainer";
  @NonNls public static final String GRADLE_API_CONFIGURATION = "org.gradle.api.artifacts.Configuration";
  @NonNls public static final String GRADLE_API_ARTIFACT_HANDLER = "org.gradle.api.artifacts.dsl.ArtifactHandler";
  @NonNls public static final String GRADLE_API_DEPENDENCY_HANDLER = "org.gradle.api.artifacts.dsl.DependencyHandler";
  @NonNls public static final String GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY = "org.gradle.api.artifacts.ExternalModuleDependency";
  @NonNls public static final String GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY = "org.gradle.api.artifacts.ModuleDependency";
  @NonNls public static final String GRADLE_API_ARTIFACTS_DEPENDENCY_ARTIFACT = "org.gradle.api.artifacts.DependencyArtifact";
  @NonNls public static final String GRADLE_API_REPOSITORY_HANDLER = "org.gradle.api.artifacts.dsl.RepositoryHandler";
  @NonNls public static final String GRADLE_API_SOURCE_DIRECTORY_SET = "org.gradle.api.file.SourceDirectorySet";
  @NonNls public static final String GRADLE_API_SOURCE_SET = "org.gradle.api.tasks.SourceSet";
  @NonNls public static final String GRADLE_API_SOURCE_SET_CONTAINER = "org.gradle.api.tasks.SourceSetContainer";
  @NonNls public static final String GRADLE_API_SCRIPT_HANDLER = "org.gradle.api.initialization.dsl.ScriptHandler";
  @NonNls public static final String GRADLE_API_TASK = "org.gradle.api.Task";
  @NonNls public static final String GRADLE_API_TASK_CONTAINER = "org.gradle.api.tasks.TaskContainer";

  private GradleCommonClassNames() {
  }
}
