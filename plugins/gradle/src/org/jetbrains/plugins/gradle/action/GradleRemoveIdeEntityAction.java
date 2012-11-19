package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.manage.GradleLocalNodeManageHelper;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.Collection;
import java.util.Iterator;

/**
 * Allows to remove IDE entity (module, dependency, content root etc). Convenient to use when project structure tree is configured
 * to show only IDE-local changes and we want to remove them all.
 * 
 * @author Denis Zhdanov
 * @since 11/17/12 3:01 PM
 */
public class GradleRemoveIdeEntityAction extends AbstractGradleSyncTreeNodeAction {

  public GradleRemoveIdeEntityAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.remove.entity.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.remove.entity.description"));
  }

  @Override
  protected void filterNodes(@NotNull Collection<GradleProjectStructureNode<?>> nodes) {
    for (Iterator<GradleProjectStructureNode<?>> iterator = nodes.iterator(); iterator.hasNext(); ) {
      GradleProjectStructureNode<?> node = iterator.next();
      GradleProjectStructureNodeDescriptor<? extends GradleEntityId> descriptor = node.getDescriptor();
      if (descriptor.getAttributes() == GradleTextAttributes.GRADLE_LOCAL_CHANGE
          || descriptor.getElement().getType() == GradleEntityType.SYNTHETIC
          || node.getParent() == null /* is root, i.e. is a project*/)
      {
        iterator.remove();
      }
    }
  }
  
  @Override
  protected void doActionPerformed(@NotNull Collection<GradleProjectStructureNode<?>> nodes, @NotNull Project project, @NotNull Tree tree) {
    final GradleLocalNodeManageHelper helper = project.getComponent(GradleLocalNodeManageHelper.class);
    helper.removeNodes(nodes);
  }
}
