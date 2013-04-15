package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.settings.ExternalSystemTextAttributes;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.manage.GradleLocalNodeManageHelper;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;

import java.util.Collection;

/**
 * Allows to remove IDE entity (module, dependency, content root etc). Convenient to use when project structure tree is configured
 * to show only IDE-local changes and we want to remove them all.
 * 
 * @author Denis Zhdanov
 * @since 11/17/12 3:01 PM
 */
public class GradleRemoveIdeEntityAction extends AbstractGradleSyncTreeNodeAction {

  public GradleRemoveIdeEntityAction() {
    // TODO den implement
//    getTemplatePresentation().setText(ExternalSystemBundle.message("gradle.action.remove.entity.text"));
//    getTemplatePresentation().setDescription(ExternalSystemBundle.message("gradle.action.remove.entity.description"));
  }

  @Override
  protected void filterNodes(@NotNull Collection<ProjectStructureNode<?>> nodes) {
    filterNodesByAttributes(nodes, ExternalSystemTextAttributes.IDE_LOCAL_CHANGE);
  }
  
  @Override
  protected void doActionPerformed(@NotNull Collection<ProjectStructureNode<?>> nodes, @NotNull Project project, @NotNull Tree tree) {
    final GradleLocalNodeManageHelper helper = ServiceManager.getService(project, GradleLocalNodeManageHelper.class);
    // TODO den implement
//    helper.removeNodes(nodes);
  }
}
