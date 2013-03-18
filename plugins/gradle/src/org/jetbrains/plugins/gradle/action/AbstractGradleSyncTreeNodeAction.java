package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskManager;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskType;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Common super class for actions that are invoked on 'sync project structures' tree nodes.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/29/12 4:15 PM
 */
public abstract class AbstractGradleSyncTreeNodeAction extends AnAction {
  
  private static final Map<String, Helper> HELPERS = new HashMap<String, Helper>();
  static {
    HELPERS.put(GradleConstants.SYNC_TREE_CONTEXT_MENU_PLACE, new ContextMenuHelper());
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public void update(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final Helper helper = HELPERS.get(e.getPlace());
    if (project == null || helper == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
      return;
    }
    Collection<GradleProjectStructureNode<?>> nodes = helper.getTargetNodes(e);
    if (nodes != null) {
      filterNodes(nodes);
    }
    helper.updatePresentation(nodes, e.getPresentation());
    if (e.getPresentation().isEnabled()) {
      final GradleTaskManager taskManager = ServiceManager.getService(GradleTaskManager.class);
      if (taskManager != null && taskManager.hasTaskOfTypeInProgress(GradleTaskType.RESOLVE_PROJECT)) {
        e.getPresentation().setEnabled(false);
      }
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final Helper helper = HELPERS.get(e.getPlace());
    if (project == null || helper == null) {
      return;
    }
    final Collection<GradleProjectStructureNode<?>> nodes = helper.getTargetNodes(e);
    if (nodes != null) {
      filterNodes(nodes);
    }
    if (nodes == null || nodes.isEmpty()) {
      return;
    }
    final Tree tree = GradleDataKeys.SYNC_TREE.getData(e.getDataContext());
    if (tree != null) {
      doActionPerformed(nodes, project, tree);
    }
  }

  protected abstract void doActionPerformed(@NotNull Collection<GradleProjectStructureNode<?>> nodes,
                                            @NotNull Project project,
                                            @NotNull Tree tree);
  
  protected void filterNodes(@NotNull Collection<GradleProjectStructureNode<?>> nodes) {
  }

  protected static void filterNodesByAttributes(@NotNull Collection<GradleProjectStructureNode<?>> nodes, @NotNull TextAttributesKey key) {
    for (Iterator<GradleProjectStructureNode<?>> iterator = nodes.iterator(); iterator.hasNext(); ) {
      GradleProjectStructureNode<?> node = iterator.next();
      if (!key.equals(node.getDescriptor().getAttributes())) {
        iterator.remove();
      }
    }
  }
  
  private interface Helper {
    
    @Nullable
    Collection<GradleProjectStructureNode<?>> getTargetNodes(@NotNull AnActionEvent e);

    void updatePresentation(@Nullable Collection<GradleProjectStructureNode<?>> nodes, @NotNull Presentation presentation);
  }

  private static class ContextMenuHelper implements Helper {
    @Nullable
    @Override
    public Collection<GradleProjectStructureNode<?>> getTargetNodes(@NotNull AnActionEvent e) {
      return GradleDataKeys.SYNC_TREE_SELECTED_NODE.getData(e.getDataContext());
    }

    @Override
    public void updatePresentation(@Nullable Collection<GradleProjectStructureNode<?>> nodes, @NotNull Presentation presentation) {
      presentation.setVisible(true);
      presentation.setEnabled(nodes != null && !nodes.isEmpty());
    }
  }
}
