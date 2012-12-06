package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.id.GradleContentRootId;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryDependencyId;
import org.jetbrains.plugins.gradle.model.id.GradleModuleDependencyId;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/6/12 3:28 PM
 */
public class GradleProjectStructureHelper extends AbstractProjectComponent {

  private final GradleProjectStructureChangesModel myModel;
  private final PlatformFacade                     myFacade;

  public GradleProjectStructureHelper(@NotNull Project project,
                                      @NotNull GradleProjectStructureChangesModel model,
                                      @NotNull PlatformFacade facade)
  {
    super(project);
    myModel = model;
    myFacade = facade;
  }
  
  @NotNull
  public Project getProject() {
    return myProject;
  }
  
  /**
   * Allows to answer if target library dependency is still available at the target project.
   *
   * @param id       target library id
   * @return         <code>true</code> if target library dependency is still available at the target project;
   *                 <code>false</code> otherwise
   */
  public boolean isIntellijLibraryDependencyExist(@NotNull final GradleLibraryDependencyId id) {
    return findIntellijLibraryDependency(id.getOwnerModuleName(), id.getDependencyName()) != null;
  }

  /**
   * Allows to answer if target library dependency is still available at the target gradle project.
   *
   * @param id       target library id
   * @return         <code>true</code> if target library dependency is still available at the target project;
   *                 <code>false</code> otherwise
   */
  public boolean isGradleLibraryDependencyExist(@NotNull final GradleLibraryDependencyId id) {
    return findGradleLibraryDependency(id.getOwnerModuleName(), id.getDependencyName()) != null;
  }

  public boolean isIntellijModuleDependencyExist(@NotNull final GradleModuleDependencyId id) {
    return findIntellijModuleDependency(id.getOwnerModuleName(), id.getDependencyName()) != null;
  }
  
  public boolean isGradleModuleDependencyExist(@NotNull final GradleModuleDependencyId id) {
    return findIntellijModuleDependency(id.getOwnerModuleName(), id.getDependencyName()) != null;
  }
  
  @Nullable
  public Module findIntellijModule(@NotNull GradleModule module) {
    // TODO den consider 'merged modules' at the registered project structure changes here.
    return findIntellijModule(module.getName());
  }
  
  @Nullable
  public Module findIntellijModule(@NotNull String intellijModuleName) {
    for (Module module : myFacade.getModules(myProject)) {
      if (intellijModuleName.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }
  
  @Nullable
  public GradleModule findGradleModule(@NotNull String name) {
    final GradleProject project = myModel.getGradleProject();
    if (project == null) {
      return null;
    }
    for (GradleModule module : project.getModules()) {
      if (name.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }

  @Nullable
  public GradleContentRoot findGradleContentRoot(@NotNull GradleContentRootId id) {
    final GradleModule module = findGradleModule(id.getModuleName());
    if (module == null) {
      return null;
    }
    for (GradleContentRoot root : module.getContentRoots()) {
      if (id.getRootPath().equals(root.getRootPath())) {
        return root;
      }
    }
    return null;
  }
  
  @Nullable
  public ModuleAwareContentRoot findIntellijContentRoot(@NotNull GradleContentRootId id) {
    final Module module = findIntellijModule(id.getModuleName());
    if (module == null) {
      return null;
    }
    for (ModuleAwareContentRoot contentRoot : myFacade.getContentRoots(module)) {
      final VirtualFile file = contentRoot.getFile();
      if (id.getRootPath().equals(file.getPath())) {
        return contentRoot;
      }
    }
    return null;
  }
  
  @Nullable
  public Library findIntellijLibrary(@NotNull final GradleLibrary library) {
    return findIntellijLibrary(library.getName());
  }
  
  @Nullable
  public Library findIntellijLibrary(@NotNull String libraryName) {
    final LibraryTable libraryTable = myFacade.getProjectLibraryTable(myProject);
    for (Library intellijLibrary : libraryTable.getLibraries()) {
      // TODO den consider 'merged libraries' at the registered project structure changes here.
      if (libraryName.equals(GradleUtil.getLibraryName(intellijLibrary))) {
        return intellijLibrary;
      }
    }
    return null;
  }
  
  @Nullable
  public LibraryOrderEntry findIntellijLibraryDependency(@NotNull final String moduleName, @NotNull final String libraryName) {
    final Module intellijModule = findIntellijModule(moduleName);
    if (intellijModule == null) {
      return null;
    }
    RootPolicy<LibraryOrderEntry> visitor = new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry intellijDependency, LibraryOrderEntry value) {
        // TODO den consider 'merged libraries' at the registered project structure changes here.
        if (libraryName.equals(intellijDependency.getLibraryName())) {
          return intellijDependency;
        }
        return value;
      }
    };
    for (OrderEntry entry : myFacade.getOrderEntries(intellijModule)) {
      final LibraryOrderEntry result = entry.accept(visitor, null);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public LibraryOrderEntry findIntellijLibraryDependency(@NotNull final String libraryName,
                                                         @NotNull ModifiableRootModel model)
  {
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry candidate = (LibraryOrderEntry)entry;
        if (libraryName.equals(candidate.getLibraryName())) {
          return candidate;
        }
      }
    }
    return null;
  }

  @Nullable
  public GradleLibrary findGradleLibrary(@NotNull final String libraryName) {
    final GradleProject project = myModel.getGradleProject();
    if (project == null) {
      return null;
    }
    for (GradleLibrary library : project.getLibraries()) {
      if (libraryName.equals(library.getName())) {
        return library;
      }
    }
    return null;
  }
  
  @Nullable
  public GradleLibraryDependency findGradleLibraryDependency(@NotNull final String moduleName, @NotNull final String libraryName) {
    final GradleModule module = findGradleModule(moduleName);
    if (module == null) {
      return null;
    }
    final Ref<GradleLibraryDependency> ref = new Ref<GradleLibraryDependency>();
    GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        if (libraryName.equals(dependency.getName())) {
          ref.set(dependency);
        }
      }
    };
    for (GradleDependency dependency : module.getDependencies()) {
      dependency.invite(visitor);
      final GradleLibraryDependency result = ref.get();
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public GradleModuleDependency findGradleModuleDependency(@NotNull final String ownerModuleName,
                                                           @NotNull final String dependencyModuleName)
  {
    final GradleModule ownerModule = findGradleModule(ownerModuleName);
    if (ownerModule == null) {
      return null;
    }
    final Ref<GradleModuleDependency> ref = new Ref<GradleModuleDependency>();
    GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        if (dependencyModuleName.equals(dependency.getName())) {
          ref.set(dependency);
        }
      }
    };
    for (GradleDependency dependency : ownerModule.getDependencies()) {
      dependency.invite(visitor);
    }
    return ref.get();
  }
  
  @Nullable
  public ModuleOrderEntry findIntellijModuleDependency(@NotNull final GradleModuleDependency gradleDependency) {
    return findIntellijModuleDependency(gradleDependency.getOwnerModule().getName(), gradleDependency.getTarget().getName());
  }
  
  @Nullable
  public ModuleOrderEntry findIntellijModuleDependency(@NotNull final String ownerModuleName, @NotNull final String dependencyModuleName) {
    final Module intellijOwnerModule = findIntellijModule(ownerModuleName);
    if (intellijOwnerModule == null) {
      return null;
    }

    RootPolicy<ModuleOrderEntry> visitor = new RootPolicy<ModuleOrderEntry>() {
      @Override
      public ModuleOrderEntry visitModuleOrderEntry(ModuleOrderEntry intellijDependency, ModuleOrderEntry value) {
        // TODO den consider 'merged modules' at the registered project structure changes here.
        if (dependencyModuleName.equals(intellijDependency.getModuleName())) {
          return intellijDependency;
        }
        return value;
      }
    };
    for (OrderEntry orderEntry : myFacade.getOrderEntries(intellijOwnerModule)) {
      final ModuleOrderEntry result = orderEntry.accept(visitor, null);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public ModuleOrderEntry findIntellijModuleDependency(@NotNull GradleModuleDependency dependency, @NotNull ModifiableRootModel model) {
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry candidate = (ModuleOrderEntry)entry;
        if (dependency.getName().equals(candidate.getModuleName())) {
          return candidate;
        }
      }
    }
    return null;
  }
}
