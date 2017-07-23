/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
   * See https://bugs.openjdk.java.net/browse/JDK-6322854.
   * java.lang.NullPointerException: Failed to retrieve atom name.
   */
  public static final boolean SUN_BUG_ID_6322854 = SystemInfo.isXWindow;

  /**
   * IBM java machine 1.4.2 crashes if debugger uses ObjectReference.disableCollection() and ObjectReference.enableCollection().
   */
  public static final boolean IBM_JDK_DISABLE_COLLECTION_BUG = "false".equalsIgnoreCase(System.getProperty("idea.debugger.keep.temp.objects"));

  /**
   * See https://bugs.openjdk.java.net/browse/JDK-4818143.
   * The bug is marked as fixed but it actually isn't - {@link java.awt.datatransfer.Clipboard#getContents(Object)} call may hang
   * for up to 10 seconds if clipboard owner is not responding.
   */
  public static final boolean SLOW_GETTING_CLIPBOARD_CONTENTS = SystemInfo.isUnix;

  /**
   * Huge int[] leak through VolatileImages cached in RepaintManager whenever screen configuration changes.
   * For instance screen saver activates or computer goes hibernate. The problem still exists in 1.6 when two (or more)
   * monitors exists
   *
   * <p> http://bugs.openjdk.java.net/browse/JDK-6209673
   * <p> http://bugs.openjdk.java.net/browse/JDK-8041654
   */
  public static final boolean REPAINT_MANAGER_LEAK = !SystemInfo.isJavaVersionAtLeast("1.8.0_60");

  /**
   * Desktop API support on X Window is limited to GNOME (and even there it may work incorrectly).
   * See https://bugs.openjdk.java.net/browse/JDK-6486393.
   */
  public static final boolean SUN_BUG_ID_6486393 = SystemInfo.isXWindow;

  /**
   * Java 7 incorrectly calculates screen insets on multi-monitor X Window configurations.
   * See https://bugs.openjdk.java.net/browse/JDK-8020443.
   */
  public static final boolean SUN_BUG_ID_8020443 = SystemInfo.isXWindow && !SystemInfo.isJavaVersionAtLeast("1.8.0_60");

  /**
   * Marker field to find all usages of the reflective access to JDK 7-specific methods
   * which need to be changed when migrated to JDK 7
   */
  public static final boolean USE_REFLECTION_TO_ACCESS_JDK7 = Boolean.TRUE; // non-compile-const to trick "Constant expression is always true" inspection

  /**
   * Marker field to find all usages of the reflective access to JDK 7-specific methods
   * which need to be changed when migrated to JDK 8
   */
  public static final boolean USE_REFLECTION_TO_ACCESS_JDK8 = Boolean.TRUE; // non-compile-const to trick "Constant expression is always true" inspection

  /**
   * Support default methods in JDI
   * See <a href="https://bugs.openjdk.java.net/browse/JDK-8042123">JDK-8042123</a>
   */
  public static final boolean JDK_BUG_ID_8042123 = !SystemInfo.isJavaVersionAtLeast("1.8.0_45");

  /**
   * Enable workaround for jdk bug with leaking TargetVM.EventController, see IDEA-163334
   */
  public static final boolean JDK_BUG_EVENT_CONTROLLER_LEAK = true;

  /**
   * NPE from com.sun.jdi.ReferenceType#constantPool()
   * See <a href="https://bugs.openjdk.java.net/browse/JDK-6822627">JDK-6822627</a>
   */
  public static final boolean JDK_BUG_ID_6822627 = true;

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
  public static final boolean JDK_BUG_ID_7162125;
  static {
    boolean value;
    if (!SystemInfo.isMac || SystemInfo.isJavaVersionAtLeast("9")) value = false;
    else if (!SystemInfo.isJetBrainsJvm) value = true;
    else {
      try {
        Class.forName("sun.font.CCompositeFont");
        value = Boolean.getBoolean("disable.font.substitution");
      }
      catch (Throwable e) {
        value = true;
      }
    }
    JDK_BUG_ID_7162125 = value;
  }

  /**
   * XToolkit.getScreenInsets() may be very slow.
   * See https://bugs.openjdk.java.net/browse/JDK-8004103.
   */
  public static boolean isJdkBugId8004103() {
    return SystemInfo.isXWindow && !GraphicsEnvironment.isHeadless();
  }

  /**
   * Some HTTP connections lock the context class loader: https://bugs.openjdk.java.net/browse/JDK-8032832
   */
  public static final boolean JDK_BUG_ID_8032832 = SystemInfo.isJavaVersionAtLeast("1.8.0_20");

  public static final boolean JDK_BUG_ID_8147994 = !SystemInfo.isMac && !SystemInfo.isJavaVersionAtLeast("1.8.0_102");
}