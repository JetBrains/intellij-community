/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.util.*;
import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;

public abstract class PlatformScalingUtil {
  private static final Logger LOG = Logger.getInstance("#" + PlatformScalingUtil.class.getName());
  private static final PlatformScalingUtil myInstance = create();

  private ArrayList<PlatformScalingUtilListener> myListeners = new ArrayList<PlatformScalingUtilListener>();

  public static PlatformScalingUtil getInstance() {
    return myInstance;
  }

  private static PlatformScalingUtil create() {
    if (SystemProperties.has("hidpi") && !SystemProperties.is("hidpi")) {
      return new IdentityScalingUtil();
    }

    if (SystemInfo.isWindows) {
      if (WindowsPerMonitorScalingUtil.isSystemMultiMonitorAware() &&
          SystemProperties.getBooleanProperty("hidpi.windows.permonitor", true)) {
        return new WindowsPerMonitorScalingUtil();
      } else {
        return new WindowsLegacyScalingUtil();
      }
    }
    else if (SystemInfo.isMac) {
      return new MacScalingUtil();
    }
    else if (SystemInfo.isLinux) {
      return new LinuxScalingUtil();
    }
    else {
      return new IdentityScalingUtil();
    }
  }

  public void addListener(PlatformScalingUtilListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(PlatformScalingUtilListener listener) {
    myListeners.remove(listener);
  }

  public abstract boolean isMultiMonitorAware();

  public abstract float getSystemScaleFactor();

  public abstract float getScaleFactorForWindow(@NotNull Window window);

  public float getActiveScaleFactor() {
    return JBUI.scale(1.0f);
  }

  public void setActiveScaleFactor(float scaleFactor) {
    JBUI.setScaleFactor(scaleFactor);
  }

  public void setActiveScaleFactorFromFontSize(int size) {
    JBUI.setScaleFactor(size / UIUtil.DEF_SYSTEM_FONT_SIZE);
  }

  public abstract float normalizeScaleFactor(float scale);

  protected static float normalizeScaleFactorUtil(float scale) {
    // Valid scale factors are 1.0, 1.25, 1.50, etc. up to 4.0
    scale = ((int)(scale * 100.0f) / 25 * 25) / 100.0f; // round down to x.25
    scale = max(1.0f, scale); // lower limit
    scale = min(4.0f, scale); // upper limit
    return scale;
  }

  protected static float getScaleFactorFromSystemFont() {
    int size = -1;
    UIUtil.initSystemFontData();
    Pair<String, Integer> fdata = UIUtil.getSystemFontData();
    if (fdata != null) size = fdata.getSecond();
    if (size == -1) {
      size = JBUI.Fonts.label().getSize();
    }

    // 100% scaling factor is 12pt font size.
    return normalizeScaleFactorUtil(size / UIUtil.DEF_SYSTEM_FONT_SIZE);
  }

  protected void invokeScaleFactorChanged(final Window window) {
    EdtInvocationManager.getInstance().invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          float scaleFactor = getScaleFactorForWindow(window);
          fireScaleFactorChanged(window, scaleFactor);
        }
        catch (Throwable e) {
          LOG.warn("Error firing scale factor for window changed event", e);
        }
      }
    });
  }

  private void fireScaleFactorChanged(Window window, float newScaleFactor) {
    ScaleFactorChangedEvent event = new ScaleFactorChangedEvent(window, newScaleFactor);
    for (PlatformScalingUtilListener listener : myListeners) {
      try {
        listener.scaleFactorChanged(event);
      }
      catch (Throwable e) {
        LOG.warn("Error in scale factor for window changed event processor", e);
      }

    }
  }

  /**
   * Mac platform: no DPI scaling necessary, until we support moving frames
   * between retina and non-retina screens.
   */
  private static class MacScalingUtil extends IdentityScalingUtil {
  }

  /**
   * Linux platform: System DPI aware
   */
  private static class LinuxScalingUtil extends PlatformScalingUtil {
    @Override
    public boolean isMultiMonitorAware() {
      return false;
    }

    @Override
    public float getSystemScaleFactor() {
      final int dpi = getSystemDPI();
      float s = normalizeScaleFactor(dpi / 96.0f);
      LOG.info("UI scale factor: " + s);
      return s;
    }

    @Override
    public float getScaleFactorForWindow(@NotNull Window window) {
      return getSystemScaleFactor();
    }

    @Override
    public float normalizeScaleFactor(float scale) {
      scale = normalizeScaleFactorUtil(scale);
      if (scale == 1.25f) {
        //Default UI font size for Unity and Gnome is 15. Scaling factor 1.25f works badly on Linux
        scale = 1f;
      }
      return scale;
    }

    private static int getSystemDPI() {
      try {
        return Toolkit.getDefaultToolkit().getScreenResolution();
      }
      catch (HeadlessException e) {
        return 96;
      }
    }
  }

  /**
   * Platform neutral, when DPI scaling disabled
   */
  private static class IdentityScalingUtil extends PlatformScalingUtil {
    @Override
    public boolean isMultiMonitorAware() {
      return false;
    }

    @Override
    public float getSystemScaleFactor() {
      return 1.0f;
    }

    @Override
    public float getScaleFactorForWindow(@NotNull Window window) {
      return getSystemScaleFactor();
    }

    @Override
    public float normalizeScaleFactor(float scale) {
      return getSystemScaleFactor();
    }
  }

  /**
   * Windows platform, when process is system DPI aware, but *not* per Monitor DPI aware
   */
  private static class WindowsLegacyScalingUtil extends PlatformScalingUtil {
    @Override
    public boolean isMultiMonitorAware() {
      return false;
    }

    @Override
    public float getSystemScaleFactor() {
      return getScaleFactorFromSystemFont();
    }

    @Override
    public float getScaleFactorForWindow(@NotNull Window window) {
      return getSystemScaleFactor();
    }

    @Override
    public float normalizeScaleFactor(float scale) {
      return getSystemScaleFactor();
    }
  }

  /**
   * Windows platform, when process is per Monitor DPI aware
   */
  private static class WindowsPerMonitorScalingUtil extends PlatformScalingUtil {
    public WindowsPerMonitorScalingUtil() {
      if (isMultiMonitorAware()) {
        Toolkit.getDefaultToolkit().addAWTEventListener(new WindowCreationMonitor(), AWTEvent.WINDOW_EVENT_MASK);
      }
    }

    @Override
    public boolean isMultiMonitorAware() {
      return isSystemMultiMonitorAware();
    }

    @Override
    public float getSystemScaleFactor() {
      return getScaleFactorFromSystemFont();
    }

    @Override
    public float getScaleFactorForWindow(@NotNull Window window) {
      try {
        WinDef.HWND wnd = new WinDef.HWND(Native.getWindowPointer(window));
        HMONITOR hmonitor = User32Extended.INSTANCE.MonitorFromWindow(wnd, User32Extended.MONITOR_DEFAULTTONULL);
        if (hmonitor == null) {
          throw new IllegalArgumentException("Invalid window handle or window is not visible on any monitor");
        }
        IntByReference dpix = new IntByReference();
        IntByReference dpiy = new IntByReference();
        WinNT.HRESULT hr = Shcore.INSTANCE.GetDpiForMonitor(hmonitor, Shcore.MDT_EFFECTIVE_DPI, dpix, dpiy);
        if (W32Errors.FAILED(hr)) {
          throw new Win32Exception(hr);
        }
        return normalizeScaleFactorUtil(dpix.getValue() / 96.0f);
      } catch (Throwable e) {
        LOG.warn("Unexpected error retrieving scale factor for window, assuming 1.0f", e);
        return getSystemScaleFactor();
      }
    }

    @Override
    public float normalizeScaleFactor(float scale) {
      return normalizeScaleFactorUtil(scale);
    }

    public static boolean isSystemMultiMonitorAware() {
      if (!SystemInfo.isWin8OrNewer) {
        return false;
      }

      try {
        IntByReference dpiFlags = new IntByReference();
        WinNT.HRESULT hr = Shcore.INSTANCE.GetProcessDpiAwareness(new WinNT.HANDLE(Pointer.NULL), dpiFlags);
        if (W32Errors.FAILED(hr)) {
          throw new Win32Exception(hr);
        }
        return dpiFlags.getValue() == Shcore.PROCESS_PER_MONITOR_DPI_AWARE;
      }
      catch (Win32Exception e) {
        LOG.warn("Error retrieving process DPI awareness -- assuming process is not multi-monitor DPI aware", e);
        return false;
      }
      catch (UnsatisfiedLinkError e) {
        LOG.info("Windows OS does not support DPI awareness API -- assuming process is not multi-monitor DPI aware", e);
        return false;
      }
      catch (Throwable e) {
        LOG.warn("Unexpected error retrieving DPI awareness -- assuming process is not multi-monitor DPI aware", e);
        return false;
      }
    }

    /**
     * Extends User32 to expose new Windows 8.1+ entry points.
     */
    @SuppressWarnings({"SpellCheckingInspection", "unused"})
    private interface User32Extended extends User32 {
      User32Extended INSTANCE = (User32Extended)Native.loadLibrary("user32", User32Extended.class, W32APIOptions.DEFAULT_OPTIONS);

      int SWP_NOZORDER = 0x0004;
      int SWP_NOACTIVATE = 0x0010;

      int WM_DPICHANGED = 0x02E0;

      /**
       * Sets a new address for the window procedure (value to be set).
       */
      int GWLP_WNDPROC = -4;

      int SetWindowLong(HWND hWnd, int nIndex, WinUser.WindowProc wndProc);

      LONG_PTR SetWindowLongPtr(HWND hWnd, int nIndex, WinUser.WindowProc wndProc);

      LRESULT CallWindowProc(LONG_PTR proc, HWND hWnd, int uMsg, WPARAM uParam, WinDef.LPARAM lParam);

      /**
       * Flag for {@link #MonitorFromWindow}
       */
      int MONITOR_DEFAULTTONULL = 0;
      /**
       * Flag for {@link #MonitorFromWindow}
       */
      int MONITOR_DEFAULTTOPRIMARY = 1;
      /**
       * Flag for {@link #MonitorFromWindow}
       */
      int MONITOR_DEFAULTTONEAREST = 2;

      /**
       * The MonitorFromWindow function retrieves a handle to the display monitor that has the largest area of intersection
       * with the bounding rectangle of a specified window.
       *
       * See <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/dd145064(v=vs.85).aspx">https://msdn.microsoft.com/en-us/library/windows/desktop/dd145064(v=vs.85).aspx</a>
       */
      HMONITOR MonitorFromWindow(HWND hwnd, int dwFlags);
    }

    /**
     * SHCORE.DLL exposes Windows 8.1+ entry points related to multi-monitor DPI support.
     */
    @SuppressWarnings({"SpellCheckingInspection", "unused"})
    private interface Shcore extends StdCallLibrary, WinUser, WinNT {
      Shcore INSTANCE = (Shcore)Native.loadLibrary("shcore", Shcore.class, W32APIOptions.DEFAULT_OPTIONS);

      /**
       * Flag for {@link #GetDpiForMonitor}
       */
      int MDT_EFFECTIVE_DPI = 0;
      /**
       * Flag for {@link #GetDpiForMonitor}
       */
      int MDT_ANGULAR_DPI = 1;
      /**
       * Flag for {@link #GetDpiForMonitor}
       */
      int MDT_RAW_DPI = 2;

      /**
       * See <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/dn280510(v=vs.85).aspx">https://msdn.microsoft.com/en-us/library/windows/desktop/dn280510(v=vs.85).aspx</a>
       */
      WinNT.HRESULT GetDpiForMonitor(HMONITOR hmonitor, int dpiType, IntByReference dpiX, IntByReference dpiY);

      /**
       * Flag for {@link #GetProcessDpiAwareness}
       */
      int PROCESS_DPI_UNAWARE = 0;
      /**
       * Flag for {@link #GetProcessDpiAwareness}
       */
      int PROCESS_SYSTEM_DPI_AWARE = 1;
      /**
       * Flag for {@link #GetProcessDpiAwareness}
       */
      int PROCESS_PER_MONITOR_DPI_AWARE = 2;

      /**
       * See <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/dn302113(v=vs.85).aspx">https://msdn.microsoft.com/en-us/library/windows/desktop/dn302113(v=vs.85).aspx</a>
       */
      WinNT.HRESULT GetProcessDpiAwareness(HANDLE hprocess, IntByReference value);

      /**
       * See <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/dn302113(v=vs.85).aspx">https://msdn.microsoft.com/en-us/library/windows/desktop/dn302113(v=vs.85).aspx</a>
       */
      WinNT.HRESULT SetProcessDpiAwareness(int value);
    }

    /**
     * The Class RECT.
     */
    public static class RECT extends Structure {
      public int left;
      public int top;
      public int right;
      public int bottom;

      public RECT() {
      }

      public RECT(Pointer p) {
        super(p);
        read();
      }

      @Override
      protected List getFieldOrder() {
        return Arrays.asList("left", "top", "right", "bottom");
      }

      public Rectangle toRectangle() {
        return new Rectangle(left, top, right - left, bottom - top);
      }

      @Override
      public String toString() {
        return "[(" + left + "," + top + ")(" + right + "," + bottom + ")]";
      }
    }

    /**
     * Note: This class needs to be public, as instances are created by the JNA interop layer.
     */
    @SuppressWarnings({"SpellCheckingInspection", "unused"})
    public static class HMONITOR extends WinNT.HANDLE {
      public HMONITOR() {
        super();
      }

      public HMONITOR(Pointer p) {
        super(p);
      }
    }

    private class WindowCreationMonitor implements AWTEventListener {
      private Map<HWND, Window> myWindows = new HashMap<HWND, Window>();
      private Map<Window, WindowSubclass> mySubclasses = new IdentityHashMap<Window, WindowSubclass>();

      @Override
      public void eventDispatched(AWTEvent event) {
        switch (event.getID()) {
          case WindowEvent.WINDOW_OPENED:
            handleWindowOpened(event);
            break;
          case WindowEvent.WINDOW_CLOSED:
            handleWindowClosed(event);
            break;
        }
      }

      private void handleWindowOpened(AWTEvent event) {
        if (!(event.getSource() instanceof Window)) {
          return;
        }
        Window window = (Window)event.getSource();
        registerWindowSubclass(window);

        // The current method is invoked a little bit after the window creation,
        // meaning the initial WM_DPICHANGED message sent by Windows has been
        // missed. We workaround that issue by firing a event here.
        if (window instanceof JFrame) {
          invokeScaleFactorChanged(window);
        }
      }

      /**
       * Subclass the Window by adding a WndProc callback, keeping the
       * callback alive in a map so that it does not get garbage collected.
       */
      private void registerWindowSubclass(Window window) {
        HWND hWnd = new HWND(Native.getWindowPointer(window));
        BaseTSD.LONG_PTR previousProcPtr;
        if (Pointer.SIZE == 8) {
          previousProcPtr = User32Extended.INSTANCE.GetWindowLongPtr(hWnd, User32Extended.GWLP_WNDPROC);
        }
        else {
          previousProcPtr = new BaseTSD.LONG_PTR(User32Extended.INSTANCE.GetWindowLong(hWnd, User32Extended.GWLP_WNDPROC));
        }

        WinUser.WindowProc listener = new MyWindowProc(previousProcPtr);
        myWindows.put(hWnd, window);
        mySubclasses.put(window, new WindowSubclass(hWnd, previousProcPtr, listener));

        // Note: Error handling: See https://msdn.microsoft.com/en-us/library/windows/desktop/ms644898(v=vs.85).aspx:
        // "If the previous value is zero and the function succeeds, the return value is zero, but the function does not
        //  clear the last error information. To determine success or failure, clear the last error information by calling
        //  SetLastError with 0, then call SetWindowLongPtr. Function failure will be indicated by a return value of zero and
        //  a GetLastError result that is nonzero."
        Kernel32.INSTANCE.SetLastError(0);
        if (Pointer.SIZE == 8) {
          previousProcPtr = User32Extended.INSTANCE.SetWindowLongPtr(hWnd, User32Extended.GWLP_WNDPROC, listener);
        }
        else {
          previousProcPtr = new BaseTSD.LONG_PTR(User32Extended.INSTANCE.SetWindowLong(hWnd, User32Extended.GWLP_WNDPROC, listener));
        }
        int lastError = Kernel32.INSTANCE.GetLastError();
        if (previousProcPtr.longValue() == 0 && lastError != 0) {
          String message;
          try {
            message = Kernel32Util.formatMessage(lastError);
          } catch(Throwable e) {
            message = "Unknown error";
          }
          LOG.warn("Error subclassing window, WM_DPICHANGED messages will not be intercepted: " + message);
        }
      }

      private void handleWindowClosed(AWTEvent event) {
        if (!(event.getSource() instanceof Window)) {
          return;
        }
        Window window = (Window)event.getSource();

        // Note: We don't need (and cannot) remove ourselves as the window subclass
        // because the window has already been destroyed at this point.
        WindowSubclass subclass = mySubclasses.get(window);
        if (subclass != null) {
          mySubclasses.remove(window);
          myWindows.remove(subclass.myHWnd);
        }
      }

      private class WindowSubclass {
        public WinDef.HWND myHWnd;
        public BaseTSD.LONG_PTR myPreviousProc;
        public final WinUser.WindowProc myProc;

        public WindowSubclass(WinDef.HWND hWnd, BaseTSD.LONG_PTR previousProcPtr, WinUser.WindowProc listener) {
          myHWnd = hWnd;
          myPreviousProc = previousProcPtr;
          myProc = listener;
        }
      }

      private class MyWindowProc implements WinUser.WindowProc {
        private final BaseTSD.LONG_PTR myPreviousProcPtr;

        public MyWindowProc(BaseTSD.LONG_PTR previousProcPtr) {
          myPreviousProcPtr = previousProcPtr;
        }

        @Override
        public WinDef.LRESULT callback(WinDef.HWND hWnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
          if (uMsg == User32Extended.WM_DPICHANGED) {
            try {
              HandleDpiChanged(hWnd, wParam, lParam);
              return new WinDef.LRESULT(0); // Handled
            }
            catch (Throwable e) {
              LOG.warn("Error handling WM_DPICHANGED message", e);
            }
          }
          return User32Extended.INSTANCE.CallWindowProc(myPreviousProcPtr, hWnd, uMsg, wParam, lParam);
        }

        @SuppressWarnings("UnusedParameters")
        private void HandleDpiChanged(final WinDef.HWND hWnd, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
          final Rectangle rect = new RECT(new Pointer(lParam.longValue())).toRectangle();
          final int flags = User32Extended.SWP_NOZORDER | User32Extended.SWP_NOACTIVATE;
          User32.INSTANCE.SetWindowPos(hWnd, null, rect.x, rect.y, rect.width, rect.height, flags);

          final Window window = myWindows.get(hWnd);
          if (window == null) {
            return;
          }
          invokeScaleFactorChanged(window);
        }
      }
    }
  }
}
