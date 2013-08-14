/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

@SuppressWarnings({"HardCodedStringLiteral", "UtilityClassWithoutPrivateConstructor"})
public class Patches {
  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4503845.
   * When JTable loses focus it cancel cell editing. It should stop cell editing instead.
   * Actually SUN-boys told they have fixed the bug, but they cancel editing instead of stopping it.
   */
  public static final boolean SUN_BUG_ID_4503845 = !SystemInfo.isJavaVersionAtLeast("1.4.1");

  /**
   * Debugger hangs on any attempt to attach/listen Connector when attach hanged once.
   * @deprecated to remove in IDEA 13 (IDEA support JRE only >= 1.6)
   */
  public static final boolean SUN_JDI_CONNECTOR_HANGUP_BUG = !SystemInfo.isJavaVersionAtLeast("1.5");

  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6322854.
   * java.lang.NullPointerException: Failed to retrieve atom name.
   */
  public static final boolean SUN_BUG_ID_6322854 = SystemInfo.isXWindow;

  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4218084.
   * If you invoke popup menu, then click on a different window (JFrame, JDialog. It doesn't matter),
   * the JPopupMenu in the previous window still has focus, as does the new window.
   * Seems like focus in two locations at the same time.
   */
  public static final boolean SUN_BUG_ID_4218084 = !SystemInfo.isJavaVersionAtLeast("1.5");

  /**
   * JDK 1.3.x and 1.4.x has the following error: when we close a dialog and its content pane is being inserted
   * into another dialog and mouse WAS INSIDE of dialog's content pane then the AWT doesn't change
   * some internal references on focused component. It cause crash of dispatching of MOUSE_EXIT event.
   */
  public static final boolean SPECIAL_INPUT_METHOD_PROCESSING = !SystemInfo.isJavaVersionAtLeast("1.5");

  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4738042.
   * BasicMenuUI$MenuKeyHandler.menuKeyPressed() incorrect for dynamic menus.
   */
  public static final boolean SUN_BUG_ID_4738042 = !SystemInfo.isJavaVersionAtLeast("1.4.2");

  /**
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4893787.
   * BasicTreeUI.FocusHandler doesn't properly repaint JTree on focus changes.
   */
  public static final boolean SUN_BUG_ID_4893787 = true;

  /**
   * Every typing produces InputMethodEvent instead of KeyEvent with keyTyped event code. Fixed in JRE higher than 1.4.2_03-117.1
   */
  public static final boolean APPLE_BUG_ID_3337563 = SystemInfo.isMac && !SystemInfo.isJavaVersionAtLeast("1.4.2.3.117.1");

  /**
   * Incorrect repaint of the components wrapped with JScrollPane.
   */
  public static final boolean APPLE_BUG_ID_3716835 = SystemInfo.isMac && !SystemInfo.isJavaVersionAtLeast("1.4.2.5");

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
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=9000030.
   */
  public static final boolean SUN_BUG_ID_9000030 = SystemInfo.isXWindow && SystemInfo.isJavaVersionAtLeast("1.7");

  /**
   * On some WMs modal dialogs may show behind full screen window.
   * See http://bugs.sun.com/view_bug.do?bug_id=8013359.
   */
  public static final boolean SUN_BUG_ID_8013359 =
    SystemInfo.isXWindow && SystemInfo.isJavaVersionAtLeast("1.7") && !SystemInfo.isJavaVersionAtLeast("1.7.0.40");

  /**
   * No BindException when another program is using the port.
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7179799
   */
  public static final boolean SUN_BUG_ID_7179799 = true;

  /**
   * Marker field to find all usages of the reflective access to JDK 7-specific methods
   * which need to be changed when migrated to JDK 7
   */
  public static final boolean USE_REFLECTION_TO_ACCESS_JDK7 = true;
}
