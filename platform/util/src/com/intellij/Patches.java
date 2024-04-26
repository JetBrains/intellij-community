// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.lang.JavaVersion;

public final class Patches {
  /**
   * See <a href="https://bugs.openjdk.org/browse/JDK-6322854">JDK-6322854</a>.
   * java.lang.NullPointerException: Failed to retrieve atom name.
   */
  public static final boolean SUN_BUG_ID_6322854 = SystemInfoRt.isUnix && !SystemInfoRt.isMac;

  /**
   * IBM JVM 1.4.2 crashes if debugger uses ObjectReference.disableCollection() and ObjectReference.enableCollection().
   */
  public static final boolean IBM_JDK_DISABLE_COLLECTION_BUG = "false".equalsIgnoreCase(System.getProperty("idea.debugger.keep.temp.objects"));

  /**
   * See <a href="https://bugs.openjdk.org/browse/JDK-4818143">JDK-4818143</a>.
   * The bug is marked as fixed but it actually isn't - {@link java.awt.datatransfer.Clipboard#getContents(Object)} call may hang
   * for up to 10 seconds if clipboard owner is not responding.
   */
  public static final boolean SLOW_GETTING_CLIPBOARD_CONTENTS = SystemInfoRt.isUnix;

  /**
   * Debugger hangs in trace mode with TRACE_SEND when method argument is a {@link com.sun.jdi.StringReference}
   */
  public static final boolean JDK_BUG_ID_21275177 = true;

  /**
   * Debugger hangs in trace mode with TRACE_SEND when method argument is a {@link com.sun.jdi.ThreadReference}
   */
  public static final boolean JDK_BUG_WITH_TRACE_SEND = true;

  /**
   * Some HTTP connections lock class loaders: <a href="https://bugs.openjdk.org/browse/JDK-8032832">JDK-8032832</a>
   * The issue claims to be fixed in 8u20, but the fix just replaces one lock with another (on a context class loader).
   */
  public static final boolean JDK_BUG_ID_8032832 = true;

  /**
   * <a href="https://bugs.openjdk.org/browse/JDK-8220231">JDK-8220231</a>
   */
  @ReviseWhenPortedToJDK("13")
  public static final boolean TEXT_LAYOUT_IS_SLOW = JavaVersion.current().feature == 12 && !SystemInfo.isJetBrainsJvm;
}
