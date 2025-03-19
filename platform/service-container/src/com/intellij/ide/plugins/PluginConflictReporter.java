// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@Internal
public interface PluginConflictReporter {
  void reportConflict(@NotNull Collection<PluginId> foundPlugins, boolean hasConflictWithPlatform);
}
