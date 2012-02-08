package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.PlatformFacade;
import org.jetbrains.plugins.gradle.model.*;

/**
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

  /**
   * Allows to answer if target library dependency is still available at the target project.
   *
   * @param id       target library id
   * @return         <code>true</code> if target library dependency is still available at the target project;
   *                 <code>false</code> otherwise
   */
  public boolean isIntellijLibraryDependencyExist(@NotNull final GradleLibraryDependencyId id) {
    final Module module = findIntellijModuleByName(id.getModuleName());
    if (module == null) {
      return false;
    }
    
    RootPolicy<Boolean> visitor = new RootPolicy<Boolean>() {
      @Override
      public Boolean visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Boolean value) {
        return id.getLibraryName().equals(libraryOrderEntry.getLibraryName());
      }
    };
    for (OrderEntry entry : myFacade.getOrderEntries(module)) {
      if (entry.accept(visitor, false)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Allows to answer if target library dependency is still available at the target project.
   *
   * @param id             target library id
   * @return         <code>true</code> if target library dependency is still available at the target project;
   *                 <code>false</code> otherwise
   */
  public boolean isGradleLibraryDependencyExist(@NotNull final GradleLibraryDependencyId id) {
    return findLibraryDependency(id) != null;
  }

  @Nullable
  public Module findIntellijModuleByName(@NotNull String name) {
    for (Module module : myFacade.getModules(myProject)) {
      if (name.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }
  
  @Nullable
  public GradleModule findGradleModuleByName(@NotNull String name) {
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
  public GradleLibraryDependency findLibraryDependency(@NotNull final GradleLibraryDependencyId id) {
    final GradleModule module = findGradleModuleByName(id.getModuleName());
    if (module == null) {
      return null;
    }
    final Ref<GradleLibraryDependency> ref = new Ref<GradleLibraryDependency>();
    GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        if (id.getLibraryName().equals(dependency.getName())) {
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
}
