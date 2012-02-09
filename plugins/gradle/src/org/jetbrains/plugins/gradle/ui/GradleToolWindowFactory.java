package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.plugins.gradle.diff.PlatformFacade;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesPanel;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.util.GradleBundle;

public class GradleToolWindowFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    final GradleProjectStructureChangesModel model = project.getComponent(GradleProjectStructureChangesModel.class);
    final PlatformFacade facade = ServiceManager.getService(PlatformFacade.class);
    final GradleProjectStructureHelper helper = project.getComponent(GradleProjectStructureHelper.class);

    final GradleProjectStructureChangesPanel panel = new GradleProjectStructureChangesPanel(project, model, facade, helper);
    final String syncTitle = GradleBundle.message("gradle.sync.title.tab");
    toolWindow.getContentManager().addContent(new ContentImpl(panel, syncTitle, true));
  }
}
