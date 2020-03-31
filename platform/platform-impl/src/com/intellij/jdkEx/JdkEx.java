// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jdkEx;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.MethodInvocator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import java.awt.*;
import java.util.List;

/**
 * Provides extensions for OpenJDK API, implemented in JetBrains JDK.
 * For OpenJDK defaults to some meaningful results where applicable or otherwise throws runtime exception.
 *
 * @author tav
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

  // CUSTOM DECOR SUPPORT {{

  public static boolean isCustomDecorationSupported() {
    if (SystemInfo.isJetBrainsJvm && SystemInfo.isWin10OrNewer) {
      return MyCustomDecorMethods.SET_HAS_CUSTOM_DECORATION.isAvailable();
    }
    return false;
  }

  public static void setHasCustomDecoration(@NotNull Window window) {
    if (!isCustomDecorationSupported()) return;
    MyCustomDecorMethods.SET_HAS_CUSTOM_DECORATION.invoke(window);
  }

  public static void setCustomDecorationHitTestSpots(@NotNull Window window, @NotNull List<Rectangle> spots) {
    if (!isCustomDecorationSupported()) return;
    MyCustomDecorMethods.SET_CUSTOM_DECORATION_HITTEST_SPOTS.invoke(AWTAccessor.getComponentAccessor().getPeer(window), spots);
  }

  public static void setCustomDecorationTitleBarHeight(@NotNull Window window, int height) {
    if (!isCustomDecorationSupported()) return;
    MyCustomDecorMethods.SET_CUSTOM_DECORATION_TITLEBAR_HEIGHT.invoke(AWTAccessor.getComponentAccessor().getPeer(window), height);
  }

  // lazy init
  private static class MyCustomDecorMethods {
    public static final MyMethod SET_HAS_CUSTOM_DECORATION =
      MyMethod.create(Window.class, "setHasCustomDecoration");
    public static final MyMethod SET_CUSTOM_DECORATION_HITTEST_SPOTS =
      MyMethod.create("sun.awt.windows.WWindowPeer", "setCustomDecorationHitTestSpots", List.class);
    public static final MyMethod SET_CUSTOM_DECORATION_TITLEBAR_HEIGHT =
      MyMethod.create("sun.awt.windows.WWindowPeer","setCustomDecorationTitleBarHeight", int.class);
  }

  // }} CUSTOM DECOR SUPPORT

  private static class MyMethod {
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

    @Nullable
    public Object invoke(Object object, Object... arguments) {
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
}
