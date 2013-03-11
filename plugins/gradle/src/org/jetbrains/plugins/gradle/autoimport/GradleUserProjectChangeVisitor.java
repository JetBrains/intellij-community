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
package org.jetbrains.plugins.gradle.autoimport;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 2/19/13 9:16 AM
 */
public interface GradleUserProjectChangeVisitor {

  void visit(@NotNull GradleAddModuleUserChange change);
  void visit(@NotNull GradleRemoveModuleUserChange change);
  void visit(@NotNull GradleAddModuleDependencyUserChange change);
  void visit(@NotNull GradleRemoveModuleDependencyUserChange change);
  void visit(@NotNull GradleAddLibraryDependencyUserChange change);
  void visit(@NotNull GradleRemoveLibraryDependencyUserChange change);
  void visit(@NotNull GradleLibraryDependencyScopeUserChange change);
  void visit(@NotNull GradleModuleDependencyScopeUserChange change);
  void visit(@NotNull GradleLibraryDependencyExportedChange change);
  void visit(@NotNull GradleModuleDependencyExportedChange change);
}
