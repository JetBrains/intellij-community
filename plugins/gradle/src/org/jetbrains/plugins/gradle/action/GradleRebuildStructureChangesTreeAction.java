package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureTreeModel;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

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
    final GradleProjectStructureTreeModel model = GradleUtil.getProjectStructureTreeModel(e.getDataContext());
    if (model != null) {
      model.rebuild();
    }
  }
}
