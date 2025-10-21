// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class NSColor {
  public static @Nullable Color getHighlightColor() {
    if (!SystemInfo.isMac)
      return null;

    return _getNSColor("selectedControlColor");
  }

  public static @Nullable Color getAccentColor() {
    return OS.CURRENT == OS.macOS && OS.CURRENT.isAtLeast(10, 14) ? _getNSColor("controlAccentColor") : null;
  }

  private static @Nullable Color _getNSColor(@NotNull String selector) {
    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID nsCol = Foundation.invoke(
        Foundation.invoke("NSColor", selector),
        "colorUsingColorSpace:",
        Foundation.invoke("NSColorSpace", "genericRGBColorSpace"));
      if (nsCol.equals(ID.NIL))
        return null;

      final double nsRed    = Foundation.invoke_fpret(nsCol, "redComponent");
      final double nsGreen  = Foundation.invoke_fpret(nsCol, "greenComponent");
      final double nsBlue   = Foundation.invoke_fpret(nsCol, "blueComponent");
      final double nsAlpha  = Foundation.invoke_fpret(nsCol, "alphaComponent");

      return new Color((float)nsRed, (float)nsGreen, (float)nsBlue, (float)nsAlpha);
    } finally {
      pool.drain();
    }
  }
}
