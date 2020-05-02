// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.foundation;

import static com.intellij.ui.mac.foundation.Foundation.createSelector;
import static com.intellij.ui.mac.foundation.Foundation.executeOnMainThread;
import static com.intellij.ui.mac.foundation.Foundation.getObjcClass;
import static com.intellij.ui.mac.foundation.Foundation.invoke;
import static com.intellij.ui.mac.foundation.Foundation.nsString;
import static com.intellij.ui.mac.foundation.Foundation.toStringViaUTF8;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.sun.jna.Pointer;
import java.awt.AWTEvent;
import java.awt.ActiveEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.MenuComponent;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

/**
 * @author pegov
 */
public class MacUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.mac.foundation.MacUtil");
  public static final String MAC_NATIVE_WINDOW_SHOWING = "MAC_NATIVE_WINDOW_SHOWING";

  private MacUtil() {
  }

  @Nullable
  public static ID findWindowForTitle(@Nullable String title) {
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
        if (0 == window.intValue()) break;

        final ID windowTitle = invoke(window, "title");
        if (windowTitle != null && windowTitle.intValue() != 0) {
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

  public static synchronized void startModal(JComponent component, String key) {
    try {
      if (SwingUtilities.isEventDispatchThread()) {
        EventQueue theQueue = component.getToolkit().getSystemEventQueue();

        while (component.getClientProperty(key) == Boolean.TRUE) {
          AWTEvent event = theQueue.getNextEvent();
          Object source = event.getSource();
          if (event instanceof ActiveEvent) {
            ((ActiveEvent)event).dispatch();
          }
          else if (source instanceof Component) {
            ((Component)source).dispatchEvent(event);
          }
          else if (source instanceof MenuComponent) {
            ((MenuComponent)source).dispatchEvent(event);
          }
          else {
            LOG.debug("Unable to dispatch: " + event);
          }
        }
      }
      else {
        assert false: "Should be called from Event-Dispatch Thread only!";
        while (component.getClientProperty(key) == Boolean.TRUE) {
          // TODO:
          //wait();
        }
      }
    }
    catch (InterruptedException ignored) {
    }
  }

  public static synchronized void startModal(JComponent component) {
    startModal(component, MAC_NATIVE_WINDOW_SHOWING);
  }

  public static boolean isFullKeyboardAccessEnabled() {
    if (!SystemInfo.isMacOSSnowLeopard) return false;
    final AtomicBoolean result = new AtomicBoolean();
    executeOnMainThread(true, true,
                        () -> result.set(invoke(invoke("NSApplication", "sharedApplication"), "isFullKeyboardAccessEnabled").intValue() == 1));
    return result.get();
  }

  public static void adjustFocusTraversal(@NotNull Disposable disposable) {
    if (!SystemInfo.isMacOSSnowLeopard) return;
    final AWTEventListener listener = new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        if (event instanceof KeyEvent
            && ((KeyEvent)event).getKeyCode() == KeyEvent.VK_TAB
            && (!(event.getSource() instanceof JTextComponent))
            && (!(event.getSource() instanceof JList))
            && !isFullKeyboardAccessEnabled())
          ((KeyEvent)event).consume();
      }
    };
    Disposer.register(disposable, new Disposable() {
      @Override
      public void dispose() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
      }
    });
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.KEY_EVENT_MASK);
  }

  public static ID findWindowFromJavaWindow(final Window w) {
    ID windowId = null;
    if (Registry.is("skip.untitled.windows.for.mac.messages")) {
      try {
        Class <?> cWindowPeerClass  = AWTAccessor.getComponentAccessor().getPeer(w).getClass();
        Method getPlatformWindowMethod = cWindowPeerClass.getDeclaredMethod("getPlatformWindow");
        Object cPlatformWindow = getPlatformWindowMethod.invoke(AWTAccessor.getComponentAccessor().getPeer(w));
        Class <?> cPlatformWindowClass = cPlatformWindow.getClass();
        Method getNSWindowPtrMethod = cPlatformWindowClass.getDeclaredMethod("getNSWindowPtr");
        windowId = new ID((Long)getNSWindowPtrMethod.invoke(cPlatformWindow));
      }
      catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        LOG.debug(e);
      }
    }
    else {
      String foremostWindowTitle = getWindowTitle(w);
      windowId = findWindowForTitle(foremostWindowTitle);
    }
    return windowId;
  }

  @Nullable
  public static String getWindowTitle(Window documentRoot) {
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
  private static class NSActivityOptions {
    // Used for activities that require the computer to not idle sleep. This is included in NSActivityUserInitiated.
    private static final long idleSystemSleepDisabled = 1L << 20;

    // App is performing a user-requested action.
    private static final long userInitiated = 0x00FFFFFFL | idleSystemSleepDisabled;
    private static final long userInitiatedAllowingIdleSystemSleep = userInitiated & ~idleSystemSleepDisabled;

    // Used for activities that require the highest amount of timer and I/O precision available. Very few applications should need to use this constant.
    private static final long latencyCritical = 0xFF00000000L;
  }

  public interface Activity {
    /**
     * Ends activity, allowing macOS to trigger AppNap (idempotent).
     */
    void matrixHasYou();
  }

  private static final class ActivityImpl extends AtomicReference<ID> implements Activity {
    private static final ID processInfoCls = getObjcClass("NSProcessInfo");
    private static final Pointer processInfoSel = createSelector("processInfo");
    private static final Pointer beginActivityWithOptionsReasonSel = createSelector("beginActivityWithOptions:reason:");
    private static final Pointer endActivitySel = createSelector("endActivity:");
    private static final Pointer retainSel = createSelector("retain");
    private static final Pointer releaseSel = createSelector("release");

    private ActivityImpl(@NotNull Object reason) {
      super(begin(reason));
    }

    @Override
    public void matrixHasYou() { end(getAndSet(null)); }

    private static ID getProcessInfo() { return invoke(processInfoCls, processInfoSel); }

    private static ID begin(@NotNull Object reason) {
      // http://lists.apple.com/archives/java-dev/2014/Feb/msg00053.html
      // https://developer.apple.com/library/prerelease/ios/documentation/Cocoa/Reference/Foundation/Classes/NSProcessInfo_Class/index.html#//apple_ref/c/tdef/NSActivityOptions
      return invoke(invoke(getProcessInfo(), beginActivityWithOptionsReasonSel,
                           NSActivityOptions.userInitiatedAllowingIdleSystemSleep, nsString(reason.toString())),
                    retainSel);
    }

    private static void end(@Nullable ID activityToken) {
      if (activityToken == null) return;
      invoke(getProcessInfo(), endActivitySel, activityToken);
      invoke(activityToken, releaseSel);
    }
  }

  public static Activity wakeUpNeo(@NotNull Object reason) {
    return SystemInfo.isMacOSMavericks && Registry.is("idea.mac.prevent.app.nap") ? new ActivityImpl(reason) : null;
  }

  @NotNull
  public static Color colorFromNative(ID color) {
    final ID colorSpace = invoke("NSColorSpace", "genericRGBColorSpace");
    final ID colorInSpace = invoke(color, "colorUsingColorSpace:", colorSpace);
    final long red = invoke(colorInSpace, "redComponent").longValue();
    final long green = invoke(colorInSpace, "greenComponent").longValue();
    final long blue = invoke(colorInSpace, "blueComponent").longValue();
    final long alpha = invoke(colorInSpace, "alphaComponent").longValue();
    final double realAlpha = alpha != 0 && (int)((alpha >> 52) & 0x7ffL) == 0 ? 1.0 : Double.longBitsToDouble(alpha);
    //noinspection UseJBColor
    return new Color((float)Double.longBitsToDouble(red),
                     (float)Double.longBitsToDouble(green),
                     (float)Double.longBitsToDouble(blue),
                     (float)realAlpha);
  }
}
