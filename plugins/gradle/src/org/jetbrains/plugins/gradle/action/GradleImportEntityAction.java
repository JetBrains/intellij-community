package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.importing.GradleLocalNodeImportHelper;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

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
    for (Iterator<GradleProjectStructureNode<?>> iterator = nodes.iterator(); iterator.hasNext(); ) {
      GradleProjectStructureNode<?> node = iterator.next();
      if (node.getDescriptor().getAttributes() != GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
        iterator.remove();
      }
    }
  }

  @Override
  protected void doActionPerformed(@NotNull Collection<GradleProjectStructureNode<?>> nodes, @NotNull Project project) {
    final GradleLocalNodeImportHelper importHelper = project.getComponent(GradleLocalNodeImportHelper.class);
    final List<GradleProjectStructureNode<?>> interestedNodes = new ArrayList<GradleProjectStructureNode<?>>();
    for (GradleProjectStructureNode<?> node : nodes) {
      if (node.getDescriptor().getAttributes() == GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
        interestedNodes.add(node);
      }
    }
    importHelper.importNodes(interestedNodes); 
  }
}
