// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.accessibility;

import com.jetbrains.JBR;
import com.jetbrains.AccessibleAnnouncer;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.Accessible;

final public class AccessibleAnnouncerUtil {
  final private static AccessibleAnnouncer announcer = JBR.getAccessibleAnnouncer();

  final public static int ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT = AccessibleAnnouncer.ANNOUNCE_WITHOUT_INTERRUPTING_CURRENT_OUTPUT;
  final public static int ANNOUNCE_WITH_INTERRUPTING_CURRENT_OUTPUT = AccessibleAnnouncer.ANNOUNCE_WITH_INTERRUPTING_CURRENT_OUTPUT;

  private AccessibleAnnouncerUtil() {}

  /**
   * This method make announce with screenreader
   *
   * @param a         announce ovner
   * @param str       message for announsing
   * @param priority  output interruption
   */
  public static void announce(@NotNull Accessible a, String str, int priority) {
    if (announcer != null) {
      announcer.announce(a, str, priority);
    }
  }
}
