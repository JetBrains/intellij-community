package org.jetbrains.plugins.gradle.ui;

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesPanel;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

public class GradleToolWindowFactory implements ToolWindowFactory, DumbAware {

  @Override
  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    final GradleProjectStructureContext context = ServiceManager.getService(project, GradleProjectStructureContext.class);

    final GradleProjectStructureChangesPanel panel = new GradleProjectStructureChangesPanel(project, context);
    final String syncTitle = GradleBundle.message("gradle.sync.title.tab");
    toolWindow.getContentManager().addContent(new ContentImpl(panel, syncTitle, true));
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        // The general idea is to change dependencies order at the UI if they are changed at the module settings.
        panel.getTreeModel().onModuleRootsChange(); 
      }
    });
  }
}
