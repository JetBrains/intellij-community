// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.accessibility;

import com.jetbrains.JBR;
import com.jetbrains.AccessibleAnnouncer;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;

final public class AccessibleAnnouncerUtil {
  final private static AccessibleAnnouncer announcer = JBR.getAccessibleAnnouncer();

  private AccessibleAnnouncerUtil() {}

  /**
   * This method make announce with screenreader
   *
   * @param a         announce ovner
   * @param str       message for announsing
   * @param interruptCurrentOutput  output interruption
   */
  public static void announce(@NotNull final Accessible a, final String str, final boolean interruptCurrentOutput) {
    if (announcer != null) {
      if (interruptCurrentOutput) {
        announcer.announce(a, str, AccessibleAnnouncer.ANNOUNCE_WITH_INTERRUPTING_CURRENT_OUTPUT);
        return;
      }
      announcer.announce(a, str, AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT);
    }
  }
}
