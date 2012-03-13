package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.util.GradleLog;

import java.util.*;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 3:23 PM
 */
public class GradleModuleDependencyImporter {
  
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

  @SuppressWarnings("MethodMayBeStatic")
  public void importModuleDependencies(@NotNull final Iterable<GradleModuleDependency> dependencies, @NotNull final Module module) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        doImportModuleDependencies(dependencies, module);
      }
    });
  }
  
  private static void doImportModuleDependencies(@NotNull final Iterable<GradleModuleDependency> dependencies,
                                                 @NotNull final Module module)
  {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
        try {
          final GradleProjectStructureHelper projectStructureHelper = module.getProject().getComponent(GradleProjectStructureHelper.class);
          for (GradleModuleDependency dependency : dependencies) {
            final String moduleName = dependency.getName();
            final Module intellijModuleDependency = projectStructureHelper.findIntellijModule(moduleName);
            if (intellijModuleDependency == null) {
              assert false;
              continue;
            }
            moduleRootModel.addModuleOrderEntry(intellijModuleDependency);
          }
        }
        finally {
          moduleRootModel.commit();
        }
      }
    });
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
    for (final GradleLibraryDependency dependency : dependencies) {
      // Try to find existing library in project libraries.
      Library library = libraryTable.getLibraryByName(dependency.getName());
      if (library != null) {
        gradle2intellij.put(dependency.getTarget(), library);
      }
      else {
        GradleLog.LOG.warn(String.format(
          "Detected situation when target library for the gradle-local library dependency doesn't exist. Dependency: %s",
          dependency
        ));
      }
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        // Register library dependencies.
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
        final GradleProjectEntityImportListener publisher
          = module.getProject().getMessageBus().syncPublisher(GradleProjectEntityImportListener.TOPIC);
        try {
          for (GradleLibraryDependency dependency : dependencies) {
            final Library library = gradle2intellij.get(dependency.getTarget());
            if (library == null) {
              continue;
            }
            publisher.onImportStart(library);
            LibraryOrderEntry orderEntry = moduleRootModel.addLibraryEntry(library);
            orderEntry.setExported(dependency.isExported());
            orderEntry.setScope(dependency.getScope());
          }
        }
        finally {
          moduleRootModel.commit();
          for (GradleLibraryDependency dependency : dependencies) {
            if (dependency != null) {
              publisher.onImportEnd(dependency);
            }
          }
        }
      }
    });
  }
}
