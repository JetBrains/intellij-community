package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;

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

  private static final Icon PROJECT_ICON      = IconLoader.getIcon("/nodes/ideaProject.png");
  private static final Icon MODULE_ICON       = IconLoader.getIcon("/nodes/ModuleOpen.png");
  private static final Icon CONTENT_ROOT_ICON = IconLoader.getIcon("/modules/addContentEntry.png");
  private static final Icon LIB_ICON          = IconLoader.getIcon("/nodes/ppLib.png");

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public <T extends GradleEntity> GradleProjectStructureNodeDescriptor buildDescriptor(@NotNull T entity) {
    final Ref<GradleProjectStructureNodeDescriptor> result = new Ref<GradleProjectStructureNodeDescriptor>();
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
        result.set(new GradleProjectStructureNodeDescriptor(
          contentRoot,
          GradleBundle.message("gradle.import.structure.tree.node.content.root"), CONTENT_ROOT_ICON
        )); 
      }

      @Override
      public void visit(@NotNull GradleLibrary library) {
        result.set(new GradleProjectStructureNodeDescriptor(library, library.getName(), LIB_ICON));
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        visit(dependency.getTarget());
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        visit(dependency.getTarget());
      }
    });
    return result.get();
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public GradleProjectStructureNodeSettings buildSettings(@NotNull GradleEntity entity,
                                                          @NotNull final DefaultTreeModel treeModel,
                                                          @NotNull final Collection<GradleProjectStructureNode> treeNodes)
  {
    final Ref<GradleProjectStructureNodeSettings> result = new Ref<GradleProjectStructureNodeSettings>();
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        setupController(project, treeModel, treeNodes);
        result.set(new GradleProjectSettings(project));
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        setupController(module, treeModel, treeNodes);
        result.set(new GradleModuleSettings(module)); 
      }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
        result.set(new GradleContentRootSettings(contentRoot));
      }

      @Override
      public void visit(@NotNull GradleLibrary library) {
        setupController(library, treeModel, treeNodes);
        result.set(new GradleLibrarySettings(library)); 
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        setupController(dependency, treeModel, treeNodes);
        result.set(new GradleModuleDependencySettings(dependency));
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        setupController(dependency, treeModel, treeNodes);
        result.set(new GradleLibraryDependencySettings(dependency));
      }
    });
    return result.get();
  }

  /**
   * Configures controller that delegates entity state change to all corresponding nodes.
   * 
   * @param entity          target entity to wrap
   * @param model           model of the target tree
   * @param treeNodes       tree nodes that represent the given entity
   */
  @SuppressWarnings("unchecked")
  private static void setupController(@NotNull final GradleEntity entity, @NotNull final DefaultTreeModel model,
                                       @NotNull final Collection<GradleProjectStructureNode> treeNodes)
  {
    
    entity.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (!Named.NAME_PROPERTY.equals(evt.getPropertyName())) {
          return;
        }
        for (GradleProjectStructureNode node : treeNodes) {
          node.getDescriptor().setName(evt.getNewValue().toString());
          model.nodeChanged(node);
        }
      }
    });
  }
}
