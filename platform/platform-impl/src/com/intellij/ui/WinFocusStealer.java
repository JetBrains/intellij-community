// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.User32Ex;
import com.intellij.util.ui.UIUtil;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinReg;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;

/**
 * This class allows to disable Windows focus stealing prevention mechanism. The changes have a system-wide effect,
 * i.e. focus stealing can only be enabled for all applications in the system, not just for IDE process.
 * <p>
 * The desired effect is achieved by setting system-wide 'foreground lock timeout' value to zero. This is a duration that should pass after
 * user input in one window before another window is allowed to steal focus.
 */
public final class WinFocusStealer implements AWTEventListener {
  private static final Logger LOG = Logger.getInstance(WinFocusStealer.class);
  private static final int DEFAULT_TIMEOUT_MS = 200000; // default registry value on Windows 10 as of time this code is written
  private final boolean myEnabled;
  private Boolean myUpdateScheduled;

  private WinFocusStealer() {
    if (JnaLoader.isLoaded()) {
      myEnabled = true;
      doUpdate(false); // make sure to restore the default state if IDE crashed after enabling focus stealing
      SwingUtilities.invokeLater(() -> Toolkit.getDefaultToolkit().addAWTEventListener(this,
                                                                                       AWTEvent.KEY_EVENT_MASK |
                                                                                       AWTEvent.MOUSE_EVENT_MASK |
                                                                                       AWTEvent.MOUSE_MOTION_EVENT_MASK |
                                                                                       AWTEvent.MOUSE_WHEEL_EVENT_MASK |
                                                                                       AWTEvent.INPUT_METHOD_EVENT_MASK));
    }
    else {
      myEnabled = false;
      LOG.info("JNA isn't available, focus stealing logic is disabled");
    }
  }

  /**
   * Enables or disables focus stealing globally in the system. This can only come to effect if the IDE window is currently active.
   * If this is not the case, at the time this method is invoked, the actual change will be performed when next user input event is
   * received.
   * <p>
   * With focus stealing enabled, {@link Window#toFront()} method should bring the target IDE window into foreground, even if user is
   * currently working with another application. For it to work reliably, {@code toFront} method shouldn't be called immediately after
   * other window- or focus-related API methods (in particular, calling {@code toFront} right after {@link Component#requestFocus()} method
   * is known not to work as expected). Required delay is empirically estimated to be around 20 ms.
   * (The hypothesis is that timestamps used for checking timeout are determined with a certain inaccuracy, caused by timer resolution,
   * and {@code BringWindowToTop} system function, called by {@code requestFocus()}, is treated as a user input, so
   * {@code SetForegroundWindow} call fails to focus a background window, even with foreground lock timeout is set to 0, if it's preceded
   * by {@code BringWindowToTop} call)
   */
  public static void setFocusStealingEnabled(boolean value) {
    WinFocusStealer stealer = ApplicationManager.getApplication().getService(WinFocusStealer.class);
    if (stealer != null && stealer.myEnabled) stealer.update(value); // the service is null on non-Windows machines
  }

  private void update(boolean enabled) {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Setting focus stealing status to '" + enabled + "'");
      }
      doUpdate(enabled);
    });
  }

  private void doUpdate(boolean enabled) {
    myUpdateScheduled = null;
    int targetTimeout = 0;
    if (!enabled) {
      targetTimeout = readRegistryTimeoutValue();
    }
    int currentTimeout = readCurrentTimeoutValue();
    if (currentTimeout != targetTimeout) {
      if (!writeCurrentTimeoutValue(targetTimeout)) {
        myUpdateScheduled = enabled;
      }
    }
  }

  private static int readRegistryTimeoutValue() {
    int targetTimeout = DEFAULT_TIMEOUT_MS;
    try {
      targetTimeout = Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, "Control Panel\\Desktop", "ForegroundLockTimeout");
      if (LOG.isDebugEnabled()) {
        LOG.debug("ForegroundLockTimeout read from registry: " + targetTimeout);
      }
    }
    catch (Exception e) {
      LOG.warn("Error getting ForegroundLockTimeout from registry, assuming default value of " + targetTimeout, e);
    }
    return targetTimeout;
  }

  private static int readCurrentTimeoutValue() {
    int currentTimeout = DEFAULT_TIMEOUT_MS;
    try {
      WinDef.UINTByReference result = new WinDef.UINTByReference();
      if (User32Ex.INSTANCE.SystemParametersInfo(new WinDef.UINT(0x2000 /* SPI_GETFOREGROUNDLOCKTIMEOUT */),
                                             new WinDef.UINT(), result, new WinDef.UINT())) {
        currentTimeout = result.getValue().intValue();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Current foreground lock timeout: " + currentTimeout);
        }
      }
      else {
        LOG.warn("Failed to get foreground lock timeout using SystemParametersInfo, assuming default value of " + currentTimeout);
      }
    }
    catch (Exception e) {
      LOG.warn("Error getting foreground lock timeout using SystemParametersInfo, assuming default value of " + currentTimeout, e);
    }
    return currentTimeout;
  }

  private static boolean writeCurrentTimeoutValue(int value) {
    try {
      if (User32Ex.INSTANCE.SystemParametersInfo(new WinDef.UINT(0x2001 /* SPI_SETFOREGROUNDLOCKTIMEOUT */),
                                                 new WinDef.UINT(), new WinDef.UINT(value), new WinDef.UINT())) {
        LOG.info("Foreground lock timeout set to " + value);
        return true;
      }
      else {
        LOG.debug("Failed to set foreground lock timeout to " + value + ", will retry on next window activation");
        return false;
      }
    }
    catch (Exception e) {
      LOG.warn("Error setting foreground lock timeout using SystemParametersInfo", e);
    }
    return true; // do not retry on exception
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    if (myUpdateScheduled != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Detected user interaction, trying to update focus stealing status to '" + myUpdateScheduled + "'");
      }
      doUpdate(myUpdateScheduled);
    }
  }
}
