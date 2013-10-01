package org.jetbrains.plugins.gradle.action;

import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * @author Denis Zhdanov
 * @since 3/17/12 2:34 PM
 */
public class GradleToolWindowHelpAction extends ContextHelpAction {

  @Override
  public void update(AnActionEvent event) {
    final Project project = CommonDataKeys.PROJECT.getData(event.getDataContext());
    if (project == null) {
      event.getPresentation().setVisible(false);
      return;
    }

    // TODO den implement
//    if (StringUtil.isEmpty(GradleSettings.getInstance(project).getLinkedExternalProjectPath())) {
//      event.getPresentation().setVisible(false);
//      return;
//    }
    event.getPresentation().setVisible(true);
    super.update(event);
  }

  @Override
  protected String getHelpId(DataContext dataContext) {
    return GradleConstants.HELP_TOPIC_TOOL_WINDOW;
  }
}
