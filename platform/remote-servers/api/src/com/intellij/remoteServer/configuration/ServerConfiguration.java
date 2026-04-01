// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.configuration;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

public abstract class ServerConfiguration {
  public abstract PersistentStateComponent<?> getSerializer();

  public @Nullable String getCustomToolWindowId() {
    return null;
  }

  /**
   * Returns whether this server configuration is applicable to the given project context.
   * <p>
   * {@code project == null} means the configuration is being accessed outside of any open project (e.g. the Welcome Screen).
   * The default implementation always returns {@code true}; Docker server configurations may override this
   * to restrict visibility based on the project's environment (e.g. Docker-in-Docker, WSL).
   */
  @ApiStatus.Internal
  public boolean isVisibleInProject(@Nullable Project project) {
    return true;
  }
}
