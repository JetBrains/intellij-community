// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.accessibility;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.AccessibleAnnouncer;
import com.jetbrains.JBR;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import java.awt.*;

public final class AccessibleAnnouncerUtil {
  private static final AccessibleAnnouncer announcer = JBR.getAccessibleAnnouncer();

  private AccessibleAnnouncerUtil() {}

  /**
   * This method makes an announcement with screen reader
   *
   * @param a         announce owner
   * @param str       message for announcing
   * @param interruptCurrentOutput  output interruption
   */
  public static void announce(final @Nullable Accessible a, final String str, final boolean interruptCurrentOutput) {
    if (announcer == null) return;

    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    if ((focusManager == null) ||
        (focusManager.getActiveWindow() == null)) return;

    if (interruptCurrentOutput) {
      announcer.announce(a, str, AccessibleAnnouncer.ANNOUNCE_WITH_INTERRUPTING_CURRENT_OUTPUT);
      return;
    }

    announcer.announce(a, str, AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT);
  }

  public static boolean isAnnouncingAvailable() {
    return ScreenReader.isActive()
           && JBR.isAccessibleAnnouncerSupported()
           && Registry.is("ide.accessibility.announcing.notifications.available", false);
  }

  public static boolean isSafeAnnouncingAvailable() {
    // For now announcement is considered safe only on Mac due to freezes and crashes with JAWS announcements (IDEA-321176).
    // On Windows we can't announce in NVDA and not in JAWS, because there is currently no way to determine which screen reader is running.
    return SystemInfo.isMac && isAnnouncingAvailable();
  }
}
