package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.importing.GradleModuleDependencyImporter;
import org.jetbrains.plugins.gradle.importing.GradleModuleImporter;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependencyId;
import org.jetbrains.plugins.gradle.model.GradleModule;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Imports target {@link GradleTextAttributes#GRADLE_LOCAL_CHANGE 'gradle local'} entity to the current intellij project.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 10:32 AM
 */
public class GradleImportEntityAction extends AnAction {
  
  private static final Logger LOG = Logger.getInstance("#" + GradleImportEntityAction.class.getName());

  public GradleImportEntityAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.import.entity.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.import.entity.description"));
  }

  @Override
  public void update(AnActionEvent e) {
    final Collection<GradleProjectStructureNode<?>> nodes = getInterestedNodes(e.getDataContext());
    e.getPresentation().setEnabled(!nodes.isEmpty());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      LOG.warn("Can't import gradle-local entities. Reason: target intellij project is undefined");
      return;
    }
    
    final Collection<GradleProjectStructureNode<?>> nodes = getInterestedNodes(e.getDataContext());
    
    // We need to import not only the selected nodes but their gradle-local parents as well.
    List<GradleProjectStructureNode<?>> nodesToImport = new ArrayList<GradleProjectStructureNode<?>>();
    for (GradleProjectStructureNode<?> node : nodes) {
      collectHierarchyToImport(node, nodesToImport);
    }

    GradleProjectStructureHelper projectStructureHelper = project.getComponent(GradleProjectStructureHelper.class);
    GradleModuleImporter moduleImporter = ServiceManager.getService(GradleModuleImporter.class);
    GradleModuleDependencyImporter dependencyImporter = ServiceManager.getService(GradleModuleDependencyImporter.class);
    
    for (GradleProjectStructureNode<?> node : nodesToImport) {
      switch (node.getType()) {
        case MODULE:
          final GradleModule gradleModule = projectStructureHelper.findGradleModuleByName(node.getDescriptor().getName());
          if (gradleModule != null) {
            moduleImporter.importModule(gradleModule, project);
          }
          break;
        case LIBRARY_DEPENDENCY:
          final Object element = node.getDescriptor().getElement();
          if (!(element instanceof GradleLibraryDependencyId)) {
            break;
          }
          GradleLibraryDependencyId id = (GradleLibraryDependencyId)element;
          final GradleLibraryDependency dependency = projectStructureHelper.findLibraryDependency(id);
          final Module intellijModule = projectStructureHelper.findIntellijModuleByName(id.getModuleName());
          if (dependency != null && intellijModule != null) {
            dependencyImporter.importDependency(dependency, intellijModule);
          }
          break;
        default: // Do nothing
      }
    }
  }
  
  /**
   * When particular gradle-local node is asked to be imported we need to import its gradle-local parent hierarchy as well.
   * <p/>
   * This method allows to collect all parent nodes of the given nodes that should be imported as well.
   * <p/>
   * <b>Note:</b> those nodes are added to the given collection starting from the topmost one.
   * 
   * @param node     target node
   * @param storage  target nodes storage
   */
  private static void collectHierarchyToImport(@Nullable GradleProjectStructureNode<?> node,
                                               @NotNull Collection<GradleProjectStructureNode<?>> storage)
  {
    if (node == null || node.getDescriptor().getAttributes() != GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
      return;
    }
    collectHierarchyToImport(node.getParent(), storage);
    storage.add(node);
  }

  @NotNull
  private static Collection<GradleProjectStructureNode<?>> getInterestedNodes(@NotNull DataContext context) {
    final Collection<GradleProjectStructureNode<?>> selectedNodes = GradleDataKeys.SYNC_TREE_NODE.getData(context);
    if (selectedNodes == null) {
      return Collections.emptyList();
    }
    List<GradleProjectStructureNode<?>> result = new ArrayList<GradleProjectStructureNode<?>>();
    for (GradleProjectStructureNode<?> node : selectedNodes) {
      if (node.getDescriptor().getAttributes() == GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
        result.add(node);
      }
    }
    return result;
  }
}
