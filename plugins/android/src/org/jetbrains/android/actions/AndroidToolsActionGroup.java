package org.jetbrains.android.actions;

import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidToolsActionGroup extends DefaultActionGroup {
  @Override
  public void update(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0);
  }
}
