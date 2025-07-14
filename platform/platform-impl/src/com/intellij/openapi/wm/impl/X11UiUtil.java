// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UnixDesktopEnv;
import com.sun.jna.Native;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;
import sun.misc.Unsafe;

import javax.swing.*;
import java.awt.*;
import java.awt.peer.ComponentPeer;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@ApiStatus.Internal
public final class X11UiUtil {
  private static final Logger LOG = Logger.getInstance(X11UiUtil.class);

  private static final int True = 1;
  private static final int False = 0;
  private static final long None = 0;
  private static final long XA_ATOM = 4;
  private static final long XA_WINDOW = 33;
  private static final long ANY_PROPERTY_TYPE = 0;
  private static final int CLIENT_MESSAGE = 33;
  private static final int FORMAT_BYTE = 8;
  private static final int FORMAT_LONG = 32;
  private static final long EVENT_MASK = (3L << 19);
  private static final long NET_WM_STATE_REMOVE = 0;
  private static final long NET_WM_STATE_ADD = 1;
  private static final long NET_WM_STATE_TOGGLE = 2;

  public static final String KDE_LAF_PROPERTY = "LookAndFeelPackage=";

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

  private static String GSETTINGS_COMMAND = "gsettings";

  private static final ConcurrentHashMap<String, Boolean> unsupportedCommands = new ConcurrentHashMap<>();

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
    private Method getChildWindows;
    private Method XGetWindowAttributes;

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
    private long _NET_WM_PID;

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
        x11.XGetWindowAttributes = method(XlibWrapper, "XGetWindowAttributes", 3);
        Class<?> XBaseWindow = Class.forName("sun.awt.X11.XBaseWindow");
        x11.getWindow = method(XBaseWindow, "getWindow");
        x11.getScreenNumber = method(XBaseWindow, "getScreenNumber");
        x11.display = (Long)method(toolkitClass, "getDisplay").invoke(null);
        x11.awtLock = method(toolkitClass, "awtLock");
        x11.awtUnlock = method(toolkitClass, "awtUnlock");

        Class<?> XlibUtil = Class.forName("sun.awt.X11.XlibUtil");
        x11.getChildWindows = method(XlibUtil, "getChildWindows", long.class);

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
        x11._NET_WM_PID = (Long)atom.get(get.invoke(null, "_NET_WM_PID"));

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

    private Long findProcessWindow(long window, long pid, int recursionLevel) throws InvocationTargetException, IllegalAccessException {
      if (recursionLevel > 100) {
        LOG.warn("Recursion level exceeded. Deep lying windows will be skipped");
        return null;
      }

      if (isProcessWindowOwner(window, pid) && isViewableWin(window)) {
        return window;
      }

      Set<Long> childWindows = getChildWindows(window);
      if (childWindows == null) return null;

      for (Long childWindow : childWindows) {
        var wnd = findProcessWindow(childWindow, pid, recursionLevel + 1);
        if (wnd != null) {
          return wnd;
        }
      }

      return null;
    }

    private static Boolean isViewableWin(long window) throws InvocationTargetException, IllegalAccessException {
      assert X11 != null;
      XWindowAttributesWrapper wrapper = null;
      try {
        X11.awtLock.invoke(null);
        wrapper = new XWindowAttributesWrapper(window);
        return wrapper.getMapState() == XWindowAttributesWrapper.MapState.IsViewable;
      }
      catch (Exception e) {
        LOG.error(e);
        return false;
      }
      finally {
        X11.awtUnlock.invoke(null);
        if (wrapper != null)
          wrapper.dispose();
      }
    }

    private boolean isProcessWindowOwner(Long window, long pid) {
      long[] value;
      try {
        value = getWindowProperty(window, _NET_WM_PID, ANY_PROPERTY_TYPE, FORMAT_LONG);
      }
      catch (Exception ex) {
        LOG.error("Exception on obtaingin \"_NET_WM_PID\" window property", ex);
        return false;
      }

      if (value == null) {
        LOG.trace("_NET_WM_PID property is not set for window " + window);
        return false;
      }

      var windowPid = value[0];
      return windowPid == pid;
    }

    public Set<Long> getChildWindows(Long window) {
      try {
        return (Set<Long>)getChildWindows.invoke(null, window);
      }
      catch (Exception e) {
        LOG.error("Can't get children for window: " + window, e);
      }
      return null;
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

  @RequiresBackgroundThread
  public static @Nullable String getTheme() {
    if (UnixDesktopEnv.CURRENT == UnixDesktopEnv.GNOME) {
      String result = exec("Cannot get gnome theme", GSETTINGS_COMMAND, "get", "org.gnome.desktop.interface", "gtk-theme");
      return trimQuotes(result);
    }

    if (UnixDesktopEnv.CURRENT == UnixDesktopEnv.KDE) {
      // https://github.com/shalva97/kde-configuration-files
      Path home = Path.of(System.getenv("HOME"), ".config/kdeglobals");
      List<String> matches = grepFile("Cannot get KDE theme", home,
                                      Pattern.compile("\\s*" + KDE_LAF_PROPERTY + ".*"));
      if (matches.size() != 1) {
        return null;
      }

      String result = matches.get(0).trim();
      if (result.startsWith(KDE_LAF_PROPERTY)) {
        return result.substring(KDE_LAF_PROPERTY.length()).trim();
      }
      return null;
    }

    return null;
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread
  public static @Nullable String getIconTheme() {
    String result = exec("Cannot get icon theme", GSETTINGS_COMMAND, "get", "org.gnome.desktop.interface", "icon-theme");
    return trimQuotes(result);
  }

  /**
   * Format like `icon:minimize,maximize,close` or `close:icon`
   */
  @RequiresBackgroundThread
  @ApiStatus.Internal
  public static @Nullable String getWindowButtonsConfig() {
    if (UnixDesktopEnv.CURRENT == UnixDesktopEnv.GNOME || UnixDesktopEnv.CURRENT == UnixDesktopEnv.KDE) {
      String execResult =
        exec("Cannot get gnome WM buttons layout", GSETTINGS_COMMAND, "get", "org.gnome.desktop.wm.preferences", "button-layout");
      return trimQuotes(execResult);
    }

    return null;
  }

  private static @Nullable String trimQuotes(@Nullable String s) {
    if (s == null || s.length() <= 1 || !s.startsWith("'") || !s.endsWith("'")) {
      return s;
    }

    return s.substring(1, s.length() - 1);
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

  @ApiStatus.Internal
  public static boolean activate(long windowId) {
    if (X11 == null) return false;
    if (FocusManagerImpl.FOCUS_REQUESTS_LOG.isDebugEnabled()) {
      FocusManagerImpl.FOCUS_REQUESTS_LOG.debug("_NET_ACTIVE_WINDOW", new Throwable());
    }
    try {
      var rootWindow = X11.getRootWindow(0);
      X11.sendClientMessage(rootWindow, windowId, X11.NET_ACTIVE_WINDOW);
      return true;
    }
    catch (Exception e) {
      LOG.error("Can not activate window:" + windowId, e);
      return false;
    }
  }

  @ApiStatus.Internal
  public static Long findVisibleWindowByPid(long pid) {
    if (X11 == null) return null;
    try {
      var rootWindow = X11.getRootWindow(0);
      return X11.findProcessWindow(rootWindow, pid, 0);
    }
    catch (Exception e) {
      return null;
    }
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

  private static @Nullable String exec(String errorMessage, String... command) {
    if (command.length == 0) {
      LOG.error(errorMessage, "No command provided");
      return null;
    }

    if (unsupportedCommands.containsKey(command[0])) {
      // Avoid running and logging unsupported commands
      return null;
    }

    try {
      Process process = new ProcessBuilder(command).start();
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        LOG.info(errorMessage + ": timeout");
        process.destroyForcibly();
        return null;
      }

      if (process.exitValue() != 0) {
        LOG.info(errorMessage + ": exit code " + process.exitValue());
        return null;
      }

      return FileUtil.loadTextAndClose(process.getInputStream()).trim();
    }
    catch (Exception e) {
      String exceptionMessage = e.getMessage();
      if (exceptionMessage.contains("No such file or directory")) {
        unsupportedCommands.put(command[0], true);

        LOG.info(errorMessage + ": " + exceptionMessage);
        LOG.trace(e);
      } else {
        LOG.info(errorMessage, e);
      }

      return null;
    }
  }

  private static List<String> grepFile(String errorMessage, Path file, Pattern pattern) {
    List<String> result = new ArrayList<>();
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      String line;

      while ((line = reader.readLine()) != null) {
        if (pattern.matcher(line).matches()) {
          result.add(line);
        }
      }
      return result;
    }
    catch (IOException e) {
      LOG.info(errorMessage, e);
      return Collections.emptyList();
    }
  }

  static final class XWindowAttributesWrapper implements Disposable {
    enum MapState {
      IsUnmapped,
      IsUnviewable,
      IsViewable,
    }

    MapState getMapState() throws InvocationTargetException, IllegalAccessException {
      return MapState.values()[(Integer)get_map_state.invoke(instance)];
    }

    XWindowAttributesWrapper(long windowId) throws Exception {
      assert X11 != null;

      instance = getXWindowAttributesCtor().newInstance();
      var ptrData = getPData.invoke(instance);

      var operationResult = (Integer)X11.XGetWindowAttributes.invoke(null, X11.display, windowId, ptrData);
      if (operationResult == 0) {
        throw new Exception("XGetWindowAttributes failed :" + operationResult);
      }
    }

    @Override
    public void dispose() {
      try {
        dispose.invoke(instance);
      }
      catch (Exception exception) {
        LOG.error("Exception on XWindowAttributes instance dispose", exception);
      }
    }

    private final Object instance;

    private static Constructor<?> ctor;
    private static Method get_map_state;
    private static Method getPData;
    private static Method dispose;

    private static Constructor<?> getXWindowAttributesCtor() throws Exception {
      if (ctor == null) {
        var clazz = Class.forName("sun.awt.X11.XWindowAttributes");
        ctor = clazz.getConstructor();
        get_map_state = method(clazz, "get_map_state");
        dispose = method(clazz, "dispose");
        getPData = method(clazz, "getPData");
      }

      return ctor;
    }
  }
}
