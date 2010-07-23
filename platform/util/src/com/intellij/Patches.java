/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  public static final boolean ALL_FOLDERS_ARE_WRITABLE = SystemInfo.isWindows;

  /**
   * See sun bug parade.
   * When JTable loses focus it cancel cell editing. It should stop cell editing instead.
   * Actually SUN-boys told they have fixed the bug, but they cancel editing instead of stopping it.
   */
  public static final boolean SUN_BUG_ID_4503845 = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1;

  /**
   * See sun bug parade [http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4620537].
   * MouseListener on JTabbedPane with SCROLL_TAB_LAYOUT policy doesn't get events. In the related bug
   * #4499556 Sun advices to reimplement or hack JTabbedPane as workaround :)
   */
  public static final boolean SUN_BUG_ID_4620537 = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1;

  /**
   * See sun bug parade.
   * Debugger hangs on any attempt to attach/listen Connector when attach hanged once. 
   */
  public static final boolean SUN_BUG_338675 = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1;

  /**
   * java.lang.NullPointerException: Failed to retrieve atom name.
   */
  public static final boolean SUN_BUG_6322854 = SystemInfo.isLinux;

  /**
   * See sun bug parade.
   * If you invoke popup menu, then click on a different window (JFrame, JDialog. It doesn't matter),
   * the JPopupMenu in the previous window still has focus, as does the new window.
   * Seems like focus in two locations at the same time.
   *
   * This bug is fixed in JDK1.5
   */
  public static final boolean SUN_BUG_ID_4218084 = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1;

  /**
   * JDK 1.3.x and 1.4.x has the following error. When we close dialog and its content pane is being inserted
   * into another dialog and mouse WAS INSIDE of dialog's content pane then the AWT doesn't change
   * some internal references on focused component. It cause crash of dispatching of MOUSE_EXIT
   * event.
   */
  public static final boolean SPECIAL_WINPUT_METHOD_PROCESSING = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1;

  /** BasicMenuUI$MenuKeyHandler.menuKeyPressed() incorrect for dynamic menus. */
  public static final boolean SUN_BUG_ID_4738042 = SystemInfo.JAVA_VERSION.indexOf("1.4.") != -1 &&
                                                   SystemInfo.JAVA_VERSION.indexOf("1.4.2") == -1;

  /** BasicTreeUI.FocusHandler doesn't properly repaint JTree on focus changes */
  public static final boolean SUN_BUG_ID_4893787 = true;

  public static final boolean FILE_CHANNEL_TRANSFER_BROKEN = SystemInfo.isLinux && SystemInfo.OS_VERSION.startsWith("2.6");

  private static final boolean BELOW_142DP2 = SystemInfo.isMac &&
                                              (SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.4.0") ||
                                               SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.4.1") ||
                                               SystemInfo.JAVA_RUNTIME_VERSION.equals("1.4.2_03-117.1"));
  private static final boolean DP2_OR_DP3 = SystemInfo.isMac && (
                                                                  SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.4.2_03") ||
                                                                  SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.4.2_04")
                                                                  );

  /**
   * Every typing produces InputMethodEvent instead of KeyEvent with keyTyped event code. Fixed in JRE higher than 1.4.2_03-117.1
   */
  public static final boolean APPLE_BUG_ID_3337563 = BELOW_142DP2;

  /**
   * A window that receives focus immediately receives focusLost() event and then focusGained() again.
   */
  public static final boolean APPLE_BUG_ID_3716865 = DP2_OR_DP3;

  /**
   * Incorrect repaint of the components wrapped with JScrollPane.
   */
  public static final boolean APPLE_BUG_ID_3716835 = DP2_OR_DP3;

  /**
   * Use of JDK1.5 ReentrantReadWriteLock API eventually leads to JVM lock-up or core dump crashes.
   * With this flag true, API is wrapped with alternative implementation via early days Doug Lea's API.
   * @see com.intellij.util.concurrency.LockFactory
   */
  public static final boolean APPLE_BUG_ID_5359442 = SystemInfo.isMac && (!SystemInfo.isMacOSLeopard || !SystemInfo.isJavaVersionAtLeast("1.5.0_16"));

  /**
   * Index out of bounds at apple.laf.AquaTabbedPaneUI.tabForCoordinate
   * http://www.jetbrains.net/jira/browse/IDEADEV-15769
   */
  public static final boolean MAC_AQUA_TABS_HACK = SystemInfo.isMac;

  /**
   * It happened on Mac that some thread did not suspended during VM suspend
   * resuming VM in this case caused com.sun.jdi.InternalException #13
   */
  public static final boolean MAC_RESUME_VM_HACK = SystemInfo.isMac;
  
  /**
   * IBM java machine 1.4.2 crashes if debugger uses ObjectReference.disableCollection() and ObjectReference.enableCollection()
   */
  public static final boolean IBM_JDK_DISABLE_COLLECTION_BUG = "false".equalsIgnoreCase(System.getProperty("idea.debugger.keep.temp.objects"));

  public static final boolean MAC_HIDE_QUIT_HACK = false;

  /**
   * Causes calling thread to lock up acquiring content of the system clipboard on linux. Being called from the swing thread an
   * application stops responding.
   */
  public static final boolean SUN_BUG_ID_4818143 = SystemInfo.isLinux || SystemInfo.isFreeBSD || SystemInfo.isMac;

  /**
   * Java does not recognize the optional BOM which can begin a UTF-8 stream.
   * It treats the BOM as if it were the initial character of the stream
   */
  public static final boolean SUN_BUG_ID_4508058 = true;

  /**
   * Huge int[] leak through VolatileImages cached in RepaintManager whenever screen configuration changes.
   * For instance screen saver activates or computer goes hibernate. The problem still exists in 1.6 when two (or more)
   * monitors exists
   */
  public static final boolean SUN_BUG_ID_6209673 = SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.5") || SystemInfo.JAVA_RUNTIME_VERSION.startsWith("1.6");

  /**
   * Under Linux (Ubuntu) invoking "requestFocus" may (probably) activate inactive app. To investigate if it's really true. 
   */
  public static final boolean REQUEST_FOCUS_MAY_ACTIVATE_APP = SystemInfo.isLinux;
}
