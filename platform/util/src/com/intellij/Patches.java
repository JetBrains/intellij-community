// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.lang.JavaVersion;

public final class Patches {
  /**
   * See <a href="https://bugs.openjdk.java.net/browse/JDK-6322854">JDK-6322854</a>.
   * java.lang.NullPointerException: Failed to retrieve atom name.
   */
  public static final boolean SUN_BUG_ID_6322854 = SystemInfoRt.isXWindow;

  /**
   * IBM JVM 1.4.2 crashes if debugger uses ObjectReference.disableCollection() and ObjectReference.enableCollection().
   */
  public static final boolean IBM_JDK_DISABLE_COLLECTION_BUG = "false".equalsIgnoreCase(System.getProperty("idea.debugger.keep.temp.objects"));

  /**
   * See <a href="https://bugs.openjdk.java.net/browse/JDK-4818143">JDK-4818143</a>.
   * The bug is marked as fixed but it actually isn't - {@link java.awt.datatransfer.Clipboard#getContents(Object)} call may hang
   * for up to 10 seconds if clipboard owner is not responding.
   */
  public static final boolean SLOW_GETTING_CLIPBOARD_CONTENTS = SystemInfoRt.isUnix;

  /**
   * Desktop API support on X Window is limited to GNOME (and even there it may work incorrectly).
   * See <a href="https://bugs.openjdk.java.net/browse/JDK-6486393">JDK-6486393</a>.
   */
  public static final boolean SUN_BUG_ID_6486393 = SystemInfoRt.isXWindow;

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
  public static final boolean JDK_MAC_FONT_STYLE_DETECTION_WORKAROUND = SystemInfoRt.isMac;

  /**
   * Some HTTP connections lock class loaders: <a href="https://bugs.openjdk.java.net/browse/JDK-8032832">JDK-8032832</a>
   * The issue claims to be fixed in 8u20, but the fix just replaces one lock with another (on a context class loader).
   */
  public static final boolean JDK_BUG_ID_8032832 = true;

  /**
   * <a href="https://bugs.openjdk.java.net/browse/JDK-8220231">JDK-8220231</a>
   */
  @ReviseWhenPortedToJDK("13")
  public static final boolean TEXT_LAYOUT_IS_SLOW = JavaVersion.current().feature == 12 && !SystemInfo.isJetBrainsJvm;
}
