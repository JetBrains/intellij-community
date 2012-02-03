package org.jetbrains.plugins.gradle.bootstrap;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.content.impl.ContentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.PlatformFacade;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesPanel;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * Encapsulates initialisation routine of the gradle integration.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 4:01 PM
 */
public class GradleBootstrap extends AbstractProjectComponent {

  private static final String GRADLE_TOOL_WINDOW_ID = GradleBundle.message("gradle.name");
  
  private final GradleProjectStructureChangesModel myChangesModel;
  private final PlatformFacade myProjectStructureHelper;
  
  public GradleBootstrap(@NotNull Project project,
                         @NotNull GradleProjectStructureChangesModel changesModel,
                         @NotNull PlatformFacade projectStructureHelper) {
    super(project);
    myChangesModel = changesModel;
    myProjectStructureHelper = projectStructureHelper;
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
    final ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(myProject);
    ToolWindow toolWindow = manager.registerToolWindow(GRADLE_TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT);
    toolWindow.setIcon(GradleIcons.GRADLE_ICON);
    String syncTitle = GradleBundle.message("gradle.sync.title.tab");
    final GradleProjectStructureChangesPanel projectStructureChanges
      = new GradleProjectStructureChangesPanel(myProject, myChangesModel, myProjectStructureHelper);
    toolWindow.getContentManager().addContent(new ContentImpl(projectStructureChanges, syncTitle, true)); 
  }
}
