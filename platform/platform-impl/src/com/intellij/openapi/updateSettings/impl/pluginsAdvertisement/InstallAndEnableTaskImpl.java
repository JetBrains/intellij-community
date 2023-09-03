// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

@ApiStatus.Internal
public final class InstallAndEnableTaskImpl extends InstallAndEnableTask {

  InstallAndEnableTaskImpl(@Nullable Project project,
                           @NotNull Set<PluginId> pluginIds,
                           boolean showDialog,
                           boolean selectAllInDialog,
                           @Nullable ModalityState modalityState,
                           @NotNull Runnable onSuccess) {
    super(project, pluginIds, false, showDialog, selectAllInDialog, modalityState, onSuccess);
  }
}

