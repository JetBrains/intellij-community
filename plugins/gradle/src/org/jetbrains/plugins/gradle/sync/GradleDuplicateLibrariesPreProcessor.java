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
package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.manage.GradleDependencyManager;
import org.jetbrains.plugins.gradle.manage.GradleLibraryManager;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryDependencyId;

/**
 * There is a possible situation that there are module-local libraries which reference jars similar to those provided by gradle
 * as project libraries (IDEA-100968). This class manages that by auto-removing module-local libraries and replacing it by 
 * references to project-level libraries instead.
 * 
 * @author Denis Zhdanov
 * @since 2/13/13 9:15 AM
 */
public class GradleDuplicateLibrariesPreProcessor implements GradleProjectStructureChangesPreProcessor {

  @NotNull private final GradleDependencyManager myDependencyManager;
  @NotNull private final GradleLibraryManager    myLibraryManager;

  public GradleDuplicateLibrariesPreProcessor(@NotNull GradleDependencyManager manager, @NotNull GradleLibraryManager manager1) {
    myDependencyManager = manager;
    myLibraryManager = manager1;
  }

  @NotNull
  @Override
  public GradleProject preProcess(@NotNull GradleProject gradleProject, @NotNull final Project ideProject) {
    final GradleProjectStructureHelper projectStructureHelper = ServiceManager.getService(ideProject, GradleProjectStructureHelper.class);
    for (GradleModule gradleModule : gradleProject.getModules()) {
      final Module ideModule = projectStructureHelper.findIdeModule(gradleModule);
      if (ideModule == null) {
        continue;
      }
      GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
        @Override
        public void visit(@NotNull GradleLibraryDependency gradleDependency) {
          GradleLibraryDependencyId id = GradleEntityIdMapper.mapEntityToId(gradleDependency);
          LibraryOrderEntry ideDependency = projectStructureHelper.findIdeModuleLocalLibraryDependency(
            id.getOwnerModuleName(), id.getDependencyName()
          );
          if (ideDependency == null) {
            return;
          }
          myDependencyManager.removeDependency(ideDependency, true);

          ideDependency = projectStructureHelper.findIdeLibraryDependency(id);
          if (ideDependency == null) {
            myDependencyManager.importDependency(gradleDependency, ideModule, true);
          }

          GradleLibrary gradleLibrary = gradleDependency.getTarget();
          Library ideLibrary = projectStructureHelper.findIdeLibrary(gradleLibrary);
          if (ideLibrary == null) {
            myLibraryManager.importLibrary(gradleLibrary, ideProject, true);
          }
          else {
            myLibraryManager.syncPaths(gradleLibrary, ideLibrary, ideProject, true);
          }
        }
      };
      for (GradleDependency dependency : gradleModule.getDependencies()) {
        dependency.invite(visitor);
      }
    }

    return gradleProject;
  }
}
