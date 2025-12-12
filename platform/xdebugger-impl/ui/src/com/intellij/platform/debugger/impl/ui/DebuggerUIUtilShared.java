// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.ui;

import com.intellij.ide.ui.AntiFlickeringPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class DebuggerUIUtilShared {
  private static boolean shouldUseAntiFlickeringPanel() {
    return !ApplicationManager.getApplication().isUnitTestMode() && Registry.intValue("debugger.anti.flickering.delay", 0) > 0;
  }

  public static @NotNull JComponent wrapWithAntiFlickeringPanel(@NotNull JComponent component) {
    return shouldUseAntiFlickeringPanel() ? new AntiFlickeringPanel(component) : component;
  }

  public static boolean freezePaintingToReduceFlickering(@Nullable Component component) {
    if (component instanceof AntiFlickeringPanel antiFlickeringPanel) {
      int delay = Registry.intValue("debugger.anti.flickering.delay", 0);
      if (delay > 0) {
        ApplicationManager.getApplication().invokeAndWait(() -> antiFlickeringPanel.freezePainting(delay), ModalityState.any());
        return true;
      }
    }
    return false;
  }
}
