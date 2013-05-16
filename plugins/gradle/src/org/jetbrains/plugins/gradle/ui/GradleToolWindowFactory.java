package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.externalSystem.service.task.ExternalSystemTasksPanel;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.plugins.gradle.util.GradleBundle;

public class GradleToolWindowFactory implements ToolWindowFactory, DumbAware {
  
  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    toolWindow.setTitle(GradleBundle.message("gradle.name"));
    ContentManager contentManager = toolWindow.getContentManager();
    String tasksTitle = ExternalSystemBundle.message("tool.window.title.tasks");
    ContentImpl tasksContent = new ContentImpl(new ExternalSystemTasksPanel(), tasksTitle, true);
    contentManager.addContent(tasksContent);
  }
}
