package org.jetbrains.plugins.gradle.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureTreeModel;
import org.jetbrains.plugins.gradle.util.GradleUtil;
import org.jetbrains.plugins.gradle.util.TextIcon;

/**
 * @author Denis Zhdanov
 * @since 11/22/12 7:27 PM
 */
public class GradleResetTreeFiltersAction extends ToggleAction {

  public GradleResetTreeFiltersAction() {
    getTemplatePresentation().setText(ExternalSystemBundle.message("gradle.action.reset.tree.filters.text"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("gradle.action.reset.tree.filters.description"));
    getTemplatePresentation().setIcon(new TextIcon(ExternalSystemBundle.message("gradle.action.reset.tree.filters.icon")));
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    final ExternalProjectStructureTreeModel model = GradleUtil.getProjectStructureTreeModel(e.getDataContext());
    return model == null || !model.hasAnyFilter();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final ExternalProjectStructureTreeModel treeModel = GradleUtil.getProjectStructureTreeModel(e.getDataContext());
    if (treeModel != null) {
      treeModel.removeAllFilters();
    }
  }
}
