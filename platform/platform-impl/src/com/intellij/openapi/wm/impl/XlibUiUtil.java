/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import sun.misc.Unsafe;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class XlibUiUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.wm.impl.XlibUiUtil");

  private static final int True = 1;
  private static final int False = 0;
  private static final int XA_ATOM = 4;
  private static final int CLIENT_MESSAGE = 33;
  private static final int LONG_FORMAT = 32;
  private static final long EVENT_MASK = (3l << 19);
  private static final long NET_WM_STATE_TOGGLE = 2;

  private static final String NET_WM_ALLOWED_ACTIONS = "_NET_WM_ALLOWED_ACTIONS";
  private static final String NET_WM_STATE = "_NET_WM_STATE";
  @SuppressWarnings("SpellCheckingInspection") private static final String NET_WM_ACTION_FULL_SCREEN = "_NET_WM_ACTION_FULLSCREEN";
  @SuppressWarnings("SpellCheckingInspection") private static final String NET_WM_STATE_FULL_SCREEN = "_NET_WM_STATE_FULLSCREEN";

  private static Unsafe unsafe;
  private static Method XGetWindowProperty;
  private static Method XFree;
  private static Method RootWindow;
  private static Method XSendEvent;
  private static Method getWindow;
  private static Method getScreenNumber;
  private static Method awtLock;
  private static Method awtUnlock;
  private static long display;
  private static long wmAllowedActionsAtom;
  private static long wmStateAtom;
  private static long wmFullScreenActionAtom;
  private static long wmFullScreenStateAtom;
  private static final boolean initialized;

  static {
    boolean ok = false;
    if (SystemInfo.isXWindow && Registry.is("ide.unix.full.screen.enabled")) {
      try {
        Class<?> XlibWrapper = Class.forName("sun.awt.X11.XlibWrapper");
        unsafe = (Unsafe)field(XlibWrapper, "unsafe").get(null);

        XGetWindowProperty = method(XlibWrapper, "XGetWindowProperty", 12);
        XFree = method(XlibWrapper, "XFree", 1);
        RootWindow = method(XlibWrapper, "RootWindow", 2);
        XSendEvent = method(XlibWrapper, "XSendEvent", 5);

        Class<?> XBaseWindow = Class.forName("sun.awt.X11.XBaseWindow");
        getWindow = method(XBaseWindow, "getWindow");
        getScreenNumber = method(XBaseWindow, "getScreenNumber");

        Class<?> XToolkit = Toolkit.getDefaultToolkit().getClass();
        display = (Long)method(XToolkit, "getDisplay").invoke(null);
        awtLock = method(XToolkit, "awtLock");
        awtUnlock = method(XToolkit, "awtUnlock");

        Class<?> XAtom = Class.forName("sun.awt.X11.XAtom");
        Method get = method(XAtom, "get", String.class);
        Field atom = field(XAtom, "atom");
        wmAllowedActionsAtom = (Long)atom.get(get.invoke(null, NET_WM_ALLOWED_ACTIONS));
        wmStateAtom = (Long)atom.get(get.invoke(null, NET_WM_STATE));
        wmFullScreenActionAtom = (Long)atom.get(get.invoke(null, NET_WM_ACTION_FULL_SCREEN));
        wmFullScreenStateAtom = (Long)atom.get(get.invoke(null, NET_WM_STATE_FULL_SCREEN));

        ok = true;
      }
      catch (Throwable t) {
        LOG.info("cannot initialize", t);
      }
    }
    initialized = ok;
  }

  public static boolean isFullScreenSupported() {
    if (!initialized) return false;

    IdeFrame[] frames = WindowManager.getInstance().getAllProjectFrames();
    if (frames.length == 0) return true;  // no frame to check the property so be optimistic here

    return frames[0] instanceof JFrame && hasWindowProperty((JFrame)frames[0], wmAllowedActionsAtom, wmFullScreenActionAtom);
  }

  public static boolean isInFullScreenMode(JFrame frame) {
    return hasWindowProperty(frame, wmStateAtom, wmFullScreenStateAtom);
  }

  private static boolean hasWindowProperty(JFrame frame, long propertyName, long propertyValue) {
    if (!initialized) return false;

    boolean hasProperty = false;
    try {
      long data = unsafe.allocateMemory(64);
      awtLock.invoke(null);
      try {
        unsafe.setMemory(data, 64, (byte)0);

        @SuppressWarnings("deprecation") ComponentPeer peer = frame.getPeer();
        long window = (Long)getWindow.invoke(peer);

        int result = (Integer)XGetWindowProperty.invoke(null,
          display, window, propertyName, 0, 1024, False, XA_ATOM, data, data + 8, data + 16, data + 24, data + 32);
        if (result == 0) {
          int format = unsafe.getInt(data + 8);
          long atoms = SystemInfo.is64Bit ? unsafe.getLong(data + 32) : unsafe.getInt(data + 32);

          if (format == LONG_FORMAT) {
            long items = SystemInfo.is64Bit ? unsafe.getLong(data + 16) : unsafe.getInt(data + 16);
            for (int i = 0; i < items; i++) {
              long atom = SystemInfo.is64Bit ? unsafe.getLong(atoms + 8 * i) : unsafe.getInt(atoms + 4 * i);
              if (atom == propertyValue) {
                hasProperty = true;
                break;
              }
            }
          }

          XFree.invoke(null, atoms);
        }
      }
      finally {
        awtUnlock.invoke(null);
        unsafe.freeMemory(data);
      }
    }
    catch (Throwable t) {
      LOG.info("cannot check property", t);
    }

    return hasProperty;
  }

  public static void toggleFullScreenMode(JFrame frame) {
    if (!initialized) return;

    try {
      long event = unsafe.allocateMemory(128);
      awtLock.invoke(null);
      try {
        unsafe.setMemory(event, 128, (byte)0);

        @SuppressWarnings("deprecation") ComponentPeer peer = frame.getPeer();
        long window = (Long)getWindow.invoke(peer);
        long screen = (Long)getScreenNumber.invoke(peer);
        long rootWindow = (Long)RootWindow.invoke(null, display, screen);

        unsafe.putInt(event, CLIENT_MESSAGE);
        if (!SystemInfo.is64Bit) {
          unsafe.putInt(event + 8, True);
          unsafe.putInt(event + 16, (int)window);
          unsafe.putInt(event + 20, (int)wmStateAtom);
          unsafe.putInt(event + 24, LONG_FORMAT);
          unsafe.putInt(event + 28, (int)NET_WM_STATE_TOGGLE);
          unsafe.putInt(event + 32, (int)wmFullScreenStateAtom);
        }
        else {
          unsafe.putInt(event + 16, True);
          unsafe.putLong(event + 32, window);
          unsafe.putLong(event + 40, wmStateAtom);
          unsafe.putInt(event + 48, LONG_FORMAT);
          unsafe.putLong(event + 56, NET_WM_STATE_TOGGLE);
          unsafe.putLong(event + 64, wmFullScreenStateAtom);
        }

        XSendEvent.invoke(null, display, rootWindow, false, EVENT_MASK, event);
      }
      finally {
        awtUnlock.invoke(null);
        unsafe.freeMemory(event);
      }
    }
    catch (Throwable t) {
      LOG.info("cannot toggle mode", t);
    }
  }

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
