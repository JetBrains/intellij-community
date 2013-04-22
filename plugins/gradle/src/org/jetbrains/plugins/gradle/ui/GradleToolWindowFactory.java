package org.jetbrains.plugins.gradle.ui;

import com.intellij.ProjectTopics;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.plugins.gradle.tasks.GradleTasksPanel;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleToolWindowFactory implements ToolWindowFactory, DumbAware {
  
  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    final ProjectStructureServices context = ServiceManager.getService(project, ProjectStructureServices.class);
    
    ContentManager contentManager = toolWindow.getContentManager();
    // TODO den implement
    String tasksTitle = "tasks";
//    String tasksTitle = ExternalSystemBundle.message("gradle.task.title.tab");
    ContentImpl tasksContent = new ContentImpl(
      new GradleTasksPanel(project), tasksTitle, true);
    contentManager.addContent(tasksContent);

    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        if (!project.isDisposed()) {
          PropertiesComponent.getInstance(project).setValue(GradleConstants.ACTIVE_TOOL_WINDOW_TAB_KEY, event.getContent().getDisplayName());
        }
      }
    });
  }
}
