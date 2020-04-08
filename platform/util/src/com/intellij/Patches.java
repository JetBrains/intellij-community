// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.util.SystemInfo;

import java.awt.*;

@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor"})
public class Patches {
  /**
   * See https://bugs.openjdk.java.net/browse/JDK-6322854.
   * java.lang.NullPointerException: Failed to retrieve atom name.
   */
  public static final boolean SUN_BUG_ID_6322854 = SystemInfo.isXWindow;

  /**
   * IBM JVM 1.4.2 crashes if debugger uses ObjectReference.disableCollection() and ObjectReference.enableCollection().
   */
  public static final boolean IBM_JDK_DISABLE_COLLECTION_BUG = "false".equalsIgnoreCase(System.getProperty("idea.debugger.keep.temp.objects"));

  /**
   * See https://bugs.openjdk.java.net/browse/JDK-4818143.
   * The bug is marked as fixed but it actually isn't - {@link java.awt.datatransfer.Clipboard#getContents(Object)} call may hang
   * for up to 10 seconds if clipboard owner is not responding.
   */
  public static final boolean SLOW_GETTING_CLIPBOARD_CONTENTS = SystemInfo.isUnix;

  /**
   * A huge int[] leak through VolatileImages cached in RepaintManager whenever screen configuration changes.
   * For instance screen saver activates or computer goes hibernate. The problem still exists in 1.6 when two (or more)
   * monitors exists
   *
   * <p> http://bugs.openjdk.java.net/browse/JDK-6209673
   * <p> http://bugs.openjdk.java.net/browse/JDK-8041654
   */
  public static final boolean REPAINT_MANAGER_LEAK = !SystemInfo.isJavaVersionAtLeast(8, 0, 60);

  /**
   * Desktop API support on X Window is limited to GNOME (and even there it may work incorrectly).
   * See https://bugs.openjdk.java.net/browse/JDK-6486393.
   */
  public static final boolean SUN_BUG_ID_6486393 = SystemInfo.isXWindow;

  /**
   * Java 7 incorrectly calculates screen insets on multi-monitor X Window configurations.
   * See https://bugs.openjdk.java.net/browse/JDK-8020443.
   */
  public static final boolean SUN_BUG_ID_8020443 = SystemInfo.isXWindow && !SystemInfo.isJavaVersionAtLeast(8, 0, 60);

  /**
   * Support default methods in JDI.
   * See <a href="https://bugs.openjdk.java.net/browse/JDK-8042123">JDK-8042123</a>
   */
  public static final boolean JDK_BUG_ID_8042123 = !SystemInfo.isJavaVersionAtLeast(8, 0, 45);

  /**
   * Enable a workaround for JDK bug with leaking TargetVM.EventController, see IDEA-163334
   */
  public static final boolean JDK_BUG_EVENT_CONTROLLER_LEAK = !SystemInfo.isJetBrainsJvm;

  /**
   * NPE from com.sun.jdi.ReferenceType#constantPool()
   * See <a href="https://bugs.openjdk.java.net/browse/JDK-6822627">JDK-6822627</a>
   */
  public static final boolean JDK_BUG_ID_6822627 = !SystemInfo.isJetBrainsJvm;

  /**
   * Debugger hangs in trace mode with TRACE_SEND when method argument is a {@link com.sun.jdi.StringReference}
   */
  public static final boolean JDK_BUG_ID_21275177 = true;

  /**
   * Debugger hangs in trace mode with TRACE_SEND when method argument is a {@link com.sun.jdi.ThreadReference}
   */
  public static final boolean JDK_BUG_WITH_TRACE_SEND = true;

  /**
   * JDK on Mac detects font style for system fonts based only on their name (PostScript name).
   * This doesn't work for some fonts, which don't use recognizable style suffixes in their names.
   * Corresponding JDK request for enhancement - <a href="https://bugs.openjdk.java.net/browse/JDK-8139151">JDK-8139151</a>.
   */
  public static final boolean JDK_MAC_FONT_STYLE_DETECTION_WORKAROUND = SystemInfo.isMac;

  /**
   * Older JDK versions could mistakenly use derived italics font, when genuine italic font was available in the system.
   * The issue was fixed in JDK 1.8.0_60 as part of <a href="https://bugs.openjdk.java.net/browse/JDK-8064833">JDK-8064833</a>.
   */
  public static final boolean JDK_MAC_FONT_STYLE_BUG = SystemInfo.isMac && !SystemInfo.isJavaVersionAtLeast(8, 0, 60);

  /**
   * XToolkit.getScreenInsets() may be very slow.
   * See https://bugs.openjdk.java.net/browse/JDK-8170937.
   */
  public static boolean isJdkBugId8004103() {
    return SystemInfo.isXWindow && !GraphicsEnvironment.isHeadless();
  }

  /**
   * Some HTTP connections lock class loaders: https://bugs.openjdk.java.net/browse/JDK-8032832
   * The issue claims to be fixed in 8u20, but the fix just replaces one lock with another (on a context class loader).
   */
  public static final boolean JDK_BUG_ID_8032832 = true;

  /**
   * Since 8u102, AWT supports Shift-scroll on all platforms (before, it only worked on macOS).
   * Ultimately fixed by <a href="https://bugs.openjdk.java.net/browse/JDK-8147994">JDK-8147994</a>.
   */
  public static final boolean JDK_BUG_ID_8147994 = !(SystemInfo.isMac || SystemInfo.isJavaVersionAtLeast(8, 0, 102));

  /**
   * https://bugs.openjdk.java.net/browse/JDK-8220231
   */
  public static final boolean TEXT_LAYOUT_IS_SLOW = !SystemInfo.isJetBrainsJvm &&
                                                    !SystemInfo.isJavaVersionAtLeast(13) &&
                                                    (SystemInfo.isJavaVersionAtLeast(12) ||
                                                     !SystemInfo.isJavaVersionAtLeast(11, 0, 6));
}