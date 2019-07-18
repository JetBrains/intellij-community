// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.foundation;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class NSColor {
  public static @Nullable Color getHighlightColor() {
    if (!SystemInfo.isMac)
      return null;

    return _getNSColor("selectedControlColor");
  }

  public static @Nullable Color getAccentColor() {
    if (!SystemInfo.isMac || !SystemInfo.isOsVersionAtLeast("10.14"))
      return null;

    return _getNSColor("controlAccentColor");
  }

  private static @Nullable Color _getNSColor(@NotNull String selector) {
    final Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      final ID nsCol = Foundation.invoke(
        Foundation.invoke("NSColor", selector),
        "colorUsingColorSpace:",
        Foundation.invoke("NSColorSpace", "genericRGBColorSpace"));
      if (nsCol == null || nsCol.equals(ID.NIL))
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
