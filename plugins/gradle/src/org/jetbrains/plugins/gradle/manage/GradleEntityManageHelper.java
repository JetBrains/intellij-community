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
import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.intellij.IdeEntityVisitor;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 2/20/13 3:14 PM
 */
public class GradleEntityManageHelper {

  @NotNull private final Project                      myProject;
  @NotNull private final GradleProjectStructureHelper myProjectStructureHelper;
  @NotNull private final GradleModuleManager          myModuleManager;
  @NotNull private final GradleLibraryManager         myLibraryManager;
  @NotNull private final GradleJarManager             myJarManager;
  @NotNull private final GradleDependencyManager      myDependencyManager;
  @NotNull private final GradleContentRootManager     myContentRootManager;

  public GradleEntityManageHelper(@NotNull Project project,
                                  @NotNull GradleProjectStructureHelper helper,
                                  @NotNull GradleModuleManager moduleManager,
                                  @NotNull GradleLibraryManager libraryManager,
                                  @NotNull GradleJarManager jarManager,
                                  @NotNull GradleDependencyManager dependencyManager,
                                  @NotNull GradleContentRootManager contentRootManager)
  {
    myProject = project;
    myProjectStructureHelper = helper;
    myModuleManager = moduleManager;
    myLibraryManager = libraryManager;
    myJarManager = jarManager;
    myDependencyManager = dependencyManager;
    myContentRootManager = contentRootManager;
  }

  public void importEntities(@NotNull Collection<GradleEntity> entities, boolean synchronous) {
    final Set<GradleModule> modules = ContainerUtilRt.newHashSet();
    final Map<GradleModule, Collection<GradleContentRoot>> contentRoots = ContainerUtilRt.newHashMap();
    final Set<GradleLibrary> libraries = ContainerUtilRt.newHashSet();
    final Set<GradleJar> jars = ContainerUtilRt.newHashSet();
    final Map<GradleModule, Collection<GradleDependency>> dependencies = ContainerUtilRt.newHashMap();
    GradleEntityVisitor visitor = new GradleEntityVisitor() {
      @Override public void visit(@NotNull GradleProject project) { }
      @Override public void visit(@NotNull GradleModule module) { modules.add(module); }
      @Override public void visit(@NotNull GradleLibrary library) { libraries.add(library); }
      @Override public void visit(@NotNull GradleJar jar) { jars.add(jar); }
      @Override public void visit(@NotNull GradleModuleDependency dependency) { addDependency(dependency); }
      @Override public void visit(@NotNull GradleLibraryDependency dependency) { addDependency(dependency); }
      @Override public void visit(@NotNull GradleCompositeLibraryDependency dependency) { }
      @Override public void visit(@NotNull GradleContentRoot contentRoot) {
        Collection<GradleContentRoot> roots = contentRoots.get(contentRoot.getOwnerModule());
        if (roots == null) {
          contentRoots.put(contentRoot.getOwnerModule(), roots = ContainerUtilRt.<GradleContentRoot>newHashSet());
        }
        roots.add(contentRoot);
      }
      private void addDependency(@NotNull GradleDependency dependency) {
        Collection<GradleDependency> d = dependencies.get(dependency.getOwnerModule());
        if (d == null) {
          dependencies.put(dependency.getOwnerModule(), d = ContainerUtilRt.<GradleDependency>newHashSet());
        }
        d.add(dependency);
      }
    };
    
    // Sort entities.
    for (GradleEntity entity : entities) {
      entity.invite(visitor);
    }
    myModuleManager.importModules(modules, myProject, false, synchronous);
    for (Map.Entry<GradleModule, Collection<GradleContentRoot>> entry : contentRoots.entrySet()) {
      Module module = myProjectStructureHelper.findIdeModule(entry.getKey());
      if (module != null) {
        myContentRootManager.importContentRoots(entry.getValue(), module, synchronous);
      }
    }
    myLibraryManager.importLibraries(libraries, myProject, synchronous);
    myJarManager.importJars(jars, myProject, synchronous);
    for (Map.Entry<GradleModule, Collection<GradleDependency>> entry : dependencies.entrySet()) {
      Module module = myProjectStructureHelper.findIdeModule(entry.getKey());
      if (module != null) {
        myDependencyManager.importDependencies(entry.getValue(), module, synchronous);
      }
    }
  }
  
  public void removeEntities(@NotNull Collection<Object> entities, boolean synchronous) {
    final List<Module> modules = ContainerUtilRt.newArrayList();
    final List<ModuleAwareContentRoot> contentRoots = ContainerUtilRt.newArrayList();
    final List<ExportableOrderEntry> dependencies = ContainerUtilRt.newArrayList();
    final List<GradleJar> jars = ContainerUtilRt.newArrayList();
    IdeEntityVisitor ideVisitor = new IdeEntityVisitor() {
      @Override public void visit(@NotNull Project project) { }
      @Override public void visit(@NotNull Module module) { modules.add(module); }
      @Override public void visit(@NotNull ModuleAwareContentRoot contentRoot) { contentRoots.add(contentRoot); }
      @Override public void visit(@NotNull LibraryOrderEntry libraryDependency) { dependencies.add(libraryDependency); }
      @Override public void visit(@NotNull ModuleOrderEntry moduleDependency) { dependencies.add(moduleDependency); }
      @Override public void visit(@NotNull Library library) { }
    };
    GradleEntityVisitor gradleVisitor = new GradleEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleJar jar) {
        jars.add(jar);
      }
    };
    for (Object entity : entities) {
      GradleUtil.dispatch(entity, gradleVisitor, ideVisitor);
    }

    myJarManager.removeJars(jars, myProjectStructureHelper.getProject(), synchronous);
    myContentRootManager.removeContentRoots(contentRoots, synchronous);
    myDependencyManager.removeDependencies(dependencies, synchronous);
    myModuleManager.removeModules(modules, synchronous);
  }
}
