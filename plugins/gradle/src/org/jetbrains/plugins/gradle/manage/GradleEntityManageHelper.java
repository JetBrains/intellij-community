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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.autoimport.*;
import org.jetbrains.plugins.gradle.diff.AbstractGradleConflictingPropertyChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootPresenceChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleDependencyExportedChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleDependencyScopeChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyPresenceChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleModuleDependencyPresenceChange;
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange;
import org.jetbrains.plugins.gradle.diff.library.GradleOutdatedLibraryVersionChange;
import org.jetbrains.plugins.gradle.diff.module.GradleModulePresenceChange;
import org.jetbrains.plugins.gradle.diff.project.GradleLanguageLevelChange;
import org.jetbrains.plugins.gradle.diff.project.GradleProjectRenameChange;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.id.*;
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
  @NotNull private final GradleProjectManager         myProjectManager;
  @NotNull private final GradleModuleManager          myModuleManager;
  @NotNull private final GradleLibraryManager         myLibraryManager;
  @NotNull private final GradleJarManager             myJarManager;
  @NotNull private final GradleDependencyManager      myDependencyManager;
  @NotNull private final GradleContentRootManager     myContentRootManager;

  public GradleEntityManageHelper(@NotNull Project project,
                                  @NotNull GradleProjectStructureHelper helper,
                                  @NotNull GradleProjectManager projectManager,
                                  @NotNull GradleModuleManager moduleManager,
                                  @NotNull GradleLibraryManager libraryManager,
                                  @NotNull GradleJarManager jarManager,
                                  @NotNull GradleDependencyManager dependencyManager,
                                  @NotNull GradleContentRootManager contentRootManager)
  {
    myProject = project;
    myProjectStructureHelper = helper;
    myProjectManager = projectManager;
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
      @Override
      public void visit(@NotNull GradleProject project) { }

      @Override
      public void visit(@NotNull GradleModule module) { modules.add(module); }

      @Override
      public void visit(@NotNull GradleLibrary library) { libraries.add(library); }

      @Override
      public void visit(@NotNull GradleJar jar) { jars.add(jar); }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) { addDependency(dependency); }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) { addDependency(dependency); }

      @Override
      public void visit(@NotNull GradleCompositeLibraryDependency dependency) { }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
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

  /**
   * Tries to eliminate all target changes (namely, all given except those which correspond 'changes to preserve') 
   * 
   * @param changesToEliminate  changes to eliminate
   * @param changesToPreserve   changes to preserve
   * @param synchronous         defines if the processing should be synchronous
   * @return                    non-processed changes
   */
  public Set<GradleProjectStructureChange> eliminateChange(@NotNull Collection<GradleProjectStructureChange> changesToEliminate,
                                                           @NotNull final Set<GradleUserProjectChange> changesToPreserve,
                                                           boolean synchronous)
  {
    EliminateChangesContext context = new EliminateChangesContext(
      myProjectStructureHelper, changesToPreserve, myProjectManager, myDependencyManager, synchronous
    );
    for (GradleProjectStructureChange change : changesToEliminate) {
      change.invite(context.visitor);
    }
    
    removeEntities(context.entitiesToRemove, synchronous);
    importEntities(context.entitiesToImport, synchronous);
    return context.nonProcessedChanges;
  }

  private static void processProjectRenameChange(@NotNull GradleProjectRenameChange change, @NotNull EliminateChangesContext context) {
    context.projectManager.renameProject(change.getGradleValue(), context.projectStructureHelper.getProject(), context.synchronous);
  }

  // Don't auto-apply language level change because we can't correctly process language level change manually made
  // by a user - there is crazy processing related to project reloading after language level change and there is just
  // no normal way to inject there.
  
//  private static void processLanguageLevelChange(@NotNull GradleLanguageLevelChange change, @NotNull EliminateChangesContext context) {
//    context.projectManager.setLanguageLevel(change.getGradleValue(), context.projectStructureHelper.getProject(), context.synchronous);
//  }
  
  private static void processModulePresenceChange(@NotNull GradleModulePresenceChange change, @NotNull EliminateChangesContext context) {
    GradleModuleId id = change.getGradleEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      Module module = context.projectStructureHelper.findIdeModule(id.getModuleName());
      if (module != null && !context.changesToPreserve.contains(new GradleAddModuleUserChange(id.getModuleName()))) {
        context.entitiesToRemove.add(module);
        return;
      }
    }
    else {
      GradleModule module = context.projectStructureHelper.findGradleModule(id.getModuleName());
      if (module != null && !context.changesToPreserve.contains(new GradleRemoveModuleUserChange(id.getModuleName()))) {
        context.entitiesToImport.add(module);
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }

  private static void processContentRootPresenceChange(@NotNull GradleContentRootPresenceChange change,
                                                       @NotNull EliminateChangesContext context)
  {
    GradleContentRootId id = change.getGradleEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      ModuleAwareContentRoot root = context.projectStructureHelper.findIdeContentRoot(id);
      if (root != null) {
        context.entitiesToRemove.add(root);
        return;
      }
    }
    else {
      GradleContentRoot root = context.projectStructureHelper.findGradleContentRoot(id);
      if (root != null) {
        context.entitiesToImport.add(root);
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }
  
  private static void processLibraryDependencyPresenceChange(@NotNull GradleLibraryDependencyPresenceChange change,
                                                             @NotNull EliminateChangesContext context)
  {
    GradleLibraryDependencyId id = change.getGradleEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      LibraryOrderEntry dependency = context.projectStructureHelper.findIdeLibraryDependency(id);
      GradleAddLibraryDependencyUserChange c = new GradleAddLibraryDependencyUserChange(id.getOwnerModuleName(), id.getDependencyName());
      if (dependency != null && !context.changesToPreserve.contains(c)) {
        context.entitiesToRemove.add(dependency);
        return;
      }
    }
    else {
      GradleLibraryDependency dependency = context.projectStructureHelper.findGradleLibraryDependency(id);
      GradleRemoveLibraryDependencyUserChange c
        = new GradleRemoveLibraryDependencyUserChange(id.getOwnerModuleName(), id.getDependencyName());
      if (dependency != null && !context.changesToPreserve.contains(c)) {
        context.entitiesToImport.add(dependency);
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }
  
  private static void processJarPresenceChange(@NotNull GradleJarPresenceChange change, @NotNull EliminateChangesContext context) {
    GradleJarId id = change.getGradleEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      GradleJar jar = context.projectStructureHelper.findIdeJar(id);
      if (jar != null) {
        context.entitiesToRemove.add(jar);
        return;
      }
    }
    else {
      GradleLibrary library = context.projectStructureHelper.findGradleLibrary(id.getLibraryId());
      if (library != null) {
        context.entitiesToImport.add(new GradleJar(id.getPath(), id.getLibraryPathType(), null, library));
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }

  private static void processModuleDependencyPresenceChange(@NotNull GradleModuleDependencyPresenceChange change,
                                                            @NotNull EliminateChangesContext context)
  {
    GradleModuleDependencyId id = change.getGradleEntity();
    if (id == null) {
      // IDE-local change.
      id = change.getIdeEntity();
      assert id != null;
      ModuleOrderEntry dependency = context.projectStructureHelper.findIdeModuleDependency(id);
      GradleAddModuleDependencyUserChange c = new GradleAddModuleDependencyUserChange(id.getOwnerModuleName(), id.getDependencyName());
      if (dependency != null && !context.changesToPreserve.contains(c)) {
        context.entitiesToRemove.add(dependency);
        return;
      }
    }
    else {
      GradleModuleDependency dependency = context.projectStructureHelper.findGradleModuleDependency(id);
      GradleRemoveModuleDependencyUserChange c
        = new GradleRemoveModuleDependencyUserChange(id.getOwnerModuleName(), id.getDependencyName());
      if (dependency != null && !context.changesToPreserve.contains(c)) {
        context.entitiesToImport.add(dependency);
        return;
      }
    }
    context.nonProcessedChanges.add(change);
  }
  
  private static void processDependencyScopeChange(@NotNull GradleDependencyScopeChange change, @NotNull EliminateChangesContext context) {
    ExportableOrderEntry dependency = findDependency(change, context);
    if (dependency == null) {
      return;
    }
    AbstractGradleDependencyId id = change.getEntityId();
    GradleUserProjectChange<?> userChange;
    if (dependency instanceof LibraryOrderEntry) {
      userChange = new GradleLibraryDependencyScopeUserChange(id.getOwnerModuleName(), id.getDependencyName(), change.getIdeValue());
    }
    else {
      userChange = new GradleModuleDependencyScopeUserChange(id.getOwnerModuleName(), id.getDependencyName(), change.getIdeValue());
    }
    if (context.changesToPreserve.contains(userChange)) {
      context.nonProcessedChanges.add(change);
    }
    else {
      context.dependencyManager.setScope(change.getGradleValue(), dependency, context.synchronous);
    }
  }

  private static void processDependencyExportedStatusChange(@NotNull GradleDependencyExportedChange change,
                                                            @NotNull EliminateChangesContext context)
  {
    ExportableOrderEntry dependency = findDependency(change, context);
    if (dependency == null) {
      return;
    }
    AbstractGradleDependencyId id = change.getEntityId();
    GradleUserProjectChange<?> userChange;
    if (dependency instanceof LibraryOrderEntry) {
      userChange = new GradleLibraryDependencyExportedChange(id.getOwnerModuleName(), id.getDependencyName(), change.getIdeValue());
    }
    else {
      userChange = new GradleModuleDependencyExportedChange(id.getOwnerModuleName(), id.getDependencyName(), change.getIdeValue());
    }
    if (context.changesToPreserve.contains(userChange)) {
      context.nonProcessedChanges.add(change);
    }
    else {
      context.dependencyManager.setExported(change.getGradleValue(), dependency, context.synchronous);
    }
  }

  @Nullable
  private static ExportableOrderEntry findDependency(@NotNull AbstractGradleConflictingPropertyChange<?> change,
                                                     @NotNull EliminateChangesContext context)
  {
    GradleEntityId id = change.getEntityId();
    ExportableOrderEntry dependency = null;
    if (id instanceof GradleLibraryDependencyId) {
      dependency = context.projectStructureHelper.findIdeLibraryDependency((GradleLibraryDependencyId)id);
    }
    else if (id instanceof GradleModuleDependencyId) {
      dependency = context.projectStructureHelper.findIdeModuleDependency((GradleModuleDependencyId)id);
    }
    else {
      context.nonProcessedChanges.add(change);
    }
    return dependency;
  }
  
  private static class EliminateChangesContext {
    @NotNull final Set<Object>                       entitiesToRemove    = ContainerUtilRt.newHashSet();
    @NotNull final Set<GradleEntity>                 entitiesToImport    = ContainerUtilRt.newHashSet();
    @NotNull final Set<GradleProjectStructureChange> nonProcessedChanges = ContainerUtilRt.newHashSet();
    @NotNull final Set<GradleUserProjectChange>      changesToPreserve   = ContainerUtilRt.newHashSet();

    @NotNull final GradleProjectManager    projectManager;
    @NotNull final GradleDependencyManager dependencyManager;
    final          boolean                 synchronous;

    @NotNull GradleProjectStructureChangeVisitor visitor = new GradleProjectStructureChangeVisitor() {
      @Override
      public void visit(@NotNull GradleProjectRenameChange change) {
        processProjectRenameChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull GradleLanguageLevelChange change) {
//        processLanguageLevelChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull GradleModulePresenceChange change) {
        processModulePresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull GradleContentRootPresenceChange change) {
        processContentRootPresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
        processLibraryDependencyPresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull GradleJarPresenceChange change) {
        processJarPresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull GradleOutdatedLibraryVersionChange change) {
      }

      @Override
      public void visit(@NotNull GradleModuleDependencyPresenceChange change) {
        processModuleDependencyPresenceChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull GradleDependencyScopeChange change) {
        processDependencyScopeChange(change, EliminateChangesContext.this);
      }

      @Override
      public void visit(@NotNull GradleDependencyExportedChange change) {
        processDependencyExportedStatusChange(change, EliminateChangesContext.this);
      }
    };

    @NotNull final GradleProjectStructureHelper projectStructureHelper;

    EliminateChangesContext(@NotNull GradleProjectStructureHelper projectStructureHelper,
                            @NotNull Set<GradleUserProjectChange> changesToPreserve,
                            @NotNull GradleProjectManager projectManager,
                            @NotNull GradleDependencyManager dependencyManager,
                            boolean synchronous)
    {
      this.projectStructureHelper = projectStructureHelper;
      this.changesToPreserve.addAll(changesToPreserve);
      this.projectManager = projectManager;
      this.dependencyManager = dependencyManager;
      this.synchronous = synchronous;
    }
  }
}
