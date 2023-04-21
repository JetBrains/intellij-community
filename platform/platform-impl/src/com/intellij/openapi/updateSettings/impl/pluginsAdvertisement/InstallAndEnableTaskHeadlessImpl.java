// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@ApiStatus.Internal
public class InstallAndEnableTaskHeadlessImpl
  extends InstallAndEnableTask {
  public InstallAndEnableTaskHeadlessImpl(@NotNull Set<PluginId> pluginIds) {
    super(null, pluginIds, true);
  }
}
