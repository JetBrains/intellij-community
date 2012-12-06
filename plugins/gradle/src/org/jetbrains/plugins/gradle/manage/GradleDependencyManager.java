package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.*;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 3:23 PM
 */
public class GradleDependencyManager {

  @NotNull private final PlatformFacade       myPlatformFacade;
  @NotNull private final GradleLibraryManager myLibraryManager;

  public GradleDependencyManager(@NotNull PlatformFacade platformFacade, @NotNull GradleLibraryManager manager) {
    myPlatformFacade = platformFacade;
    myLibraryManager = manager;
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
  public void importModuleDependencies(@NotNull final Collection<GradleModuleDependency> dependencies, @NotNull final Module module) {
    if (dependencies.isEmpty()) {
      return;
    }
    
    GradleUtil.executeProjectChangeAction(module.getProject(), dependencies, new Runnable() {
      @Override
      public void run() {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
        try {
          final GradleProjectStructureHelper projectStructureHelper = module.getProject().getComponent(GradleProjectStructureHelper.class);
          for (GradleModuleDependency dependency : dependencies) {
            final String moduleName = dependency.getName();
            final Module intellijModule = projectStructureHelper.findIntellijModule(moduleName);
            if (intellijModule == null) {
              assert false;
              continue;
            }
            else if (intellijModule.equals(module)) {
              // Gradle api returns recursive module dependencies (a module depends on itself) for 'gradle' project.
              continue;
            }

            ModuleOrderEntry orderEntry = projectStructureHelper.findIntellijModuleDependency(dependency, moduleRootModel);
            if (orderEntry == null) {
              orderEntry = moduleRootModel.addModuleOrderEntry(intellijModule);
            }
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
    GradleUtil.executeProjectChangeAction(module.getProject(), dependencies, new Runnable() {
      @Override
      public void run() {
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
        Set<GradleLibrary> librariesToImport = new HashSet<GradleLibrary>();
        for (GradleLibraryDependency dependency : dependencies) {
          final Library library = libraryTable.getLibraryByName(dependency.getName());
          if (library == null) {
            librariesToImport.add(dependency.getTarget());
          }
        }
        if (!librariesToImport.isEmpty()) {
          myLibraryManager.importLibraries(librariesToImport, module.getProject());
        }

        for (GradleLibraryDependency dependency : dependencies) {
          GradleProjectStructureHelper helper = module.getProject().getComponent(GradleProjectStructureHelper.class);
          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
          try {
            libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
            final Library library = libraryTable.getLibraryByName(dependency.getName());
            if (library == null) {
              assert false;
              continue;
            }
            LibraryOrderEntry orderEntry = helper.findIntellijLibraryDependency(dependency.getName(), moduleRootModel);
            if (orderEntry == null) {
              // We need to get the most up-to-date Library object due to our project model restrictions.
              orderEntry = moduleRootModel.addLibraryEntry(library);
            }
            orderEntry.setExported(dependency.isExported());
            orderEntry.setScope(dependency.getScope());
          }
          finally {
            moduleRootModel.commit();
          }
        }
      }
    });
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void removeDependencies(@NotNull final Collection<ExportableOrderEntry> dependencies) {
    if (dependencies.isEmpty()) {
      return;
    }

    for (final ExportableOrderEntry dependency : dependencies) {
      final Module module = dependency.getOwnerModule();
      GradleUtil.executeProjectChangeAction(module.getProject(), dependency, new Runnable() {
        @Override
        public void run() {
          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
          try {
            // The thing is that intellij created order entry objects every time new modifiable model is created,
            // that's why we can't use target dependency object as is but need to get a reference to the current
            // entry object from the model instead.
            for (OrderEntry entry : moduleRootModel.getOrderEntries()) {
              if (entry.getPresentableName().equals(dependency.getPresentableName())) {
                moduleRootModel.removeOrderEntry(entry);
                break;
              }
            }
          }
          finally {
            moduleRootModel.commit();
          } 
        }
      });
    }
  }
}
