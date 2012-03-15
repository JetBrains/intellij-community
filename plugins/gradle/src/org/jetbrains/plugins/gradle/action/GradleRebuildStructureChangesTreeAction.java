package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureTreeModel;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;

/**
 * @author Denis Zhdanov
 * @since 3/15/12 11:23 AM
 */
public class GradleRebuildStructureChangesTreeAction extends AnAction {

  public GradleRebuildStructureChangesTreeAction() {
    getTemplatePresentation().setText(GradleBundle.message("gradle.action.rebuild.sync.tree.text"));
    getTemplatePresentation().setDescription(GradleBundle.message("gradle.action.rebuild.sync.tree.description"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final GradleProjectStructureTreeModel model = getModel(e.getDataContext());
    if (model != null) {
      model.rebuild();
    }
  }

  @Nullable
  private static GradleProjectStructureTreeModel getModel(@NotNull DataContext context) {
    final GradleProjectStructureTreeModel model = GradleDataKeys.SYNC_TREE_MODEL.getData(context);
    if (model != null) {
      return model;
    }
    
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (project == null) {
      return null;
    }

    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    final ToolWindow toolWindow = toolWindowManager.getToolWindow(GradleConstants.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return null;
    }
    
    final ContentManager contentManager = toolWindow.getContentManager();
    if (contentManager == null) {
      return null;
    }

    for (Content content : contentManager.getContents()) {
      final JComponent component = content.getComponent();
      if (component instanceof DataProvider) {
        final Object data = ((DataProvider)component).getData(GradleDataKeys.SYNC_TREE_MODEL.getName());
        if (data instanceof GradleProjectStructureTreeModel) {
          return (GradleProjectStructureTreeModel)data;
        }
      }
    }
    return null;
  }
}
