// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.foundation;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ReflectionUtil;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.ui.mac.foundation.Foundation.*;

public final class MacUtil {
  private static final Logger LOG = Logger.getInstance(MacUtil.class);

  private MacUtil() { }

  public static @Nullable ID findWindowForTitle(@Nullable String title) {
    if (title == null || title.isEmpty()) return null;
    final ID pool = invoke("NSAutoreleasePool", "new");

    ID focusedWindow = null;
    try {
      final ID sharedApplication = invoke("NSApplication", "sharedApplication");
      final ID windows = invoke(sharedApplication, "windows");
      final ID windowEnumerator = invoke(windows, "objectEnumerator");

      while (true) {
        // dirty hack: walks through all the windows to find a cocoa window to show sheet for
        final ID window = invoke(windowEnumerator, "nextObject");
        if (ID.NIL.equals(window)) break;

        final ID windowTitle = invoke(window, "title");
        if (!ID.NIL.equals(windowTitle)) {
          final String titleString = toStringViaUTF8(windowTitle);
          if (Objects.equals(titleString, title)) {
            focusedWindow = window;
            break;
          }
        }
      }
    }
    finally {
      invoke(pool, "release");
    }

    return focusedWindow;
  }

  /** @deprecated the method became obsolete with the demise of sheets */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public static void adjustFocusTraversal(@NotNull Disposable disposable) { }

  public static @NotNull ID getWindowFromJavaWindow(@Nullable Window w) {
    if (w == null) {
      return ID.NIL;
    }
    if (SystemInfo.isJetBrainsJvm) {
      try {
        Object cPlatformWindow = getPlatformWindow(w);
        if (cPlatformWindow != null) {
          Field ptr = cPlatformWindow.getClass().getSuperclass().getDeclaredField("ptr");
          ptr.setAccessible(true);
          return new ID(ptr.getLong(cPlatformWindow));
        }
      }
      catch (IllegalAccessException | NoSuchFieldException e) {
        LOG.debug(e);
      }
    }
    return ID.NIL;
  }

  @ApiStatus.Internal
  public static @Nullable Object getPlatformPeer(@NotNull Window w) {
    if (SystemInfo.isJetBrainsJvm) {
      try {
        Class<?> awtAccessor = Class.forName("sun.awt.AWTAccessor");
        Object componentAccessor = awtAccessor.getMethod("getComponentAccessor").invoke(null);
        Method getPeer = componentAccessor.getClass().getMethod("getPeer", Component.class);
        getPeer.setAccessible(true);
        return getPeer.invoke(componentAccessor, w);
      }
      catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
        LOG.debug(e);
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public static @Nullable Object getPeerUnderCursor(@NotNull Object peer) {
    try {
      Method method = peer.getClass().getMethod("getPeerUnderCursor");
      return method.invoke(null); // null because the method is static, we only need the peer here to get its class
    }
    catch (Throwable ex) {
      LOG.debug(ex);
    }
    return null;
  }

  public static @Nullable Object getPlatformWindow(@NotNull Window w) {
    if (SystemInfo.isJetBrainsJvm) {
      try {
        Class<?> awtAccessor = Class.forName("sun.awt.AWTAccessor");
        Object componentAccessor = awtAccessor.getMethod("getComponentAccessor").invoke(null);
        Method getPeer = componentAccessor.getClass().getMethod("getPeer", Component.class);
        getPeer.setAccessible(true);
        Object peer = getPeer.invoke(componentAccessor, w);
        if (peer != null) {
          Class<?> cWindowPeerClass = peer.getClass();
          Method getPlatformWindowMethod = cWindowPeerClass.getDeclaredMethod("getPlatformWindow");
          Object cPlatformWindow = getPlatformWindowMethod.invoke(peer);
          if (cPlatformWindow != null) {
            return cPlatformWindow;
          }
        }
      }
      catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | ClassNotFoundException e) {
        LOG.debug(e);
      }
    }
    return null;
  }

  public static boolean isNativeBoundsEmpty(@NotNull Window window) {
    Object platformWindow = getPlatformWindow(window);
    if (platformWindow != null) {
      try {
        Field boundsField = platformWindow.getClass().getDeclaredField("nativeBounds");
        boundsField.setAccessible(true);
        Object boundsObject = boundsField.get(platformWindow);
        if (boundsObject instanceof Rectangle bounds) {
          return bounds.isEmpty();
        }
      }
      catch (NoSuchFieldException | IllegalAccessException e) {
        LOG.debug(e);
      }
    }
    return false;
  }

  public static void updateRootPane(@NotNull Window window, @NotNull JRootPane rootPane) {
    try {
      Object platformWindow = getPlatformWindow(window);
      if (platformWindow == null) {
        return;
      }

      Field field = platformWindow.getClass().getDeclaredField("CLIENT_PROPERTY_APPLICATOR");
      field.setAccessible(true);
      Object clientPropertyApplicator = field.get(platformWindow);

      Method method = ReflectionUtil.getMethod(clientPropertyApplicator.getClass(), "attachAndApplyClientProperties", JComponent.class);
      if (method != null) {
        method.invoke(clientPropertyApplicator, rootPane);
      }
    }
    catch (Throwable e) {
      LOG.debug(e);
    }
    finally {
      // https://youtrack.jetbrains.com/issue/IDEA-323593
      window.revalidate();
    }
  }

  public static ID findWindowFromJavaWindow(final Window w) {
    if (Registry.is("skip.untitled.windows.for.mac.messages")) {
      ID window = getWindowFromJavaWindow(w);
      if (!ID.NIL.equals(window)) {
        return window;
      }
    }
    return findWindowForTitle(getWindowTitle(w));
  }

  public static @Nullable String getWindowTitle(Window documentRoot) {
    String windowTitle = null;
    if (documentRoot instanceof Frame) {
      windowTitle = ((Frame)documentRoot).getTitle();
    }
    else if (documentRoot instanceof Dialog) {
      windowTitle = ((Dialog)documentRoot).getTitle();
    }
    return windowTitle;
  }

  @SuppressWarnings("unused")
  private static final class NSActivityOptions {
    // Used for activities that require the computer to not idle sleep. This is included in NSActivityUserInitiated.
    private static final long idleSystemSleepDisabled = 1L << 20;

    // App is performing a user-requested action.
    private static final long userInitiated = 0x00FFFFFFL | idleSystemSleepDisabled;
    private static final long userInitiatedAllowingIdleSystemSleep = userInitiated & ~idleSystemSleepDisabled;

    // Used for activities that require the highest amount of timer and I/O precision available. Very few applications should need to use this constant.
    private static final long latencyCritical = 0xFF00000000L;
  }

  private static final class ActivityImpl extends AtomicReference<ID> implements Runnable {
    private static final ID processInfoCls = getObjcClass("NSProcessInfo");
    private static final Pointer processInfoSel = createSelector("processInfo");
    private static final Pointer beginActivityWithOptionsReasonSel = createSelector("beginActivityWithOptions:reason:");
    private static final Pointer endActivitySel = createSelector("endActivity:");
    private static final Pointer retainSel = createSelector("retain");
    private static final Pointer releaseSel = createSelector("release");

    private ActivityImpl(@NotNull Object reason) {
      super(begin(reason));
    }

    /**
     * Ends activity, allowing macOS to trigger AppNap (idempotent).
     */
    @Override
    public void run() {
      end(getAndSet(null));
    }

    private static ID getProcessInfo() { return invoke(processInfoCls, processInfoSel); }

    private static ID begin(@NotNull Object reason) {
      // http://lists.apple.com/archives/java-dev/2014/Feb/msg00053.html
      // https://developer.apple.com/library/prerelease/ios/documentation/Cocoa/Reference/Foundation/Classes/NSProcessInfo_Class/index.html#//apple_ref/c/tdef/NSActivityOptions
      return invoke(invoke(getProcessInfo(), beginActivityWithOptionsReasonSel,
                           NSActivityOptions.userInitiatedAllowingIdleSystemSleep, nsString(reason.toString())),
                    retainSel);
    }

    private static void end(@Nullable ID activityToken) {
      if (activityToken == null) {
        return;
      }
      invoke(getProcessInfo(), endActivitySel, activityToken);
      invoke(activityToken, releaseSel);
    }
  }

  public static @NotNull Runnable wakeUpNeo(@NotNull Object reason) {
    return new ActivityImpl(reason);
  }
}
