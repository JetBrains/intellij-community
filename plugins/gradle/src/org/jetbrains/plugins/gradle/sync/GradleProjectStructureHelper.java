package org.jetbrains.plugins.gradle.sync;

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
import org.jetbrains.plugins.gradle.model.id.*;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.util.GradleArtifactInfo;
import org.jetbrains.plugins.gradle.util.GradleLibraryPathTypeMapper;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/6/12 3:28 PM
 */
public class GradleProjectStructureHelper {

  @NotNull private final GradleProjectStructureChangesModel myModel;
  @NotNull private final PlatformFacade                     myFacade;
  @NotNull private final GradleLibraryPathTypeMapper        myLibraryPathTypeMapper;
  @NotNull private final Project                            myProject;

  public GradleProjectStructureHelper(@NotNull Project project,
                                      @NotNull GradleProjectStructureChangesModel model,
                                      @NotNull PlatformFacade facade,
                                      @NotNull GradleLibraryPathTypeMapper mapper)
  {
    myProject = project;
    myModel = model;
    myFacade = facade;
    myLibraryPathTypeMapper = mapper;
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
  public boolean isIdeLibraryDependencyExist(@NotNull final GradleLibraryDependencyId id) {
    return findIdeLibraryDependency(id.getOwnerModuleName(), id.getDependencyName()) != null;
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

  public boolean isIdeModuleDependencyExist(@NotNull final GradleModuleDependencyId id) {
    return findIdeModuleDependency(id.getOwnerModuleName(), id.getDependencyName()) != null;
  }
  
  public boolean isGradleModuleDependencyExist(@NotNull final GradleModuleDependencyId id) {
    return findIdeModuleDependency(id.getOwnerModuleName(), id.getDependencyName()) != null;
  }
  
  @Nullable
  public Module findIdeModule(@NotNull GradleModule module) {
    return findIdeModule(module.getName());
  }
  
  @Nullable
  public Module findIdeModule(@NotNull String ideModuleName) {
    for (Module module : myFacade.getModules(myProject)) {
      if (ideModuleName.equals(module.getName())) {
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
  public ModuleAwareContentRoot findIdeContentRoot(@NotNull GradleContentRootId id) {
    final Module module = findIdeModule(id.getModuleName());
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
  public Library findIdeLibrary(@NotNull final GradleLibrary library) {
    return findIdeLibrary(library.getName());
  }

  /**
   * Gradle library names follow the following pattern: {@code '[base library name]-[library-version]'}.
   * <p/>
   * This methods serves as an utility which tries to find a library by it's given base name.
   * 
   * @param baseName  base name of the target library
   * @return          target library for the given base name if there is one and only one library for it;
   *                  null otherwise (if there are no libraries or more than one library for the given base name) 
   */
  @Nullable
  public Library findIdeLibraryByBaseName(@NotNull String baseName) {
    final LibraryTable libraryTable = myFacade.getProjectLibraryTable(myProject);
    Library result = null;
    for (Library library : libraryTable.getLibraries()) {
      GradleArtifactInfo info = GradleUtil.parseArtifactInfo(GradleUtil.getLibraryName(library));
      if (info == null || !baseName.equals(info.getName())) {
        continue;
      }
      if (result != null) {
        return null;
      }
      result = library;
    }
    return result;
  }
  
  @Nullable
  public Library findIdeLibrary(@NotNull String libraryName) {
    final LibraryTable libraryTable = myFacade.getProjectLibraryTable(myProject);
    for (Library ideLibrary : libraryTable.getLibraries()) {
      if (libraryName.equals(GradleUtil.getLibraryName(ideLibrary))) {
        return ideLibrary;
      }
    }
    return null;
  }

  @Nullable
  public Library findIdeLibrary(@NotNull String libraryName, @NotNull OrderRootType jarType, @NotNull String jarPath) {
    Library library = findIdeLibrary(libraryName);
    if (library == null) {
      return null;
    }
    for (VirtualFile file : library.getFiles(jarType)) {
      if (jarPath.equals(GradleUtil.getLocalFileSystemPath(file))) {
        return library;
      }
    }
    return null;
  }

  @Nullable
  public LibraryOrderEntry findIdeLibraryDependency(@NotNull GradleLibraryDependencyId id) {
    return findIdeLibraryDependency(id.getOwnerModuleName(), id.getLibraryId().getLibraryName());
  }
  
  @Nullable
  public LibraryOrderEntry findIdeLibraryDependency(@NotNull final String moduleName, @NotNull final String libraryName) {
    final Module ideModule = findIdeModule(moduleName);
    if (ideModule == null) {
      return null;
    }
    RootPolicy<LibraryOrderEntry> visitor = new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry ideDependency, LibraryOrderEntry value) {
        if (libraryName.equals(ideDependency.getLibraryName())) {
          return ideDependency;
        }
        return value;
      }
    };
    for (OrderEntry entry : myFacade.getOrderEntries(ideModule)) {
      final LibraryOrderEntry result = entry.accept(visitor, null);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public LibraryOrderEntry findIdeLibraryDependency(@NotNull final String libraryName,
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
  public GradleLibrary findGradleLibrary(@NotNull final GradleLibraryId id) {
    return findGradleLibrary(id.getLibraryName());
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
  public GradleLibrary findGradleLibrary(@NotNull String libraryName, @NotNull LibraryPathType jarType, @NotNull String jarPath) {
    GradleLibrary library = findGradleLibrary(libraryName);
    if (library == null) {
      return null;
    }
    return library.getPaths(jarType).contains(jarPath) ? library : null;
  }
  
  @Nullable
  public GradleLibraryDependency findGradleLibraryDependency(@NotNull GradleLibraryDependencyId id) {
    return findGradleLibraryDependency(id.getOwnerModuleName(), id.getLibraryId().getLibraryName());
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
  public ModuleOrderEntry findIdeModuleDependency(@NotNull final GradleModuleDependency gradleDependency) {
    return findIdeModuleDependency(gradleDependency.getOwnerModule().getName(), gradleDependency.getTarget().getName());
  }
  
  @Nullable
  public ModuleOrderEntry findIdeModuleDependency(@NotNull final String ownerModuleName, @NotNull final String dependencyModuleName) {
    final Module ideOwnerModule = findIdeModule(ownerModuleName);
    if (ideOwnerModule == null) {
      return null;
    }

    RootPolicy<ModuleOrderEntry> visitor = new RootPolicy<ModuleOrderEntry>() {
      @Override
      public ModuleOrderEntry visitModuleOrderEntry(ModuleOrderEntry ideDependency, ModuleOrderEntry value) {
        if (dependencyModuleName.equals(ideDependency.getModuleName())) {
          return ideDependency;
        }
        return value;
      }
    };
    for (OrderEntry orderEntry : myFacade.getOrderEntries(ideOwnerModule)) {
      final ModuleOrderEntry result = orderEntry.accept(visitor, null);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Nullable
  public ModuleOrderEntry findIdeModuleDependency(@NotNull GradleModuleDependency dependency, @NotNull ModifiableRootModel model) {
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

  @Nullable
  public GradleJar findIdeJar(@NotNull GradleJarId jarId) {
    Library library = findIdeLibrary(jarId.getLibraryId().getLibraryName());
    if (library == null) {
      return null;
    }
    for (VirtualFile file : library.getFiles(myLibraryPathTypeMapper.map(jarId.getLibraryPathType()))) {
      if (jarId.getPath().equals(GradleUtil.getLocalFileSystemPath(file))) {
        return new GradleJar(jarId.getPath(), jarId.getLibraryPathType(), library, null);
      }
    }
    return null;
  }
}
