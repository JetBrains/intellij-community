// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.remoteServer.impl.runtime.ui.tree.ServersTreeStructure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.execution.services.ServiceViewActionUtils.getTarget;

class ServersTreeActionUtils {
  private ServersTreeActionUtils() {
  }

  @Nullable
  static ServersTreeStructure.RemoteServerNode getRemoteServerTarget(@NotNull AnActionEvent e) {
    return getTarget(e, ServersTreeStructure.RemoteServerNode.class);
  }
}
