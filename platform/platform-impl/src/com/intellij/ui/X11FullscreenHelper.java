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
package com.intellij.ui;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.platform.unix.X11;
import sun.awt.X11.XAtom;
import sun.awt.X11.XBaseWindow;

import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 4/10/13
 * Time: 12:19 AM
 * based on code from VLCJ
 * http://code.google.com/p/vlcj/source/browse/trunk/vlcj/src/main/java/uk/co/caprica/vlcj/runtime/x/LibXUtil.java
 */

public class X11FullscreenHelper {
  /**
   * Ask the window manager to make a window full-screen.
   * <p>
   * This method sends a low-level event to an X window to request that the
   * window be made 'real' full-screen - i.e. the window will be sized to fill
   * the entire screen bounds, and will appear <em>above</em> any window
   * manager screen furniture such as panels and menus.
   * <p>
   * This method should only be called on platforms where X is supported.
   * <p>
   * The implementation makes use of the JNA X11 platform binding.
   *
   * @param w window to make full-screen
   * @param fullScreen <code>true</code> to make the window full-screen; <code>false</code> to restore the window to it's original size and position
   * @return <code>true</code> if the message was successfully sent to the window; <code>false</code> otherwise
   */
  public static boolean setFullScreenWindow(Window w, boolean fullScreen) {
    // Use the JNA platform X11 binding
    X11 x = X11.INSTANCE;
    X11.Display display = null;
    try {
      // Open the display
      display = x.XOpenDisplay(null);
      // Send the message

      // Send change property before going to Fullscreen to make sure that WM will know that window going to Fullscreen
      // Workaround for http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7057287
      XAtom.get("_NET_WM_STATE").setAtomListProperty((XBaseWindow)w.getPeer(), new XAtom[] {XAtom.get("_NET_WM_STATE_FULLSCREEN"), XAtom.get("_NET_WM_STATE_ABOVE")});

      int result = sendClientMessage(
        display,
        Native.getWindowID(w),
        "_NET_WM_STATE",
        new NativeLong(fullScreen ? _NET_WM_STATE_ADD : _NET_WM_STATE_REMOVE),
        x.XInternAtom(display, "_NET_WM_STATE_FULLSCREEN", false),
        x.XInternAtom(display, "_NET_WM_STATE_ABOVE", false)
      );
      return result != 0;
    }
    finally {
      if(display != null) {
        // Close the display
        x.XCloseDisplay(display);
      }
    }
  }

  /**
   * Helper method to send a client message to an X window.
   *
   * @param display display
   * @param wid native window identifier
   * @param msg type of message to send
   * @param data0 message data
   * @param data1 message data
   * @return <code>1</code> if the message was successfully sent to the window; <code>0</code> otherwise
   */
  private static int sendClientMessage(X11.Display display, long wid, String msg, NativeLong data0, NativeLong data1, NativeLong data2) {
    // Use the JNA platform X11 binding
    X11 x = X11.INSTANCE;
    // Create and populate a client-event structure
    X11.XEvent event = new X11.XEvent();
    event.type = X11.ClientMessage;
    // Select the proper union structure for the event type and populate it
    event.setType(X11.XClientMessageEvent.class);
    event.xclient.type = X11.ClientMessage;
    event.xclient.serial = new NativeLong(0L);
    event.xclient.send_event = 1;
    event.xclient.message_type = x.XInternAtom(display, msg, false);
    event.xclient.window = new X11.Window(wid);
    event.xclient.format = 32;
    // Select the proper union structure for the event data and populate it
    event.xclient.data.setType(NativeLong[].class);
    event.xclient.data.l[0] = data0;
    event.xclient.data.l[1] = data1;
    event.xclient.data.l[2] = data2;
    event.xclient.data.l[3] = new NativeLong(0L);
    event.xclient.data.l[4] = new NativeLong(0L);

    // Send the event
    NativeLong mask = new NativeLong(X11.SubstructureRedirectMask | X11.SubstructureNotifyMask);
    int result = x.XSendEvent(display, x.XDefaultRootWindow(display), 0, mask, event);
    // Flush, since we're not processing an X event loop
    x.XFlush(display);
    // Finally, return the result of sending the event
    return result;
  }

  // X window message definitions
  private static final int _NET_WM_STATE_REMOVE = 0;
  private static final int _NET_WM_STATE_ADD    = 1;

}
