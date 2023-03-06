// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Predicates;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@ApiStatus.Internal
public class InstallAndEnableTaskHeadlessImpl
  extends InstallAndEnableTask {
  public InstallAndEnableTaskHeadlessImpl(@NotNull Set<PluginId> pluginIds, @NotNull Runnable onSuccess) {
    super(null, pluginIds, true, false, true, null, onSuccess);
  }

  @Override
  public void onSuccess() {
    if (myCustomPlugins == null) {
      return;
    }
    // Avoids creation of DialogWrapper and consequent leaks
    new PluginsAdvertiserDialogPluginInstaller(myProject, myPlugins, myCustomPlugins, super::runOnSuccess)
      .doInstallPlugins(Predicates.alwaysTrue(), myModalityState != null ? myModalityState : ModalityState.NON_MODAL);
  }
}
