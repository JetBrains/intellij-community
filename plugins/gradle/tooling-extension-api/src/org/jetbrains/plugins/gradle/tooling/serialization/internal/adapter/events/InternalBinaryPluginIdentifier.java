// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.BinaryPluginIdentifier;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class InternalBinaryPluginIdentifier extends AbstractInternalPluginIdentifier implements BinaryPluginIdentifier {
  private final String className;
  private final String pluginId;

  public InternalBinaryPluginIdentifier(String displayName, String className, String pluginId) {
    super(displayName);
    this.className = className;
    this.pluginId = pluginId;
  }

  @Override
  public String getClassName() {
    return this.className;
  }

  @Override
  public String getPluginId() {
    return this.pluginId;
  }
}
