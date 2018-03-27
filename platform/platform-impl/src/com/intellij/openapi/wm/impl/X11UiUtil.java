/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.Nullable;
import sun.misc.Unsafe;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ArrayUtil.newLongArray;

public class X11UiUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.X11UiUtil");

  private static final int True = 1;
  private static final int False = 0;
  private static final long None = 0;
  private static final long XA_ATOM = 4;
  private static final long XA_WINDOW = 33;
  private static final int CLIENT_MESSAGE = 33;
  private static final int FORMAT_BYTE = 8;
  private static final int FORMAT_LONG = 32;
  private static final long EVENT_MASK = (3L << 19);
  private static final long NET_WM_STATE_TOGGLE = 2;

  @SuppressWarnings("SpellCheckingInspection")
  private static class Xlib {
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

    private long UTF8_STRING;
    private long NET_SUPPORTING_WM_CHECK;
    private long NET_WM_NAME;
    private long NET_WM_ALLOWED_ACTIONS;
    private long NET_WM_STATE;
    private long NET_WM_ACTION_FULLSCREEN;
    private long NET_WM_STATE_FULLSCREEN;

    @Nullable
    private static Xlib getInstance() {
      Class<? extends Toolkit> toolkitClass = Toolkit.getDefaultToolkit().getClass();
      if (!SystemInfo.isXWindow || !"sun.awt.X11.XToolkit".equals(toolkitClass.getName())) {
        return null;
      }

      try {
        Xlib x11 = new Xlib();

        // reflect on Xlib method wrappers and important structures
        Class<?> XlibWrapper = Class.forName("sun.awt.X11.XlibWrapper");
        x11.unsafe = (Unsafe)field(XlibWrapper, "unsafe").get(null);
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
        x11.UTF8_STRING = (Long)atom.get(get.invoke(null, "UTF8_STRING"));
        x11.NET_SUPPORTING_WM_CHECK = (Long)atom.get(get.invoke(null, "_NET_SUPPORTING_WM_CHECK"));
        x11.NET_WM_NAME = (Long)atom.get(get.invoke(null, "_NET_WM_NAME"));
        x11.NET_WM_ALLOWED_ACTIONS = (Long)atom.get(get.invoke(null, "_NET_WM_ALLOWED_ACTIONS"));
        x11.NET_WM_STATE = (Long)atom.get(get.invoke(null, "_NET_WM_STATE"));
        x11.NET_WM_ACTION_FULLSCREEN = (Long)atom.get(get.invoke(null, "_NET_WM_ACTION_FULLSCREEN"));
        x11.NET_WM_STATE_FULLSCREEN = (Long)atom.get(get.invoke(null, "_NET_WM_STATE_FULLSCREEN"));

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

    @Nullable
    private Long getNetWmWindow() throws Exception {
      long rootWindow = getRootWindow(0);
      long[] values = getLongArrayProperty(rootWindow, NET_SUPPORTING_WM_CHECK, XA_WINDOW);
      return values != null && values.length > 0 ? values[0] : null;
    }

    @Nullable
    private long[] getLongArrayProperty(long window, long name, long type) throws Exception {
      return getWindowProperty(window, name, type, FORMAT_LONG);
    }

    @Nullable
    private String getUtfStringProperty(long window, long name) throws Exception {
      byte[] bytes = getWindowProperty(window, name, UTF8_STRING, FORMAT_BYTE);
      return bytes != null ? new String(bytes, CharsetToolkit.UTF8_CHARSET) : null;
    }

    @Nullable
    private <T> T getWindowProperty(long window, long name, long type, long expectedFormat) throws Exception {
      long data = unsafe.allocateMemory(64);
      awtLock.invoke(null);
      try {
        unsafe.setMemory(data, 64, (byte)0);

        int result = (Integer)XGetWindowProperty.invoke(
          null, display, window, name, 0L, 65535L, (long)False, type, data, data + 8, data + 16, data + 24, data + 32);
        if (result == 0) {
          int format = unsafe.getInt(data + 8);
          long pointer = SystemInfo.is64Bit ? unsafe.getLong(data + 32) : unsafe.getInt(data + 32);

          if (pointer != None && format == expectedFormat) {
            int length = SystemInfo.is64Bit ? (int)unsafe.getLong(data + 16) : unsafe.getInt(data + 16);
            if (format == FORMAT_BYTE) {
              byte[] bytes = new byte[length];
              for (int i = 0; i < length; i++) bytes[i] = unsafe.getByte(pointer + i);
              @SuppressWarnings("unchecked") T t = (T)bytes;
              return t;
            }
            else if (format == FORMAT_LONG) {
              long[] values = newLongArray(length);
              for (int i = 0; i < length; i++) {
                values[i] = SystemInfo.is64Bit ? unsafe.getLong(pointer + 8 * i) : unsafe.getInt(pointer + 4 * i);
              }
              @SuppressWarnings("unchecked") T t = (T)values;
              return t;
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
        if (!SystemInfo.is64Bit) {
          unsafe.putInt(event + 8, True);
          unsafe.putInt(event + 16, (int)window);
          unsafe.putInt(event + 20, (int)type);
          unsafe.putInt(event + 24, FORMAT_LONG);
          for (int i = 0; i < data.length; i++) {
            unsafe.putInt(event + 28 + 4 * i, (int)data[i]);
          }
        }
        else {
          unsafe.putInt(event + 16, True);
          unsafe.putLong(event + 32, window);
          unsafe.putLong(event + 40, NET_WM_STATE);
          unsafe.putInt(event + 48, FORMAT_LONG);
          for (int i = 0; i < data.length; i++) {
            unsafe.putLong(event + 56 + 8 * i, data[i]);
          }
        }

        XSendEvent.invoke(null, display, target, false, EVENT_MASK, event);
      }
      finally {
        awtUnlock.invoke(null);
        unsafe.freeMemory(event);
      }
    }
  }

  @Nullable private static final Xlib X11 = Xlib.getInstance();

  // WM detection and patching

  @Nullable
  public static String getWmName() {
    if (X11 == null) return null;

    try {
      Long netWmWindow = X11.getNetWmWindow();
      if (netWmWindow != null) {
        return X11.getUtfStringProperty(netWmWindow, X11.NET_WM_NAME);
      }
    }
    catch (Throwable t) {
      LOG.info("cannot get WM name", t);
    }

    return null;
  }

  @SuppressWarnings("SpellCheckingInspection")
  public static void patchDetectedWm(String wmName) {
    if (X11 == null || !Registry.is("ide.x11.override.wm")) return;

    try {
      if (wmName.startsWith("Mutter") || "Muffin".equals(wmName) || "GNOME Shell".equals(wmName)) {
        setWM("MUTTER_WM", "METACITY_WM");
      }
      else if ("Marco".equals(wmName)) {
        setWM("MARCO_WM", "METACITY_WM");
      }
      else if ("awesome".equals(wmName)) {
        String version = getAwesomeWMVersion();
        if (StringUtil.compareVersionNumbers(version, "3.5") >= 0) {
          setWM("SAWFISH_WM");
        }
        else if (version != null) {
          setWM("OTHER_NONREPARENTING_WM", "LG3D_WM");
        }
      }
    }
    catch (Throwable t) {
      LOG.warn(t);
    }
  }

  private static void setWM(String... wmConstants) throws Exception {
    Class<?> xwmClass = Class.forName("sun.awt.X11.XWM");
    Object xwm = method(xwmClass, "getWM").invoke(null);
    if (xwm != null) {
      for (String wmConstant : wmConstants) {
        try {
          Field wm = field(xwmClass, wmConstant);
          Object id = wm.get(null);
          if (id != null) {
            field(xwmClass, "awt_wmgr").set(null, id);
            field(xwmClass, "WMID").set(xwm, id);
            LOG.info("impersonated WM: " + wmConstant);
            break;
          }
        }
        catch (NoSuchFieldException ignore) { }
      }
    }
  }

  @Nullable
  private static String getAwesomeWMVersion() {
    try {
      String version = ExecUtil.execAndReadLine(new GeneralCommandLine("awesome", "--version"));
      if (version != null) {
        Matcher m = Pattern.compile("awesome v([0-9.]+)").matcher(version);
        if (m.find()) {
          return m.group(1);
        }
      }
    }
    catch (Throwable t) {
      LOG.warn(t);
    }
    return null;
  }

  // full-screen support

  public static boolean isFullScreenSupported() {
    if (X11 == null) return false;

    IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();
    if (frames.length == 0) return true;  // no frame to check the property so be optimistic here

    return frames[0] instanceof JFrame && hasWindowProperty((JFrame)frames[0], X11.NET_WM_ALLOWED_ACTIONS, X11.NET_WM_ACTION_FULLSCREEN);
  }

  public static boolean isInFullScreenMode(JFrame frame) {
    return X11 != null && hasWindowProperty(frame, X11.NET_WM_STATE, X11.NET_WM_STATE_FULLSCREEN);
  }

  private static boolean hasWindowProperty(JFrame frame, long name, long expected) {
    if (X11 == null) return false;
    try {
      @SuppressWarnings("deprecation") ComponentPeer peer = frame.getPeer();
      if (peer != null) {
        long window = (Long)X11.getWindow.invoke(peer);
        long[] values = X11.getLongArrayProperty(window, name, XA_ATOM);
        if (values != null) {
          for (long value : values) {
            if (value == expected) return true;
          }
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

    try {
      @SuppressWarnings("deprecation") ComponentPeer peer = frame.getPeer();
      if (peer == null) throw new IllegalStateException(frame + " has no peer");
      long window = (Long)X11.getWindow.invoke(peer);
      long screen = (Long)X11.getScreenNumber.invoke(peer);
      long rootWindow = X11.getRootWindow(screen);
      X11.sendClientMessage(rootWindow, window, X11.NET_WM_STATE, NET_WM_STATE_TOGGLE, X11.NET_WM_STATE_FULLSCREEN);
    }
    catch (Throwable t) {
      LOG.info("cannot toggle mode", t);
    }
  }

  // reflection utilities

  private static Method method(Class<?> aClass, String name, Class<?>... parameterTypes) throws Exception {
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

  private static Method method(Class<?> aClass, String name, int parameters) throws Exception {
    for (Method method : aClass.getDeclaredMethods()) {
      if (name.equals(method.getName()) && method.getParameterTypes().length == parameters) {
        method.setAccessible(true);
        return method;
      }
    }
    throw new NoSuchMethodException(name);
  }

  private static Field field(Class<?> aClass, String name) throws Exception {
    Field field = aClass.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}