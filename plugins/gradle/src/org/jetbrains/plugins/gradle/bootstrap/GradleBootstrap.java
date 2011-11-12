package org.jetbrains.plugins.gradle.bootstrap;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesPanel;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleIcons;

import javax.swing.*;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 4:01 PM
 */
public class GradleBootstrap extends AbstractProjectComponent {

  private static final String GRADLE_TOOL_WINDOW_ID = GradleBundle.message("gradle.name");
  
  private final GradleProjectStructureChangesModel myChangesModel;
  
  public GradleBootstrap(@NotNull Project project, @NotNull GradleProjectStructureChangesModel changesModel) {
    super(project);
    myChangesModel = changesModel;
  }

  @Override
  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        initToolWindow();
      }
    });
  }

  private void initToolWindow() {
    // TODO den don't show tool window if no gradle project is associated with the current project.
    if (!Boolean.getBoolean("gradle.show.tool.window")) {
      return;
    }
    final ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(myProject);
    ToolWindow toolWindow = manager.registerToolWindow(GRADLE_TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT);
    toolWindow.setIcon(GradleIcons.GRADLE_ICON);
    String syncTitle = GradleBundle.message("gradle.sync.title.tab");
    toolWindow.getContentManager().addContent(new ContentImpl(new GradleProjectStructureChangesPanel(myChangesModel), syncTitle, true)); 
  }
}
