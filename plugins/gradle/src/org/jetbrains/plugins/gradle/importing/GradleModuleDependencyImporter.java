package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.PlatformFacade;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.util.GradleLog;

import java.io.File;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 3:23 PM
 */
public class GradleModuleDependencyImporter {

  private static final Map<LibraryPathType, OrderRootType> LIBRARY_ROOT_MAPPINGS
    = new EnumMap<LibraryPathType, OrderRootType>(LibraryPathType.class);
  static {
    LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.BINARY, OrderRootType.CLASSES);
    LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.SOURCE, OrderRootType.SOURCES);
    LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.DOC, JavadocOrderRootType.getInstance());
    assert LibraryPathType.values().length == LIBRARY_ROOT_MAPPINGS.size();
  }
  
  @NotNull private final PlatformFacade myPlatformFacade;

  public GradleModuleDependencyImporter(@NotNull PlatformFacade platformFacade) {
    myPlatformFacade = platformFacade;
  }

  public void importDependency(@NotNull GradleDependency dependency, @NotNull Module module) {
    importDependencies(Collections.singleton(dependency), module);
  }
  
  public void importDependencies(@NotNull Iterable<GradleDependency> dependencies, @NotNull Module module) {
    final List<GradleModuleDependency> moduleDependencies = new ArrayList<GradleModuleDependency>();
    final List<GradleLibraryDependency> libraryDependencies = new ArrayList<GradleLibraryDependency>();
    GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        moduleDependencies.add(dependency);
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        libraryDependencies.add(dependency);
      }
    };
    for (GradleDependency dependency : dependencies) {
      dependency.invite(visitor);
    }
    importLibraryDependencies(libraryDependencies, module);
    importModuleDependencies(moduleDependencies, module);
  }

  public void importModuleDependencies(@NotNull Iterable<GradleModuleDependency> dependencies, @NotNull Module module) {
    // TODO den implement
  }

  public void importLibraryDependencies(@NotNull final Iterable<GradleLibraryDependency> dependencies, @NotNull final Module module) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        doImportLibraryDependencies(dependencies, module); 
      }
    });
  }
  
  public void doImportLibraryDependencies(@NotNull final Iterable<GradleLibraryDependency> dependencies, @NotNull final Module module) {
    // Is assumed to be called from EDT
    final LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
    final Map<GradleLibrary, Library> gradle2intellij = new HashMap<GradleLibrary, Library>(); 
    final Set<GradleLibrary> librariesToCreate = new HashSet<GradleLibrary>();
    for (final GradleLibraryDependency dependency : dependencies) {
      // Try to find existing library in project libraries.
      Library library = libraryTable.getLibraryByName(dependency.getName());
      if (library == null) {
        librariesToCreate.add(dependency.getTarget());
      }
      else {
        gradle2intellij.put(dependency.getTarget(), library);
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        // Create all necessary libraries.
        if (!librariesToCreate.isEmpty()) {
          final LibraryTable.ModifiableModel projectLibraryModel = libraryTable.getModifiableModel();
          try {
            for (GradleLibrary library : librariesToCreate) {
              final Library intellijLibrary = projectLibraryModel.createLibrary(library.getName());
              gradle2intellij.put(library, intellijLibrary);
              final Library.ModifiableModel libraryModel = intellijLibrary.getModifiableModel();
              try {
                registerPaths(library, libraryModel);
              }
              finally {
                libraryModel.commit();
              }
            }
          }
          finally {
            projectLibraryModel.commit();
          }
        }
        
        // Register library dependencies.
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
        try {
          for (GradleLibraryDependency dependency : dependencies) {
            LibraryOrderEntry orderEntry = moduleRootModel.addLibraryEntry(gradle2intellij.get(dependency.getTarget()));
            orderEntry.setExported(dependency.isExported());
            orderEntry.setScope(dependency.getScope());
          }
        }
        finally {
          moduleRootModel.commit();
        }
      }
    });
  }

  private static void registerPaths(@NotNull GradleLibrary gradleLibrary, @NotNull Library.ModifiableModel model) {
    for (LibraryPathType pathType : LibraryPathType.values()) {
      for (String path : gradleLibrary.getPaths(pathType)) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
        if (virtualFile == null) {
          GradleLog.LOG.warn(String.format("Can't find %s of the library '%s' at path '%s'", pathType, gradleLibrary.getName(), path));
          continue;
        }
        if (virtualFile.isDirectory()) {
          model.addRoot(virtualFile, LIBRARY_ROOT_MAPPINGS.get(pathType));
        }
        else {
          VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
          if (jarRoot == null) {
            GradleLog.LOG.warn(String.format(
              "Can't parse contents of the jar file at path '%s' for the library '%s''", path, gradleLibrary.getName()
            ));
            continue;
          }
          model.addRoot(jarRoot, LIBRARY_ROOT_MAPPINGS.get(pathType));
        }
      }
    }
  }

}
