// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.accessibility;

import com.jetbrains.AccessibleAnnouncer;
import com.jetbrains.JBR;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import java.awt.*;

public final class AccessibleAnnouncerUtil {
  private static final AccessibleAnnouncer announcer = JBR.getAccessibleAnnouncer();

  private AccessibleAnnouncerUtil() {}

  /**
   * This method make announce with screenreader
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
}
