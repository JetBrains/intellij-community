// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.SystemDock;
import org.jetbrains.annotations.Nullable;

/**
 * @author Nikita Provotorov
 */
public final class WinDockDelegateInitializer implements SystemDock.Delegate.Initializer {
  @Override
  public void onUiInitialization() {
    if (wsi != null) {
      wsi.updateAppUserModelId();
    }
  }

  @Override
  public @Nullable SystemDock.Delegate onUiInitialized() {
    if (wsi == null)
      return null;

    final var app = ApplicationManagerEx.getApplicationEx();

    final boolean shouldBeDisabled = app.isHeadlessEnvironment() || app.isLightEditMode();
    if (shouldBeDisabled)
      return null;

    if (!Registry.is("windows.jumplist"))
      return null;

    return new WinDockDelegate(wsi);
  }


  private final @Nullable WinShellIntegration wsi = WinShellIntegration.getInstance();
}
