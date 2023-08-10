// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jdkEx;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.MethodInvocator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Provides extensions for OpenJDK API, implemented in JetBrains JDK.
 * For OpenJDK defaults to some meaningful results where applicable or otherwise throws runtime exception.
 * <p>
 * WARNING: For internal usage only.
 *
 * @author tav
 */
@ApiStatus.Internal
public final class JdkEx {
  private static final Logger LOG = Logger.getInstance(JdkEx.class);

  @SuppressWarnings("unused")
  public static @NotNull InputEventEx getInputEventEx() {
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

  public static void setTransparent(@NotNull JWindow window) {
    // disable -Dswing.bufferPerWindow=true for the window
    JComponent rootPane = window.getRootPane();
    if (rootPane != null) {
      rootPane.setDoubleBuffered(false);
    }
    JComponent contentPane = (JComponent)window.getContentPane();
    if (contentPane != null) {
      contentPane.setDoubleBuffered(false);
    }

    //noinspection UseJBColor
    window.setBackground(new Color(1, 1, 1, 0));
  }

  private static final class MyMethod {
    private static final MyMethod EMPTY_INSTANCE = new MyMethod(null);

    @Nullable MethodInvocator myInvocator;

    public static MyMethod create(@NotNull String className, @NotNull String methodName, Class<?>... parameterTypes) {
      try {
        return create(Class.forName(className), methodName, parameterTypes);
      }
      catch (ClassNotFoundException ignore) {
      }
      return EMPTY_INSTANCE;
    }

    public static MyMethod create(@NotNull Class<?> cls, @NotNull String methodName, Class<?>... parameterTypes) {
      return new MyMethod(new MethodInvocator(false, cls, methodName, parameterTypes));
    }

    private MyMethod(@Nullable MethodInvocator invocator) {
      this.myInvocator = invocator;
    }

    public boolean isAvailable() {
      return myInvocator != null && myInvocator.isAvailable();
    }

    public @Nullable Object invoke(Object object, Object... arguments) {
      if (isAvailable()) {
        //noinspection ConstantConditions
        return myInvocator.invoke(object, arguments);
      }
      return null;
    }
  }

  public static void setIgnoreMouseEvents(@NotNull Window window, boolean ignoreMouseEvents) {
    if (SystemInfo.isJetBrainsJvm && (SystemInfo.isMac || SystemInfo.isWindows)) {
      window.setEnabled(false);
      try {
        MethodInvocator invocator =
          new MethodInvocator(false, Class.forName("java.awt.Window"), "setIgnoreMouseEvents", boolean.class);
        if (invocator.isAvailable()) {
          invocator.invoke(window, ignoreMouseEvents);
        }
      }
      catch (ClassNotFoundException ignore) {
      }
    }
  }

  private static MethodInvocator mySetTabbingMode;

  private static @Nullable MethodInvocator getTabbingModeInvocator() {
    if ((ExperimentalUI.isNewUI() && !Registry.is("ide.mac.os.wintabs.version2", true)) ||
        !SystemInfo.isJetBrainsJvm ||
        !SystemInfo.isMacOSBigSur ||
        !Registry.is("ide.mac.bigsur.window.with.tabs.enabled", true)) {
      if (SystemInfoRt.isMac) {
        LOG.info("=== TabbingMode: disabled (" +
                 SystemInfo.isJetBrainsJvm + "," +
                 SystemInfo.isMacOSBigSur + "," +
                 Registry.is("ide.mac.bigsur.window.with.tabs.enabled") + "," +
                 ExperimentalUI.isNewUI() +
                 Registry.is("ide.mac.os.wintabs.version2") + ") ===");
      }
      return null;
    }
    if (mySetTabbingMode == null) {
      try {
        mySetTabbingMode = new MethodInvocator(false, Class.forName("java.awt.Window"), "setTabbingMode");
        if (mySetTabbingMode.isAvailable()) {
          LOG.info("=== TabbingMode: available ===");
          return mySetTabbingMode;
        }
      }
      catch (ClassNotFoundException e) {
        LOG.error(e);
      }
      LOG.info("=== TabbingMode: not available ===");
      return null;
    }
    return mySetTabbingMode.isAvailable() ? mySetTabbingMode : null;
  }

  public static boolean isTabbingModeAvailable() {
    return getTabbingModeInvocator() != null;
  }

  public static boolean setTabbingMode(@NotNull Window window, @NotNull String windowId, @Nullable Runnable moveTabToNewWindowCallback) {
    MethodInvocator invocator = getTabbingModeInvocator();
    if (invocator != null) {
      invocator.invoke(window);
      ((RootPaneContainer)window).getRootPane().putClientProperty("JavaWindowTabbingIdentifier", windowId);

      if (moveTabToNewWindowCallback != null) {
        try {
          new MethodInvocator(false, Class.forName("java.awt.Window"), "setMoveTabToNewWindowCallback", Runnable.class)
            .invoke(window, moveTabToNewWindowCallback);
        }
        catch (ClassNotFoundException e) {
          LOG.error(e);
        }
      }
      LOG.info("=== TabbingMode: on ===");
      return true;
    }
    LOG.info("=== TabbingMode: off ===");
    return false;
  }
}
