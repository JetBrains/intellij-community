/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij;

import com.intellij.openapi.util.SystemInfo;

import java.awt.*;

@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor"})
public class Patches {
  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6322854.
   * java.lang.NullPointerException: Failed to retrieve atom name.
   */
  public static final boolean SUN_BUG_ID_6322854 = SystemInfo.isXWindow;

  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4893787.
   * BasicTreeUI.FocusHandler doesn't properly repaint JTree on focus changes.
   */
  public static final boolean SUN_BUG_ID_4893787 = true;

  /**
   * Minimizing and restoring application via View | Minimize leads to visual artifacts.
   */
  public static final boolean APPLE_BUG_ID_10514018 = SystemInfo.isMac && !SystemInfo.isJavaVersionAtLeast("1.6.0_31");

  /**
   * IBM java machine 1.4.2 crashes if debugger uses ObjectReference.disableCollection() and ObjectReference.enableCollection().
   */
  public static final boolean IBM_JDK_DISABLE_COLLECTION_BUG = "false".equalsIgnoreCase(System.getProperty("idea.debugger.keep.temp.objects"));

  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4818143.
   * The bug is marked as fixed but it actually isn't - {@link java.awt.datatransfer.Clipboard#getContents(Object)} call may hang
   * for up to 10 seconds if clipboard owner is not responding.
   */
  public static final boolean SLOW_GETTING_CLIPBOARD_CONTENTS = SystemInfo.isUnix;

  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6209673.
   * Huge int[] leak through VolatileImages cached in RepaintManager whenever screen configuration changes.
   * For instance screen saver activates or computer goes hibernate. The problem still exists in 1.6 when two (or more)
   * monitors exists
   */
  public static final boolean SUN_BUG_ID_6209673 = true;

  /**
   * Desktop API support on X Window is limited to GNOME (and even there it may work incorrectly).
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6486393.
   */
  public static final boolean SUN_BUG_ID_6486393 = SystemInfo.isXWindow;

  /**
   * Desktop API calls may crash on Windows.
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6457572.
   */
  public static final boolean SUN_BUG_ID_6457572 = SystemInfo.isWindows && !SystemInfo.isJavaVersionAtLeast("1.7");

  /**
   * Java 7 incorrectly calculates screen insets on multi-monitor X Window configurations.
   * https://bugs.openjdk.java.net/browse/JDK-8020443
   */
  public static final boolean SUN_BUG_ID_8020443 =
    SystemInfo.isXWindow && SystemInfo.isJavaVersionAtLeast("1.7") && !SystemInfo.isJavaVersionAtLeast("1.8.0_60");

  /**
   * On some WMs modal dialogs may show behind full screen window.
   * See http://bugs.sun.com/view_bug.do?bug_id=8013359.
   */
  public static final boolean SUN_BUG_ID_8013359 =
    SystemInfo.isXWindow && SystemInfo.isJavaVersionAtLeast("1.7") && !SystemInfo.isJavaVersionAtLeast("1.7.0.40");

  /**
   * No BindException when another program is using the port.
   * See https://bugs.openjdk.java.net/browse/JDK-7179799.
   */
  public static final boolean SUN_BUG_ID_7179799 = SystemInfo.isWindows && !SystemInfo.isJavaVersionAtLeast("1.8");

  /**
   * Frame size reverts meaning of maximized attribute if frame size close to display.
   * See http://bugs.openjdk.java.net/browse/JDK-8007219
   * Fixed in JDK 8.
   */
  public static final boolean JDK_BUG_ID_8007219 = SystemInfo.isMac
                                                   && SystemInfo.isJavaVersionAtLeast("1.7")
                                                   && !SystemInfo.isJavaVersionAtLeast("1.8");

  /**
   * Marker field to find all usages of the reflective access to JDK 7-specific methods
   * which need to be changed when migrated to JDK 7
   */
  public static final boolean USE_REFLECTION_TO_ACCESS_JDK7 = Boolean.valueOf(true);

  /**
   * Marker field to find all usages of the reflective access to JDK 7-specific methods
   * which need to be changed when migrated to JDK 8
   */
  public static final boolean USE_REFLECTION_TO_ACCESS_JDK8 = Boolean.valueOf(true);

  /**
   * AtomicIntegerFieldUpdater does not work when SecurityManager is installed
   * fixed in JDK8
   */
  public static final boolean JDK_BUG_ID_7103570 = true;

  /**
   * Support default methods in JDI
   * See <a href="https://bugs.openjdk.java.net/browse/JDK-8042123">JDK-8042123</a>
   */
  public static final boolean JDK_BUG_ID_8042123 = !SystemInfo.isJavaVersionAtLeast("1.8.0_40");

  /**
   * JDK on Mac detects font style for system fonts based only on their name (PostScript name).
   * This doesn't work for some fonts which don't use recognizable style suffixes in their names.
   * Corresponding JDK request for enhancement - <a href="https://bugs.openjdk.java.net/browse/JDK-8139151">JDK-8139151</a>.
   */
  public static final boolean JDK_MAC_FONT_STYLE_DETECTION_WORKAROUND = SystemInfo.isMac;

  /**
   * Older JDK versions could mistakenly use derived italics font, when genuine italics font was available in the system.
   * The issue was fixed in JDK 1.8.0_60 as part of <a href="https://bugs.openjdk.java.net/browse/JDK-8064833">JDK-8064833</a>.
   */
  public static final boolean JDK_MAC_FONT_STYLE_BUG = SystemInfo.isMac && !SystemInfo.isJavaVersionAtLeast("1.8.0_60");

  /**
   * On Mac OS font ligatures are not supported for natively loaded fonts, font needs to be loaded explicitly by JDK. 
   */
  public static final boolean JDK_BUG_ID_7162125 = SystemInfo.isMac && !SystemInfo.isJavaVersionAtLeast("1.9");

  /**
   * XToolkit.getScreenInsets() may be very slow.
   * See https://bugs.openjdk.java.net/browse/JDK-8004103.
   */
  public static boolean isJdkBugId8004103() {
    return SystemInfo.isXWindow && !GraphicsEnvironment.isHeadless() && SystemInfo.isJavaVersionAtLeast("1.7");
  }
}
