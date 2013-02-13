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
package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.model.gradle.GradleCompositeLibraryDependency;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 1/23/13 11:35 AM
 */
public class GradleOutdatedLibraryManager {

  @NotNull private final GradleLibraryManager          myLibraryManager;
  @NotNull private final GradleDependencyManager       myDependencyManager;
  @NotNull private final GradleProjectStructureContext myContext;
  @NotNull private final Project                       myProject;

  public GradleOutdatedLibraryManager(@NotNull GradleLibraryManager libraryManager,
                                      @NotNull GradleDependencyManager dependencyManager,
                                      @NotNull GradleProjectStructureContext context,
                                      @NotNull Project project)
  {
    myLibraryManager = libraryManager;
    myDependencyManager = dependencyManager;
    myContext = context;
    myProject = project;
  }

  public void sync(Collection<GradleProjectStructureNode<?>> nodes) {
    List<Pair<GradleLibraryDependency, Module>> libraryDependenciesToImport = ContainerUtilRt.newArrayList();
    Map<String /* ide library name */, Library> ideLibsToRemove = ContainerUtilRt.newHashMap();
    Map<String /* ide library name */, GradleLibrary> ide2gradleLibs = ContainerUtilRt.newHashMap();
    Collection<LibraryOrderEntry> ideLibraryDependenciesToRemove = ContainerUtilRt.newArrayList();
    GradleProjectStructureHelper projectStructureHelper = myContext.getProjectStructureHelper();
    PlatformFacade facade = myContext.getPlatformFacade();

    // The general idea is to remove all ide-local entities references by the 'outdated libraries' and import
    // all corresponding gradle-local entities.

    //region Parse information to use for further processing.
    for (GradleProjectStructureNode<?> node : nodes) {
      Object entity = node.getDescriptor().getElement().mapToEntity(myContext);
      if (!(entity instanceof GradleCompositeLibraryDependency)) {
        continue;
      }
      GradleCompositeLibraryDependency e = (GradleCompositeLibraryDependency)entity;
      String ideLibraryName = e.getIdeEntity().getLibraryName();
      Library ideLibraryToRemove = null;
      if (ideLibraryName != null) {
        
        ideLibraryToRemove = projectStructureHelper.findIdeLibrary(ideLibraryName);
      }

      if (ideLibraryToRemove != null) {
        // We use map here because Library.hashCode()/equals() contract is not clear. That's why we consider two currently
        // configured libraries with the same name to be the same.
        ideLibsToRemove.put(ideLibraryName, ideLibraryToRemove);
        ide2gradleLibs.put(ideLibraryName, e.getGradleEntity().getTarget());
      }
    }
    //endregion

    //region Do actual sync
    RootPolicy<LibraryOrderEntry> visitor = new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, LibraryOrderEntry value) {
        return libraryOrderEntry;
      }
    };
    for (Module ideModule : facade.getModules(myProject)) {
      GradleModule gradleModule = projectStructureHelper.findGradleModule(ideModule.getName());
      if (gradleModule == null) {
        continue;
      }
      
      for (OrderEntry entry : facade.getOrderEntries(ideModule)) {
        LibraryOrderEntry ideLibraryDependency = entry.accept(visitor, null);
        if (ideLibraryDependency == null) {
          continue;
        }
        String libraryName = ideLibraryDependency.getLibraryName();
        if (libraryName == null) {
          continue;
        }
        if (!ideLibsToRemove.containsKey(libraryName)) {
          continue;
        }
        ideLibraryDependenciesToRemove.add(ideLibraryDependency);
        GradleLibraryDependency gradleLibraryDependency = new GradleLibraryDependency(gradleModule, ide2gradleLibs.get(libraryName));
        gradleLibraryDependency.setExported(ideLibraryDependency.isExported());
        gradleLibraryDependency.setScope(ideLibraryDependency.getScope());
        libraryDependenciesToImport.add(Pair.create(gradleLibraryDependency, ideModule));
      }
    }
    myDependencyManager.removeDependencies(ideLibraryDependenciesToRemove, false);
    myLibraryManager.removeLibraries(ideLibsToRemove.values(), myProject);
    for (Pair<GradleLibraryDependency, Module> pair : libraryDependenciesToImport) {
      // Assuming that dependency manager is smart enough to import library for a given library dependency if it hasn't been
      // imported yet.
      myDependencyManager.importDependency(pair.first, pair.second, false);
    }
    //endregion
  }
}
