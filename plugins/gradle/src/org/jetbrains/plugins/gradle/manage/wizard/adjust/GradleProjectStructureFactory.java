package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleUtil;

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
  public <T extends ExternalEntity> ProjectStructureNodeDescriptor<ProjectEntityId> buildDescriptor(@NotNull T entity) {
    final Ref<String> text = new Ref<String>();
    entity.invite(new ExternalEntityVisitor() {
      @Override
      public void visit(@NotNull ExternalProject project) {
        text.set(project.getName());
      }

      @Override
      public void visit(@NotNull ExternalModule module) {
        text.set(module.getName());
      }

      @Override
      public void visit(@NotNull ExternalContentRoot contentRoot) {
        text.set(ExternalSystemBundle.message("gradle.import.structure.tree.node.content.root"));
      }

      @Override
      public void visit(@NotNull ExternalLibrary library) {
        text.set(library.getName());
      }

      @Override
      public void visit(@NotNull Jar jar) {
        text.set(GradleUtil.extractNameFromPath(jar.getPath()));
      }

      @Override
      public void visit(@NotNull ExternalModuleDependency dependency) {
        visit(dependency.getTarget());
      }

      @Override
      public void visit(@NotNull ExternalLibraryDependency dependency) {
        visit(dependency.getTarget());
      }

      @Override
      public void visit(@NotNull ExternalCompositeLibraryDependency dependency) {
        assert false; // We don't expect outdated library during importing project.
      }
    });
    return GradleUtil.buildDescriptor(EntityIdMapper.mapEntityToId(entity), text.get());
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public GradleProjectStructureNodeSettings buildSettings(@NotNull ExternalEntity entity,
                                                          @NotNull final DefaultTreeModel treeModel,
                                                          @NotNull final Collection<ProjectStructureNode> treeNodes)
  {
    final Ref<GradleProjectStructureNodeSettings> result = new Ref<GradleProjectStructureNodeSettings>();
    entity.invite(new ExternalEntityVisitor() {
      @Override
      public void visit(@NotNull ExternalProject project) {
        setupController(project, treeModel, treeNodes);
        result.set(new GradleProjectSettings(project));
      }

      @Override
      public void visit(@NotNull ExternalModule module) {
        setupController(module, treeModel, treeNodes);
        result.set(new GradleModuleSettings(module)); 
      }

      @Override
      public void visit(@NotNull ExternalContentRoot contentRoot) {
        result.set(new GradleContentRootSettings(contentRoot));
      }

      @Override
      public void visit(@NotNull ExternalLibrary library) {
        result.set(new GradleLibrarySettings()); 
      }

      @Override
      public void visit(@NotNull Jar jar) {
        result.set(new GradleJarSettings(jar)); 
      }

      @Override
      public void visit(@NotNull ExternalModuleDependency dependency) {
        setupController(dependency, treeModel, treeNodes);
        result.set(new GradleModuleDependencySettings(dependency));
      }

      @Override
      public void visit(@NotNull ExternalLibraryDependency dependency) {
        setupController(dependency, treeModel, treeNodes);
        result.set(new GradleLibraryDependencySettings(dependency));
      }

      @Override
      public void visit(@NotNull ExternalCompositeLibraryDependency dependency) {
        assert false; // We don't expect outdated library during importing project. 
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
  private static void setupController(@NotNull final ExternalEntity entity, @NotNull final DefaultTreeModel model,
                                       @NotNull final Collection<ProjectStructureNode> treeNodes)
  {
    
    entity.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (!Named.NAME_PROPERTY.equals(evt.getPropertyName())) {
          return;
        }
        for (ProjectStructureNode node : treeNodes) {
          node.getDescriptor().setName(evt.getNewValue().toString());
          model.nodeChanged(node);
        }
      }
    });
  }
}
