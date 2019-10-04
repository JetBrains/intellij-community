package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNodeSelector;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ui.RemoteServersView;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author nik
 */
public class RemoteServersViewImpl extends RemoteServersView {
  private final Project myProject;
  private final List<Pair<ServersTreeNodeSelector, Condition<ServerConnection>>> myCustomSelectors = new CopyOnWriteArrayList<>();

  public RemoteServersViewImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void showServerConnection(@NotNull ServerConnection<?> connection) {
    ServersTreeNodeSelector customSelector = findCustomSelector(connection);
    if (customSelector != null) {
      customSelector.select(connection);
      return;
    }

    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(getToolWindowId(connection));
    if (toolWindow != null) {
      toolWindow.activate(() -> {
        ServersTreeNodeSelector selector = findSelector(toolWindow);
        if (selector != null) {
          selector.select(connection);
        }
      });
    }
  }

  private ServersTreeNodeSelector findSelector(ToolWindow toolWindow) {
    //todo[nik] register ServersToolWindowContent as project service?
    return UIUtil.findComponentOfType(toolWindow.getComponent(), ServersToolWindowContent.class);
  }

  private ServersTreeNodeSelector findCustomSelector(ServerConnection<?> connection) {
    for (Pair<ServersTreeNodeSelector, Condition<ServerConnection>> pair : myCustomSelectors) {
      if (pair.second.value(connection)) {
        return pair.first;
      }
    }
    return null;
  }

  @Override
  public void showDeployment(@NotNull ServerConnection<?> connection, @NotNull String deploymentName) {
    ServersTreeNodeSelector customSelector = findCustomSelector(connection);
    if (customSelector != null) {
      customSelector.select(connection, deploymentName);
      return;
    }

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = toolWindowManager.getToolWindow(getToolWindowId(connection));
    if (toolWindow != null) {
      toolWindowManager.invokeLater(() -> {
        ServersTreeNodeSelector selector = findSelector(toolWindow);
        if (selector != null) {
          selector.select(connection, deploymentName);
        }
      });
    }
  }

  @Override
  public void registerCustomTreeNodeSelector(@NotNull ServersTreeNodeSelector selector,
                                             @NotNull Condition<ServerConnection> condition) {
    myCustomSelectors.add(Pair.create(selector, condition));
  }

  private static String getToolWindowId(ServerConnection<?> connection) {
    String customToolWindowId = RemoteServersViewContribution.getRemoteServerToolWindowId(connection.getServer());
    return StringUtil.notNullize(customToolWindowId, DefaultServersToolWindowManager.WINDOW_ID);
  }
}
