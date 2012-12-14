/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootPresenceChange;
import org.jetbrains.plugins.gradle.diff.dependency.*;
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange;
import org.jetbrains.plugins.gradle.diff.module.GradleModulePresenceChange;
import org.jetbrains.plugins.gradle.diff.project.GradleLanguageLevelChange;
import org.jetbrains.plugins.gradle.diff.project.GradleProjectRenameChange;

/**
 * Defines common interface for dispatching gradle project structure change objects.
 * 
 * @author Denis Zhdanov
 * @since 11/16/11 8:48 PM
 */
public interface GradleProjectStructureChangeVisitor {
  void visit(@NotNull GradleProjectRenameChange change);
  void visit(@NotNull GradleLanguageLevelChange change);
  void visit(@NotNull GradleModulePresenceChange change);
  void visit(@NotNull GradleContentRootPresenceChange change);
  void visit(@NotNull GradleLibraryDependencyPresenceChange change);
  void visit(@NotNull GradleJarPresenceChange change);
  void visit(@NotNull GradleModuleDependencyPresenceChange change);
  void visit(@NotNull GradleDependencyScopeChange change);
  void visit(@NotNull GradleDependencyExportedChange change);
}
