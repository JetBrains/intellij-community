// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface UpdateResultCallbackProvider {
  UpdateResultCallback createCallback(@NotNull CheckForUpdateResult checkForUpdateResult,
                                      @Nullable Collection<PluginDownloader> updatedPlugins,
                                      @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins,
                                      @NotNull UpdateSettings updateSettings,
                                      boolean enableLink);
}