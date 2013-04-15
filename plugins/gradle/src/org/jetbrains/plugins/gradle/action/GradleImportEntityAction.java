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
 * Imports target {@link ExternalSystemTextAttributes#EXTERNAL_SYSTEM_LOCAL_CHANGE 'gradle local'} entity to the current intellij project.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 10:32 AM
 */
public class GradleImportEntityAction extends AbstractGradleSyncTreeNodeAction {
  
  public GradleImportEntityAction() {
    // TODO den implement
//    getTemplatePresentation().setText(ExternalSystemBundle.message("gradle.action.import.entity.text"));
//    getTemplatePresentation().setDescription(ExternalSystemBundle.message("gradle.action.import.entity.description"));
  }

  @Override
  protected void filterNodes(@NotNull Collection<ProjectStructureNode<?>> nodes) {
    filterNodesByAttributes(nodes, ExternalSystemTextAttributes.EXTERNAL_SYSTEM_LOCAL_CHANGE);
  }

  @Override
  protected void doActionPerformed(@NotNull Collection<ProjectStructureNode<?>> nodes, @NotNull Project project, @NotNull Tree tree) {
    final GradleLocalNodeManageHelper helper = ServiceManager.getService(project, GradleLocalNodeManageHelper.class);
    // TODO den implement
//    helper.importNodes(nodes); 
  }
}
