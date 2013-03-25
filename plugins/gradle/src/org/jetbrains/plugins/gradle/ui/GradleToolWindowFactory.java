package org.jetbrains.plugins.gradle.ui;

import com.intellij.ProjectTopics;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
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
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesPanel;
import org.jetbrains.plugins.gradle.tasks.GradleTasksPanel;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

public class GradleToolWindowFactory implements ToolWindowFactory, DumbAware {
  
  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    final GradleProjectStructureContext context = ServiceManager.getService(project, GradleProjectStructureContext.class);
    
    // Project structure.
    final GradleProjectStructureChangesPanel projectStructurePanel = new GradleProjectStructureChangesPanel(project, context);
    final String projectStructureTitle = GradleBundle.message("gradle.sync.title.tab");
    ContentImpl projectStructureContent = new ContentImpl(projectStructurePanel, projectStructureTitle, true);
    ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(projectStructureContent);
    
    // Task.
    String tasksTitle = GradleBundle.message("gradle.task.title.tab");
    ContentImpl tasksContent = new ContentImpl(
      new GradleTasksPanel(project), tasksTitle, true);
    contentManager.addContent(tasksContent);
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        // The general idea is to change dependencies order at the UI if they are changed at the module settings.
        projectStructurePanel.getTreeModel().onModuleRootsChange();
      }
    });

    // Restore previously selected tab.
    String toSelect = PropertiesComponent.getInstance(project).getValue(GradleConstants.ACTIVE_TOOL_WINDOW_TAB_KEY, projectStructureTitle);
    if (tasksTitle.equals(toSelect)) {
      contentManager.setSelectedContent(tasksContent);
    }

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
