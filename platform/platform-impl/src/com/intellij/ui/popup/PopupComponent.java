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
package com.intellij.ui.popup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;
import com.sun.awt.AWTUtilities;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface PopupComponent {
  Logger LOG = Logger.getInstance("#com.intellij.ui.popup.PopupComponent");

  void hide(boolean dispose);

  void show();

  Window getWindow();

  void setRequestFocus(boolean requestFocus);

  boolean isPopupWindow(Window window);

  interface Factory {
    PopupComponent getPopup(Component owner, Component content, int x, int y, JBPopup jbPopup);

    boolean isNativePopup();

    class AwtDefault implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y, JBPopup jbPopup) {
        final PopupFactory factory = PopupFactory.getSharedInstance();
        final Popup popup = factory.getPopup(owner, content, x, y);
        return new AwtPopupWrapper(popup, jbPopup);
      }

      public boolean isNativePopup() {
        return true;
      }
    }

    class AwtHeavyweight implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y, JBPopup jbPopup) {
        if (OurHeavyWeightPopup.isEnabled()) {
          return new AwtPopupWrapper(new OurHeavyWeightPopup(owner, content, x, y), jbPopup);
        }
        final PopupFactory factory = PopupFactory.getSharedInstance();

        final int oldType = PopupUtil.getPopupType(factory);
        PopupUtil.setPopupType(factory, 2);
        final Popup popup = factory.getPopup(owner, content, x, y);
        if (oldType >= 0) PopupUtil.setPopupType(factory, oldType);

        return new AwtPopupWrapper(popup, jbPopup);
      }

      public boolean isNativePopup() {
        return true;
      }
    }

    class Dialog implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y, JBPopup jbPopup) {
        return new DialogPopupWrapper(owner, content, x, y, jbPopup);
      }

      public boolean isNativePopup() {
        return false;
      }
    }
  }

  class DialogPopupWrapper implements PopupComponent {
    private final JDialog myDialog;
    private boolean myRequestFocus = true;

    public void setRequestFocus(boolean requestFocus) {
      myRequestFocus = requestFocus;
    }

    @Override
    public boolean isPopupWindow(Window window) {
      return myDialog != null && myDialog == window;
    }

    public DialogPopupWrapper(Component owner, Component content, int x, int y, JBPopup jbPopup) {
      if (!owner.isShowing()) {
        throw new IllegalArgumentException("Popup owner must be showing");
      }

      final Window wnd = UIUtil.getWindow(owner);
      if (wnd instanceof Frame) {
        myDialog = new JDialog((Frame)wnd);
      } else if (wnd instanceof Dialog) {
        myDialog = new JDialog((Dialog)wnd);
      } else {
        myDialog = new JDialog();
      }

      myDialog.getContentPane().setLayout(new BorderLayout());
      myDialog.getContentPane().add(content, BorderLayout.CENTER);
      myDialog.getRootPane().putClientProperty(JBPopup.KEY, jbPopup);
      myDialog.getRootPane().setWindowDecorationStyle(JRootPane.NONE);
      myDialog.setUndecorated(true);
      myDialog.setBackground(UIUtil.getPanelBackground());
      myDialog.pack();
      myDialog.setLocation(x, y);
      myDialog.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosed(WindowEvent e) {
          super.windowClosed(e);
          A11YFix.invokeFocusGained(myDialog);
        }
      });
    }

    public Window getWindow() {
      return myDialog;
    }

    public void hide(boolean dispose) {
      myDialog.setVisible(false);
      if (dispose) {
        myDialog.dispose();
        myDialog.getRootPane().putClientProperty(JBPopup.KEY, null);
      }
    }

    public void show() {

    UIUtil.suppressFocusStealing(getWindow());

      if (!myRequestFocus) {
        myDialog.setFocusableWindowState(false);
      }

      AwtPopupWrapper.fixFlickering(myDialog, false);
      myDialog.setVisible(true);
      AwtPopupWrapper.fixFlickering(myDialog, true);

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myDialog.setFocusableWindowState(true);
        }
      });
    }
  }

  class AwtPopupWrapper implements PopupComponent {

    private final Popup myPopup;
    private JBPopup myJBPopup;

    public AwtPopupWrapper(Popup popup, JBPopup jbPopup) {
      myPopup = popup;
      myJBPopup = jbPopup;

      if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) {
        final Component c = ReflectionUtil.getField(Popup.class, myPopup, Component.class, "component");
        c.setBackground(UIUtil.getPanelBackground());
      }
      // TODO: should we call A11YFix.invokeFocusGained(getWindow()) on window closing?
    }

    @Override
    public boolean isPopupWindow(Window window) {
      final Window wnd = getWindow();
      return wnd != null && wnd == window;
    }

    public void hide(boolean dispose) {
      myPopup.hide();

      Window wnd = getWindow();
      if (wnd instanceof JWindow) {
        JRootPane rootPane = ((JWindow)wnd).getRootPane();
        if (rootPane != null) {
          ReflectionUtil.resetField(rootPane, "clientProperties");
          final Container cp = rootPane.getContentPane();
          if (cp != null) {
            cp.removeAll();
          }
        }
      }
    }

    public void show() {
      Window wnd = getWindow();
      
      fixFlickering(wnd, false);
      myPopup.show();
      fixFlickering(wnd, true);
      
      if (wnd instanceof JWindow) {
        ((JWindow)wnd).getRootPane().putClientProperty(JBPopup.KEY, myJBPopup);
      }
    }

    private static void fixFlickering(Window wnd, boolean opaque) {
      try {
        if (UIUtil.isUnderDarcula() && SystemInfo.isMac && Registry.is("darcula.fix.native.flickering") && wnd != null) {
          AWTUtilities.setWindowOpaque(wnd, opaque);
        }
      } catch (Exception ignore) {}
    }

    public Window getWindow() {
      final Component c = ReflectionUtil.getField(Popup.class, myPopup, Component.class, "component");
      return c instanceof JWindow ? (JWindow)c : null;
    }

    public void setRequestFocus(boolean requestFocus) {
    }
  }
}

/**
 * On Windows, AccessBridge loses a11y focus when a non-focusable popup window is closed.
 * At the same time, IDEA focus remains in, for instance, the editor and doesn't change.
 * As a workaround, {@link #invokeFocusGained} notifies AccessBridge that the current
 * focus owner gains focus. This doesn't affect IDEA focus management. See IDEA-152169
 */
class A11YFix {
  private static Class cAccessBridge;
  private static Field fAccessBridge;
  private static Method mFocusGained;
  private static boolean initialized;
  // TODO: additionally check if a11y tool (like NVDA) is active
  private static final boolean ENABLED = SystemInfo.isWindows && UIUtil.isA11YEnabled(UIUtil.A11Y_ACCESS_BRIDGE);

  public static void invokeFocusGained(Window closingWindow) {
    if (!ENABLED) return;

    IdeFocusManager manager = IdeFocusManager.findInstanceByComponent(closingWindow);
    if (manager != null) {
      Component focusOwner = manager.getFocusOwner();
      if (focusOwner != null) {
        Window focusedWindow = UIUtil.getWindow(focusOwner);
        // Check if the focus owner is not in the closing window and notify AB it gains focus.
        // In case focus owner changes, AB will catch up with it on its own.
        if (focusedWindow != closingWindow) {
          Object bridge = getAccessBridge();
          if (bridge != null) {
            FocusEvent fe = new FocusEvent(focusOwner, FocusEvent.FOCUS_GAINED);
            try {
              mFocusGained.invoke(bridge, fe, focusOwner.getAccessibleContext());
            }
            catch (Throwable ignore) {
            }
          }
        }
      }
    }
  }

  private static boolean checkInit() {
    if (initialized) return fAccessBridge != null && mFocusGained != null;

    try {
      ClassLoader cl = ClassLoader.getSystemClassLoader();
      cAccessBridge = cl.loadClass("com.sun.java.accessibility.AccessBridge");
    }
    catch (Throwable ignore) {
    }
    if (cAccessBridge != null) {
      fAccessBridge = ReflectionUtil.getDeclaredField(cAccessBridge, "theAccessBridge");
      if (fAccessBridge != null) {
        fAccessBridge.setAccessible(true);
        mFocusGained = ReflectionUtil.getDeclaredMethod(cAccessBridge, "focusGained", FocusEvent.class, AccessibleContext.class);
        if (mFocusGained != null) {
          mFocusGained.setAccessible(true);
        }
      }
    }
    initialized = true;
    return fAccessBridge != null && mFocusGained != null;
  }

  private static Object getAccessBridge() {
    if (!checkInit()) return null;
    try {
      return fAccessBridge.get(null);
    }
    catch (Throwable ignore) {
    }
    return null;
  }
}

