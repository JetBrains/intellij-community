// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.AWTEventListener;

public final class MacGestureSupportInstaller {
  public static void installOnComponent(@NotNull JComponent component, @Nullable AWTEventListener listener) {
    try {
      new MacGestureSupportForEditor(component, listener);
    }
    catch (Throwable t) {
      Logger.getInstance(MacGestureSupportInstaller.class).warn("macOS gesture support failed", t);
    }
  }
}
