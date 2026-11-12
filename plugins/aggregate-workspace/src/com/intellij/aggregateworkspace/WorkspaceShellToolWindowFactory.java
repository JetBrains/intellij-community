package com.intellij.aggregateworkspace;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.jetbrains.annotations.NotNull;

public final class WorkspaceShellToolWindowFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    WorkspaceShellPanel panel = new WorkspaceShellPanel(project);
    toolWindow.getContentManager().addContent(
      toolWindow.getContentManager().getFactory().createContent(panel, "Workspace", false)
    );
  }
}
