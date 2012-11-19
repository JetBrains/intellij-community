package org.jetbrains.plugins.gradle.manage;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 3:23 PM
 */
public class GradleDependencyImporter {
  
  @NotNull private final PlatformFacade myPlatformFacade;

  public GradleDependencyImporter(@NotNull PlatformFacade platformFacade) {
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
            final ModuleOrderEntry orderEntry = moduleRootModel.addModuleOrderEntry(intellijModuleDependency);
            orderEntry.setScope(dependency.getScope());
            orderEntry.setExported(dependency.isExported());
          }
        }
        finally {
          moduleRootModel.commit();
        }
      }
    });
  }

  public void importLibraryDependencies(@NotNull final Iterable<GradleLibraryDependency> dependencies, @NotNull final Module module) {
    final List<LibraryDependencyInfo> infos = new ArrayList<LibraryDependencyInfo>();
    final LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
    for (GradleLibraryDependency dependency : dependencies) {
      final Library library = libraryTable.getLibraryByName(dependency.getName());
      if (library != null) {
        infos.add(new LibraryDependencyInfo(library, dependency.getScope(), dependency.isExported()));
      }
    }
    doImportLibraryDependencies(infos, module);
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void importLibraryDependencies(@NotNull Module module, @NotNull Collection<Library> libraries) {
    List<LibraryDependencyInfo> infos = new ArrayList<LibraryDependencyInfo>();
    for (Library library : libraries) {
      infos.add(new LibraryDependencyInfo(library, DependencyScope.PROVIDED, true));
    }
    doImportLibraryDependencies(infos, module);
  }

  private static void doImportLibraryDependencies(@NotNull final Iterable<LibraryDependencyInfo> infos, @NotNull final Module module) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            // Register library dependencies.
            ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
            final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
            final GradleProjectEntityChangeListener publisher
              = module.getProject().getMessageBus().syncPublisher(GradleProjectEntityChangeListener.TOPIC);
            try {
              for (LibraryDependencyInfo info : infos) {
                publisher.onChangeStart(info.library);
                LibraryOrderEntry orderEntry = moduleRootModel.addLibraryEntry(info.library);
                orderEntry.setExported(info.exported);
                orderEntry.setScope(info.scope);
              }
            }
            finally {
              moduleRootModel.commit();
              for (LibraryDependencyInfo info : infos) {
                publisher.onChangeEnd(info.library);
              }
            }
          }
        }); 
      }
    });
  }

  private static class LibraryDependencyInfo {
    
    @NotNull public final Library         library;
    @NotNull public final DependencyScope scope;
    public final          boolean         exported;

    LibraryDependencyInfo(@NotNull Library library, @NotNull DependencyScope scope, boolean exported) {
      this.library = library;
      this.scope = scope;
      this.exported = exported;
    }
  }
}
