package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleBundle;

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

  @SuppressWarnings({"MethodMayBeStatic", "unchecked"})
  @NotNull
  public <T extends GradleEntity> GradleProjectStructureNodeDescriptor<T> buildDescriptor(@NotNull T entity) {
    final Ref<GradleProjectStructureNodeDescriptor<GradleEntity>> result = new Ref<GradleProjectStructureNodeDescriptor<GradleEntity>>();
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        result.set(new GradleProjectStructureNodeDescriptor<GradleEntity>(
          project, project.getName(), IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl())
        ));
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        result.set(new GradleProjectStructureNodeDescriptor<GradleEntity>(module, module.getName(), GradleIcons.MODULE_ICON));
      }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
        result.set(new GradleProjectStructureNodeDescriptor<GradleEntity>(
          contentRoot,
          GradleBundle.message("gradle.import.structure.tree.node.content.root"), GradleIcons.CONTENT_ROOT_ICON
        )); 
      }

      @Override
      public void visit(@NotNull GradleLibrary library) {
        result.set(new GradleProjectStructureNodeDescriptor<GradleEntity>(library, library.getName(), GradleIcons.LIB_ICON));
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
    return (GradleProjectStructureNodeDescriptor<T>)result.get();
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
