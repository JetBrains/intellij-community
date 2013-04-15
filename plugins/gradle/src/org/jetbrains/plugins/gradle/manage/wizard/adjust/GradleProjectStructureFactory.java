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
  public <T extends ProjectEntityData> ProjectStructureNodeDescriptor<ProjectEntityId> buildDescriptor(@NotNull T entity) {
    final Ref<String> text = new Ref<String>();
    // TODO den implement
//    entity.invite(new ExternalEntityVisitor() {
//      @Override
//      public void visit(@NotNull ProjectData project) {
//        text.set(project.getName());
//      }
//
//      @Override
//      public void visit(@NotNull ModuleData module) {
//        text.set(module.getName());
//      }
//
//      @Override
//      public void visit(@NotNull ContentRootData contentRoot) {
//        text.set(ExternalSystemBundle.message("gradle.import.structure.tree.node.content.root"));
//      }
//
//      @Override
//      public void visit(@NotNull LibraryData library) {
//        text.set(library.getName());
//      }
//
//      @Override
//      public void visit(@NotNull JarData jar) {
//        text.set(GradleUtil.extractNameFromPath(jar.getPath()));
//      }
//
//      @Override
//      public void visit(@NotNull ModuleDependencyData dependency) {
//        visit(dependency.getTarget());
//      }
//
//      @Override
//      public void visit(@NotNull LibraryDependencyData dependency) {
//        visit(dependency.getTarget());
//      }
//
//      @Override
//      public void visit(@NotNull CompositeLibraryDependencyData dependency) {
//        assert false; // We don't expect outdated library during importing project.
//      }
//    });
    return GradleUtil.buildDescriptor(EntityIdMapper.mapEntityToId(entity), text.get());
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public GradleProjectStructureNodeSettings buildSettings(@NotNull ProjectEntityData entity,
                                                          @NotNull final DefaultTreeModel treeModel,
                                                          @NotNull final Collection<ProjectStructureNode> treeNodes)
  {
    final Ref<GradleProjectStructureNodeSettings> result = new Ref<GradleProjectStructureNodeSettings>();
    // TODO den implement
//    entity.invite(new ExternalEntityVisitor() {
//      @Override
//      public void visit(@NotNull ProjectData project) {
//        setupController(project, treeModel, treeNodes);
//        result.set(new GradleProjectSettings(project));
//      }
//
//      @Override
//      public void visit(@NotNull ModuleData module) {
//        setupController(module, treeModel, treeNodes);
//        result.set(new GradleModuleSettings(module)); 
//      }
//
//      @Override
//      public void visit(@NotNull ContentRootData contentRoot) {
//        result.set(new GradleContentRootSettings(contentRoot));
//      }
//
//      @Override
//      public void visit(@NotNull LibraryData library) {
//        result.set(new GradleLibrarySettings()); 
//      }
//
//      @Override
//      public void visit(@NotNull JarData jar) {
//        result.set(new GradleJarSettings(jar)); 
//      }
//
//      @Override
//      public void visit(@NotNull ModuleDependencyData dependency) {
//        setupController(dependency, treeModel, treeNodes);
//        result.set(new GradleModuleDependencySettings(dependency));
//      }
//
//      @Override
//      public void visit(@NotNull LibraryDependencyData dependency) {
//        setupController(dependency, treeModel, treeNodes);
//        result.set(new GradleLibraryDependencySettings(dependency));
//      }
//
//      @Override
//      public void visit(@NotNull CompositeLibraryDependencyData dependency) {
//        assert false; // We don't expect outdated library during importing project. 
//      }
//    });
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
  private static void setupController(@NotNull final ProjectEntityData entity, @NotNull final DefaultTreeModel model,
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
