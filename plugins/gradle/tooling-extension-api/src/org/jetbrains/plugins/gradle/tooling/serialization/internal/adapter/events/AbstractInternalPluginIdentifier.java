// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.PluginIdentifier;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public abstract class AbstractInternalPluginIdentifier implements PluginIdentifier, Serializable {
  private final String displayName;

  AbstractInternalPluginIdentifier(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String getDisplayName() {
    return this.displayName;
  }
}