package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNodeSelector;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ui.RemoteServersView;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author nik
 */
public class RemoteServersViewImpl extends RemoteServersView {
  private final List<Pair<ServersTreeNodeSelector, Condition<ServerConnection<?>>>> mySelectors = new CopyOnWriteArrayList<>();

  @Override
  public void showServerConnection(@NotNull ServerConnection<?> connection) {
    ServersTreeNodeSelector selector = findSelector(connection);
    if (selector != null) {
      selector.select(connection);
    }
  }

  private ServersTreeNodeSelector findSelector(ServerConnection<?> connection) {
    for (Pair<ServersTreeNodeSelector, Condition<ServerConnection<?>>> pair : mySelectors) {
      if (pair.second.value(connection)) {
        return pair.first;
      }
    }
    return null;
  }

  @Override
  public void showDeployment(@NotNull ServerConnection<?> connection, @NotNull String deploymentName) {
    ServersTreeNodeSelector selector = findSelector(connection);
    if (selector != null) {
      selector.select(connection, deploymentName);
    }
  }

  @Override
  public void registerTreeNodeSelector(@NotNull ServersTreeNodeSelector selector,
                                       @NotNull Condition<ServerConnection<?>> condition) {
    mySelectors.add(Pair.create(selector, condition));
  }
}
