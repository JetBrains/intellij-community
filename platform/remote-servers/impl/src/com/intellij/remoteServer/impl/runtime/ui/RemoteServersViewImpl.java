package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ui.RemoteServersView;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class RemoteServersViewImpl extends RemoteServersView {
  @NotNull private final Project myProject;

  public RemoteServersViewImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void showServerConnection(@NotNull final ServerConnection<?> connection) {
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(getToolWindowId(connection));
    if (toolWindow != null) {
      toolWindow.activate(new Runnable() {
        @Override
        public void run() {
          ServersToolWindowContent content = getServersViewComponent(toolWindow);
          if (content != null) {
            content.select(connection);
          }
        }
      });
    }
  }

  private static ServersToolWindowContent getServersViewComponent(ToolWindow toolWindow) {
    //todo[nik] register ServersToolWindowContent as project service?
    return UIUtil.findComponentOfType(toolWindow.getComponent(), ServersToolWindowContent.class);
  }

  @Override
  public void showDeployment(@NotNull final ServerConnection<?> connection, @NotNull final String deploymentName) {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    final ToolWindow toolWindow = toolWindowManager.getToolWindow(getToolWindowId(connection));
    if (toolWindow != null) {
      toolWindowManager.invokeLater(new Runnable() {
        @Override
        public void run() {
          ServersToolWindowContent component = getServersViewComponent(toolWindow);
          if (component != null) {
            component.select(connection, deploymentName);
          }
        }
      });
    }
  }

  private static String getToolWindowId(ServerConnection<?> connection) {
    String customToolWindowId = connection.getServer().getType().getCustomToolWindowId();
    return StringUtil.notNullize(customToolWindowId, DefaultServersToolWindowManager.WINDOW_ID);
  }
}
