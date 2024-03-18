// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.sun.jna.Native;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;
import sun.misc.Unsafe;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class X11UiUtil {
  private static final Logger LOG = Logger.getInstance(X11UiUtil.class);

  private static final int True = 1;
  private static final int False = 0;
  private static final long None = 0;
  private static final long XA_ATOM = 4;
  private static final long XA_WINDOW = 33;
  private static final int CLIENT_MESSAGE = 33;
  private static final int FORMAT_BYTE = 8;
  private static final int FORMAT_LONG = 32;
  private static final long EVENT_MASK = (3L << 19);
  private static final long NET_WM_STATE_REMOVE = 0;
  private static final long NET_WM_STATE_ADD = 1;
  private static final long NET_WM_STATE_TOGGLE = 2;

  /**
   * List of all known tile WM in lower case, can be updated later
   */
  public static final Set<String> TILE_WM = Set.of(
    "awesome",
    "bspwm",
    "cagebreak",
    "compiz",
    "dwl",
    "dwm",
    "frankenwm",
    "herbstluftwm",
    "hyprland",
    "i3",
    "ion",
    "larswm",
    "leftwm",
    "notion",
    "qtile",
    "ratpoison",
    "river",
    "snapwm",
    "spectrwm",
    "stumpwm",
    "sway",
    "wmii",
    "xmonad"
  );

  @SuppressWarnings("SpellCheckingInspection")
  private static final class Xlib {
    private Unsafe unsafe;
    private Method XGetWindowProperty;
    private Method XFree;
    private Method RootWindow;
    private Method XSendEvent;
    private Method getWindow;
    private Method getScreenNumber;
    private Method awtLock;
    private Method awtUnlock;

    private long display;

    private long NET_SUPPORTING_WM_CHECK;
    private long NET_WM_ALLOWED_ACTIONS;
    private long NET_WM_STATE;
    private long NET_WM_ACTION_FULLSCREEN;
    private long NET_WM_STATE_FULLSCREEN;
    private long NET_WM_STATE_MAXIMIZED_VERT;
    private long NET_WM_STATE_MAXIMIZED_HORZ;
    private long NET_WM_STATE_DEMANDS_ATTENTION;
    private long NET_ACTIVE_WINDOW;

    private static @Nullable Xlib getInstance() {
      Class<? extends Toolkit> toolkitClass = Toolkit.getDefaultToolkit().getClass();
      if (!StartupUiUtil.isXToolkit()) {
        return null;
      }

      try {
        Xlib x11 = new Xlib();

        // reflect on Xlib method wrappers and important structures
        Class<?> XlibWrapper = Class.forName("sun.awt.X11.XlibWrapper");
        x11.unsafe = (Unsafe)ReflectionUtil.getUnsafe();
        x11.XGetWindowProperty = method(XlibWrapper, "XGetWindowProperty", 12);
        x11.XFree = method(XlibWrapper, "XFree", 1);
        x11.RootWindow = method(XlibWrapper, "RootWindow", 2);
        x11.XSendEvent = method(XlibWrapper, "XSendEvent", 5);
        Class<?> XBaseWindow = Class.forName("sun.awt.X11.XBaseWindow");
        x11.getWindow = method(XBaseWindow, "getWindow");
        x11.getScreenNumber = method(XBaseWindow, "getScreenNumber");
        x11.display = (Long)method(toolkitClass, "getDisplay").invoke(null);
        x11.awtLock = method(toolkitClass, "awtLock");
        x11.awtUnlock = method(toolkitClass, "awtUnlock");

        // intern atoms
        Class<?> XAtom = Class.forName("sun.awt.X11.XAtom");
        Method get = method(XAtom, "get", String.class);
        Field atom = field(XAtom, "atom");
        x11.NET_SUPPORTING_WM_CHECK = (Long)atom.get(get.invoke(null, "_NET_SUPPORTING_WM_CHECK"));
        x11.NET_WM_ALLOWED_ACTIONS = (Long)atom.get(get.invoke(null, "_NET_WM_ALLOWED_ACTIONS"));
        x11.NET_WM_STATE = (Long)atom.get(get.invoke(null, "_NET_WM_STATE"));
        x11.NET_WM_ACTION_FULLSCREEN = (Long)atom.get(get.invoke(null, "_NET_WM_ACTION_FULLSCREEN"));
        x11.NET_WM_STATE_FULLSCREEN = (Long)atom.get(get.invoke(null, "_NET_WM_STATE_FULLSCREEN"));
        x11.NET_WM_STATE_MAXIMIZED_VERT = (Long)atom.get(get.invoke(null, "_NET_WM_STATE_MAXIMIZED_VERT"));
        x11.NET_WM_STATE_MAXIMIZED_HORZ = (Long)atom.get(get.invoke(null, "_NET_WM_STATE_MAXIMIZED_HORZ"));
        x11.NET_WM_STATE_DEMANDS_ATTENTION = (Long)atom.get(get.invoke(null, "_NET_WM_STATE_DEMANDS_ATTENTION"));
        x11.NET_ACTIVE_WINDOW = (Long)atom.get(get.invoke(null, "_NET_ACTIVE_WINDOW"));

        // check for _NET protocol support
        Long netWmWindow = x11.getNetWmWindow();
        if (netWmWindow == null) {
          LOG.info("_NET protocol is not supported");
          return null;
        }

        return x11;
      }
      catch (Throwable t) {
        LOG.info("cannot initialize", t);
      }

      return null;
    }

    private long getRootWindow(long screen) throws Exception {
      awtLock.invoke(null);
      try {
        return (Long)RootWindow.invoke(null, display, screen);
      }
      finally {
        awtUnlock.invoke(null);
      }
    }

    private @Nullable Long getNetWmWindow() throws Exception {
      long rootWindow = getRootWindow(0);
      long[] values = getLongArrayProperty(rootWindow, NET_SUPPORTING_WM_CHECK, XA_WINDOW);
      return values != null && values.length > 0 ? values[0] : null;
    }

    private long @Nullable [] getLongArrayProperty(long window, long name, long type) throws Exception {
      return getWindowProperty(window, name, type, FORMAT_LONG);
    }

    @SuppressWarnings("SameParameterValue")
    private @Nullable <T> T getWindowProperty(long window, long name, long type, long expectedFormat) throws Exception {
      long data = unsafe.allocateMemory(64);
      awtLock.invoke(null);
      try {
        unsafe.setMemory(data, 64, (byte)0);

        int result = (Integer)XGetWindowProperty.invoke(
          null, display, window, name, 0L, 65535L, (long)False, type, data, data + 8, data + 16, data + 24, data + 32);
        if (result == 0) {
          int format = unsafe.getInt(data + 8);
          long pointer = Native.LONG_SIZE == 4 ? unsafe.getInt(data + 32) : unsafe.getLong(data + 32);

          if (pointer != None && format == expectedFormat) {
            int length = Native.LONG_SIZE == 4 ? unsafe.getInt(data + 16) : (int)unsafe.getLong(data + 16);
            if (format == FORMAT_BYTE) {
              byte[] bytes = new byte[length];
              for (int i = 0; i < length; i++) bytes[i] = unsafe.getByte(pointer + i);
              return (T)bytes;
            }
            else if (format == FORMAT_LONG) {
              long[] values = new long[length];
              for (int i = 0; i < length; i++) {
                values[i] = Native.LONG_SIZE == 4 ? unsafe.getInt(pointer + 4L * i) : unsafe.getLong(pointer + 8L * i);
              }
              return (T)values;
            }
            else if (format != None) {
              LOG.info("unexpected format: " + format);
            }
          }

          if (pointer != None) XFree.invoke(null, pointer);

        }
      }
      finally {
        awtUnlock.invoke(null);
        unsafe.freeMemory(data);
      }

      return null;
    }

    private void sendClientMessage(long target, long window, long type, long... data) throws Exception {
      assert data.length <= 5;
      long event = unsafe.allocateMemory(128);
      awtLock.invoke(null);
      try {
        unsafe.setMemory(event, 128, (byte)0);

        unsafe.putInt(event, CLIENT_MESSAGE);
        if (Native.LONG_SIZE == 4) {
          unsafe.putInt(event + 8, True);
          unsafe.putInt(event + 16, (int)window);
          unsafe.putInt(event + 20, (int)type);
          unsafe.putInt(event + 24, FORMAT_LONG);
          for (int i = 0; i < data.length; i++) {
            unsafe.putInt(event + 28 + 4L * i, (int)data[i]);
          }
        }
        else {
          unsafe.putInt(event + 16, True);
          unsafe.putLong(event + 32, window);
          unsafe.putLong(event + 40, type);
          unsafe.putInt(event + 48, FORMAT_LONG);
          for (int i = 0; i < data.length; i++) {
            unsafe.putLong(event + 56 + 8L * i, data[i]);
          }
        }

        XSendEvent.invoke(null, display, target, false, EVENT_MASK, event);
      }
      finally {
        awtUnlock.invoke(null);
        unsafe.freeMemory(event);
      }
    }

    private void sendClientMessage(Window window, String operation, long type, long... params) {
      try {
        ComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(window);
        if (peer == null) throw new IllegalStateException(window + " has no peer");
        long windowId = (Long)getWindow.invoke(peer);
        long screen = (Long)getScreenNumber.invoke(peer);
        long rootWindow = getRootWindow(screen);
        sendClientMessage(rootWindow, windowId, type, params);
      }
      catch (Throwable t) {
        LOG.info("cannot " + operation, t);
      }
    }
  }

  private static final @Nullable Xlib X11 = Xlib.getInstance();

  public static boolean isInitialized() {
    return X11 != null;
  }

  public static boolean isFullScreenSupported() {
    if (X11 == null) return false;

    List<ProjectFrameHelper> frames = WindowManagerEx.getInstanceEx().getProjectFrameHelpers();
    // no frame to check the property so be optimistic here
    if (frames.isEmpty()) {
      return true;
    }

    IdeFrameImpl frame = frames.get(0).getFrame();
    return hasWindowProperty(frame, X11.NET_WM_ALLOWED_ACTIONS, X11.NET_WM_ACTION_FULLSCREEN);
  }

  public static boolean isInFullScreenMode(JFrame frame) {
    return X11 != null && hasWindowProperty(frame, X11.NET_WM_STATE, X11.NET_WM_STATE_FULLSCREEN);
  }

  public static boolean isMaximizedVert(JFrame frame) {
    return X11 != null && hasWindowProperty(frame, X11.NET_WM_STATE, X11.NET_WM_STATE_MAXIMIZED_VERT);
  }

  public static boolean isMaximizedHorz(JFrame frame) {
    return X11 != null && hasWindowProperty(frame, X11.NET_WM_STATE, X11.NET_WM_STATE_MAXIMIZED_HORZ);
  }

  public static void setMaximized(JFrame frame, boolean maximized) {
    if (X11 == null) return;

    if (maximized) {
      X11.sendClientMessage(frame, "set Maximized mode", X11.NET_WM_STATE, NET_WM_STATE_ADD,
                            X11.NET_WM_STATE_MAXIMIZED_HORZ, X11.NET_WM_STATE_MAXIMIZED_VERT);
    }
    else {
      X11.sendClientMessage(frame, "reset Maximized mode", X11.NET_WM_STATE, NET_WM_STATE_REMOVE,
                            X11.NET_WM_STATE_MAXIMIZED_HORZ, X11.NET_WM_STATE_MAXIMIZED_VERT);
    }
  }

  public static boolean isWSL() {
    return SystemInfoRt.isUnix && !SystemInfoRt.isMac && System.getenv("WSL_DISTRO_NAME") != null;
  }

  public static boolean isTileWM() {
    String desktop = System.getenv("XDG_CURRENT_DESKTOP");
    return SystemInfoRt.isUnix && !SystemInfoRt.isMac && desktop != null && TILE_WM.contains(desktop.toLowerCase(Locale.ENGLISH));
  }

  public static boolean isUndefinedDesktop() {
    String desktop = System.getenv("XDG_CURRENT_DESKTOP");
    return SystemInfoRt.isUnix && !SystemInfoRt.isMac && desktop == null;
  }

  private static boolean hasWindowProperty(JFrame frame, long name, long expected) {
    if (X11 == null) return false;
    try {
      ComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(frame);
      if (peer != null) {
        long window = (Long)X11.getWindow.invoke(peer);
        long[] values = X11.getLongArrayProperty(window, name, XA_ATOM);
        if (values != null) {
          return ArrayUtil.indexOf(values, expected) != -1;
        }
      }
      return false;
    }
    catch (Throwable t) {
      LOG.info("cannot check window property", t);
      return false;
    }
  }

  public static void toggleFullScreenMode(JFrame frame) {
    if (X11 == null) return;
    X11.sendClientMessage(frame, "toggle mode", X11.NET_WM_STATE, NET_WM_STATE_TOGGLE, X11.NET_WM_STATE_FULLSCREEN);
  }

  public static void setFullScreenMode(JFrame frame, boolean fullScreen) {
    if (X11 == null) return;

    if (fullScreen) {
      X11.sendClientMessage(frame, "set FullScreen mode", X11.NET_WM_STATE, NET_WM_STATE_ADD, X11.NET_WM_STATE_FULLSCREEN);
    }
    else {
      X11.sendClientMessage(frame, "reset FullScreen mode", X11.NET_WM_STATE, NET_WM_STATE_REMOVE, X11.NET_WM_STATE_FULLSCREEN);
    }
  }

  /**
   * This method requests window manager to activate the specified application window. This might cause the window to steal focus
   * from another (currently active) application, which is generally not considered acceptable. So it should be used only in
   * special cases, when we know that the user definitely expects such behaviour. In most cases, requesting focus in the target
   * window should be used instead - in that case window manager is expected to indicate that the application requires user
   * attention but won't switch the focus to it automatically.
   */
  public static void activate(@NotNull Window window) {
    if (X11 == null) return;
    if (FocusManagerImpl.FOCUS_REQUESTS_LOG.isDebugEnabled()) {
      FocusManagerImpl.FOCUS_REQUESTS_LOG.debug("_NET_ACTIVE_WINDOW", new Throwable());
    }
    X11.sendClientMessage(window, "activate", X11.NET_ACTIVE_WINDOW);
  }

  public static void requestAttention(@NotNull Window window) {
    if (X11 == null) return;
    X11.sendClientMessage(window, "request attention", X11.NET_WM_STATE, NET_WM_STATE_ADD, X11.NET_WM_STATE_DEMANDS_ATTENTION);
  }

  // reflection utilities

  private static Method method(Class<?> aClass, @NonNls String name, Class<?>... parameterTypes) throws Exception {
    while (aClass != null) {
      try {
        Method method = aClass.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
      }
      catch (NoSuchMethodException e) {
        aClass = aClass.getSuperclass();
      }
    }
    throw new NoSuchMethodException(name);
  }

  private static Method method(Class<?> aClass, @NonNls String name, int parameters) throws Exception {
    for (Method method : aClass.getDeclaredMethods()) {
      if (method.getParameterCount() == parameters && name.equals(method.getName())) {
        method.setAccessible(true);
        return method;
      }
    }
    throw new NoSuchMethodException(name);
  }

  @SuppressWarnings("SameParameterValue")
  private static Field field(Class<?> aClass, @NonNls String name) throws Exception {
    Field field = aClass.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
