// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.win;

import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.impl.SystemDock;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Nikita Provotorov
 */
public final class WinDockDelegateInitializer implements SystemDock.Delegate.Initializer {
  @Override
  public synchronized void onUiInitialization() {
    final var app = ApplicationManagerEx.getApplicationEx();
    shouldBeDisabled = app.isHeadlessEnvironment() || app.isLightEditMode();
    if (shouldBeDisabled)
      return;

    // Unfortunately the Registry component is not loaded yet at this point
    // So we can't write something like
    //  if (!Registry.is("windows.jumplist"))
    //    return;
    // to avoid loading WinShellIntegration's native

    if (!WinShellIntegration.isAvailable)
      return;

    wsi = Objects.requireNonNull(WinShellIntegration.getInstance());

    wsi.updateAppUserModelId();
  }

  @Override
  public synchronized @Nullable SystemDock.Delegate onUiInitialized() {
    if (shouldBeDisabled)
      return null;

    if (wsi == null)
      return null;

    if (!Registry.is("windows.jumplist"))
      return null;

    return new WinDockDelegate(wsi);
  }


  private boolean shouldBeDisabled = true;
  private @Nullable WinShellIntegration wsi = null;
}
