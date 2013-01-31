package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.manage.GradleLocalNodeManageHelper;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.Collection;

/**
 * Imports target {@link GradleTextAttributes#GRADLE_LOCAL_CHANGE 'gradle local'} entity to the current intellij project.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/7/12 10:32 AM
 */
public class GradleImportEntityAction extends AbstractGradleSyncTreeNodeAction {
  
  public GradleImportEntityAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.import.entity.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.import.entity.description"));
  }

  @Override
  protected void filterNodes(@NotNull Collection<GradleProjectStructureNode<?>> nodes) {
    filterNodesByAttributes(nodes, GradleTextAttributes.GRADLE_LOCAL_CHANGE);
  }

  @Override
  protected void doActionPerformed(@NotNull Collection<GradleProjectStructureNode<?>> nodes, @NotNull Project project, @NotNull Tree tree) {
    final GradleLocalNodeManageHelper helper = ServiceManager.getService(project, GradleLocalNodeManageHelper.class);
    helper.importNodes(nodes); 
  }
}
