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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class RemoteServersViewImpl extends RemoteServersView {
  @NotNull private final Project myProject;
  @NotNull private final List<Pair<ServersTreeNodeSelector, Condition<ServerConnection>>> myCustomSelectors = ContainerUtil.newSmartList();

  public RemoteServersViewImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void showServerConnection(@NotNull final ServerConnection<?> connection) {
    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(getToolWindowId(connection));
    if (toolWindow != null) {
      toolWindow.activate(() -> {
        ServersTreeNodeSelector selector = getSelector(toolWindow, connection);
        if (selector != null) {
          selector.select(connection);
        }
      });
    }
  }

  private ServersTreeNodeSelector getSelector(ToolWindow toolWindow, ServerConnection<?> connection) {
    //todo[nik] register ServersToolWindowContent as project service?
    ServersTreeNodeSelector selector = UIUtil.findComponentOfType(toolWindow.getComponent(), ServersToolWindowContent.class);
    if (selector != null) return selector;

    for (Pair<ServersTreeNodeSelector, Condition<ServerConnection>> pair : myCustomSelectors) {
      if (pair.second.value(connection)) {
        return pair.first;
      }
    }
    return null;
  }

  @Override
  public void showDeployment(@NotNull final ServerConnection<?> connection, @NotNull final String deploymentName) {
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    final ToolWindow toolWindow = toolWindowManager.getToolWindow(getToolWindowId(connection));
    if (toolWindow != null) {
      toolWindowManager.invokeLater(() -> {
        ServersTreeNodeSelector selector = getSelector(toolWindow, connection);
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
