package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureTreeModel;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * @author Denis Zhdanov
 * @since 3/15/12 11:23 AM
 */
public class GradleRebuildStructureChangesTreeAction extends AnAction {

  public GradleRebuildStructureChangesTreeAction() {
    getTemplatePresentation().setText(ExternalSystemBundle.message("gradle.action.rebuild.sync.tree.text"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("gradle.action.rebuild.sync.tree.description"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ExternalProjectStructureTreeModel model = GradleUtil.getProjectStructureTreeModel(e.getDataContext());
    if (model != null) {
      model.rebuild();
    }
  }
}
