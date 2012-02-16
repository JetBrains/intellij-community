package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.importing.GradleLocalNodeImportHelper;
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
    final GradleLocalNodeImportHelper importHelper = project.getComponent(GradleLocalNodeImportHelper.class);
    importHelper.importNodes(nodes);
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
