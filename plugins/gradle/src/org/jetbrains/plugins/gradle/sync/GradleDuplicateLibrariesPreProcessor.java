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

import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.service.project.change.ExternalProjectStructureChangesPreProcessor;
<<<<<<< HEAD
import com.intellij.openapi.externalSystem.service.project.manage.LibraryDataService;
import com.intellij.openapi.externalSystem.service.project.manage.LibraryDependencyDataService;
=======
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.service.project.manage.LibraryDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.LibraryDependencyDataManager;
import com.intellij.openapi.module.Module;
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * There is a possible situation that there are module-local libraries which reference jars similar to those provided by gradle
 * as project libraries (IDEA-100968). This class manages that by auto-removing module-local libraries and replacing it by 
 * references to project-level libraries instead.
 * 
 * @author Denis Zhdanov
 * @since 2/13/13 9:15 AM
 */
public class GradleDuplicateLibrariesPreProcessor implements ExternalProjectStructureChangesPreProcessor {

<<<<<<< HEAD
  @NotNull private final LibraryDependencyDataService myDependencyManager;
  @NotNull private final LibraryDataService           myLibraryManager;

  public GradleDuplicateLibrariesPreProcessor(@NotNull LibraryDependencyDataService manager, @NotNull LibraryDataService manager1) {
=======
  @NotNull private final LibraryDependencyDataManager myDependencyManager;
  @NotNull private final LibraryDataManager           myLibraryManager;

  public GradleDuplicateLibrariesPreProcessor(@NotNull LibraryDependencyDataManager manager, @NotNull LibraryDataManager manager1) {
>>>>>>> 38a9775... IDEA-104500 Gradle: Allow to reuse common logic for other external systems
    myDependencyManager = manager;
    myLibraryManager = manager1;
  }

  @NotNull
  @Override
  public ProjectData preProcess(@NotNull ProjectData externalProject, @NotNull final Project ideProject) {
    // TODO den implement
//    final ProjectStructureHelper projectStructureHelper = ServiceManager.getService(ideProject, ProjectStructureHelper.class);
//    for (ModuleData gradleModule : externalProject.getModules()) {
//      final Module ideModule = projectStructureHelper.findIdeModule(gradleModule);
//      if (ideModule == null) {
//        continue;
//      }
//      ExternalEntityVisitor visitor = new ExternalEntityVisitorAdapter() {
//        @Override
//        public void visit(@NotNull LibraryDependencyData gradleDependency) {
//          LibraryDependencyId id = EntityIdMapper.mapEntityToId(gradleDependency);
//          LibraryOrderEntry ideDependency = projectStructureHelper.findIdeModuleLocalLibraryDependency(
//            id.getOwnerModuleName(), id.getDependencyName()
//          );
//          if (ideDependency == null) {
//            return;
//          }
//          myDependencyManager.removeDependency(ideDependency, true);
//
//          ideDependency = projectStructureHelper.findIdeLibraryDependency(id);
//          if (ideDependency == null) {
//            myDependencyManager.importDependency(gradleDependency, ideModule, true);
//          }
//
//          LibraryData gradleLibrary = gradleDependency.getTarget();
//          Library ideLibrary = projectStructureHelper.findIdeLibrary(gradleLibrary);
//          if (ideLibrary == null) {
//            myLibraryManager.importLibrary(gradleLibrary, ideProject, true);
//          }
//          else {
//            myLibraryManager.syncPaths(gradleLibrary, ideLibrary, ideProject, true);
//          }
//        }
//      };
//      for (DependencyData dependency : gradleModule.getDependencies()) {
//        dependency.invite(visitor);
//      }
//    }

    return externalProject;
  }
}
