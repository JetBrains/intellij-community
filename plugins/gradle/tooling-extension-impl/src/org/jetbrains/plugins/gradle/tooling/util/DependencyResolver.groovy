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


package org.jetbrains.plugins.gradle.tooling.util

import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.model.ExternalDependency

/**
 * @author Vladislav.Soroka
 */
interface DependencyResolver {
  String COMPILE_SCOPE = "COMPILE"
  String RUNTIME_SCOPE = "RUNTIME"
  String PROVIDED_SCOPE = "PROVIDED"

  Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName)

  Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration)

  Collection<ExternalDependency> resolveDependencies(@NotNull SourceSet sourceSet)
}