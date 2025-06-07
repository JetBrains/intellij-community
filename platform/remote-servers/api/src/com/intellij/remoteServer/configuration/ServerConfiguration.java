// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.configuration;

import com.intellij.openapi.components.PersistentStateComponent;
import org.jetbrains.annotations.Nullable;

public abstract class ServerConfiguration {
  public abstract PersistentStateComponent<?> getSerializer();

  public @Nullable String getCustomToolWindowId() {
    return null;
  }
}
