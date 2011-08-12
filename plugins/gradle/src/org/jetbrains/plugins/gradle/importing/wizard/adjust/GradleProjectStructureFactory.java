package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.*;

import javax.swing.*;

/**
 * Allows to build various entities related to the 'project structure' view elements.
 * <p/>
 * Thread-safe.
 * <p/>
 * This class is not singleton but offers single-point-of-usage field - {@link #INSTANCE}.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:52 PM
 */
public class GradleProjectStructureFactory {

  /** Shared instance of the current (stateless) class. */
  public static final GradleProjectStructureFactory INSTANCE = new GradleProjectStructureFactory();

  private static final Icon PROJECT_ICON = IconLoader.getIcon("/nodes/ideaProject.png");
  private static final Icon MODULE_ICON  = IconLoader.getIcon("/nodes/ModuleOpen.png");
  private static final Icon LIB_ICON     = IconLoader.getIcon("/nodes/ppLib.png");

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public <T extends GradleEntity> NodeDescriptor<GradleEntity> buildDescriptor(@NotNull T entity) {
    final Ref<NodeDescriptor<GradleEntity>> result = new Ref<NodeDescriptor<GradleEntity>>();
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        result.set(new GradleProjectStructureNodeDescriptor(project, project.getName(), PROJECT_ICON));
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        result.set(new GradleProjectStructureNodeDescriptor(module, module.getName(), MODULE_ICON));
      }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
        // TODO den implement 
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        result.set(new GradleProjectStructureNodeDescriptor(dependency, dependency.getModule().getName(), MODULE_ICON));
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        result.set(new GradleProjectStructureNodeDescriptor(dependency, dependency.getName(), LIB_ICON));
      }
    });
    return result.get();
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public GradleProjectStructureNodeSettings buildSettings(@NotNull GradleEntity entity) {
    // TODO den remove
    final GradleProjectStructureNodeSettings toRemove = new GradleProjectStructureNodeSettings() {
      @Override
      public boolean commit() {
        return true;
      }

      @NotNull
      @Override
      public JComponent getComponent() {
        return new JLabel("xxxxxxxxx" + this);
      }
    };
    final Ref<GradleProjectStructureNodeSettings> result = new Ref<GradleProjectStructureNodeSettings>();
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        result.set(new GradleProjectSettings(project));
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        result.set(new GradleModuleSettings(module)); 
      }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
        // TODO den implement
        result.set(toRemove);
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        // TODO den implement
        result.set(toRemove);
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        // TODO den implement
        result.set(toRemove);
      }
    });
    return result.get();
  }
}
