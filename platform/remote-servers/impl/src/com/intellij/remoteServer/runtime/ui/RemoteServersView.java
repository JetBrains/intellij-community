// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.runtime.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeNodeSelector;
import com.intellij.remoteServer.runtime.ServerConnection;
import org.jetbrains.annotations.NotNull;

public abstract class RemoteServersView {
  public static RemoteServersView getInstance(@NotNull Project project) {
    return project.getService(RemoteServersView.class);
  }

  public abstract void showServerConnection(@NotNull ServerConnection<?> connection);

  public abstract void showDeployment(@NotNull ServerConnection<?> connection, @NotNull String deploymentName);

  public abstract void registerTreeNodeSelector(@NotNull ServersTreeNodeSelector selector,
                                                @NotNull Condition<ServerConnection<?>> condition);
}
