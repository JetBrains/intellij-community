// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkEx;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.MethodInvocator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import sun.awt.AWTAccessor;

import java.awt.*;
import java.util.List;

/**
 * Provides extensions for OpenJDK API, implemented in JetBrains JDK.
 * For OpenJDK defaults to some meaningful results where applicable or otherwise throws runtime exception.
 */
@ApiStatus.Experimental
public class JdkEx {
  @SuppressWarnings("unused")
  @NotNull
  public static InputEventEx getInputEventEx() {
    if (SystemInfo.isJetBrainsJvm) {
      return new JBInputEventEx();
    }
    return new DefInputEventEx();
  }

  public static DisplayModeEx getDisplayModeEx() {
    if (SystemInfo.isJetBrainsJvm) {
      return new JBDisplayModeEx();
    }
    return new DefDisplayModeEx();
  }

  public static boolean isCustomDecorationSupported() {
    if (SystemInfo.isJetBrainsJvm && SystemInfo.isWin10OrNewer) {
      try {
        MethodInvocator invocator = new MethodInvocator(false, Class.forName("java.awt.Window"), "setHasCustomDecoration");
        return invocator.isAvailable();
      }
      catch (ClassNotFoundException ignore) {
      }
    }
    return false;
  }

  public static void setHasCustomDecoration(@NotNull Window window) {
    if (SystemInfo.isJetBrainsJvm && SystemInfo.isWindows) {
      try {
        MethodInvocator invocator = new MethodInvocator(false, Class.forName("java.awt.Window"), "setHasCustomDecoration");
        if (invocator.isAvailable()) {
          invocator.invoke(window);
        }
      }
      catch (ClassNotFoundException ignore) {
      }
    }
  }

  public static void setCustomDecorationHitTestSpots(@NotNull Window window, @NotNull List<Rectangle> spots) {
    if (SystemInfo.isJetBrainsJvm && SystemInfo.isWindows) {
      try {
        MethodInvocator invocator =
          new MethodInvocator(false, Class.forName("sun.awt.windows.WWindowPeer"), "setCustomDecorationHitTestSpots", List.class);
        if (invocator.isAvailable()) {
          invocator.invoke(AWTAccessor.getComponentAccessor().getPeer(window), spots);
        }
      }
      catch (ClassNotFoundException ignore) {
      }
    }
  }
}
