// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.clouds.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.execution.services.ServiceViewActionUtils.getTarget;

final class ServersTreeActionUtils {
  private ServersTreeActionUtils() {
  }

  static @Nullable ServersTreeStructure.RemoteServerNode getRemoteServerTarget(@NotNull AnActionEvent e) {
    return getTarget(e, ServersTreeStructure.RemoteServerNode.class);
  }
}
