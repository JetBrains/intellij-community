// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.FieldAccessor;
import com.intellij.util.ui.UIUtil;
import com.sun.awt.AWTUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
      @Override
      public PopupComponent getPopup(Component owner, Component content, int x, int y, JBPopup jbPopup) {
        final PopupFactory factory = PopupFactory.getSharedInstance();
        final Popup popup = factory.getPopup(owner, content, x, y);
        return new AwtPopupWrapper(popup, jbPopup);
      }

      @Override
      public boolean isNativePopup() {
        return true;
      }
    }

    class AwtHeavyweight implements Factory {
      @Override
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

      @Override
      public boolean isNativePopup() {
        return true;
      }
    }

    class Dialog implements Factory {
      @Override
      public PopupComponent getPopup(Component owner, Component content, int x, int y, JBPopup jbPopup) {
        return new DialogPopupWrapper(owner, content, x, y, jbPopup);
      }

      @Override
      public boolean isNativePopup() {
        return false;
      }
    }
  }

  class DialogPopupWrapper implements PopupComponent {
    private final JDialog myDialog;
    private boolean myRequestFocus = true;

    @Override
    public void setRequestFocus(boolean requestFocus) {
      myRequestFocus = requestFocus;
    }

    @Override
    public boolean isPopupWindow(Window window) {
      return myDialog != null && myDialog == window;
    }

    public DialogPopupWrapper(Component owner, Component content, int x, int y, JBPopup jbPopup) {
      if (!owner.isShowing()) {
        throw new IllegalArgumentException("Popup owner must be showing, owner " + owner.getClass());
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
    }

    @Override
    public Window getWindow() {
      return myDialog;
    }

    @Override
    public void hide(boolean dispose) {
      myDialog.setVisible(false);
      if (dispose) {
        myDialog.dispose();
        myDialog.getRootPane().putClientProperty(JBPopup.KEY, null);
      }
    }

    @Override
    public void show() {

      if (!myRequestFocus) {
        myDialog.setFocusableWindowState(false);
      }

      AwtPopupWrapper.fixFlickering(myDialog, false);
      myDialog.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosed(WindowEvent e) {
          //A11YFix.invokeFocusGained(myDialog);
          myDialog.removeWindowListener(this);
        }
      });
      myDialog.setVisible(true);
      AwtPopupWrapper.fixFlickering(myDialog, true);

      SwingUtilities.invokeLater(() -> myDialog.setFocusableWindowState(true));
    }
  }

  class AwtPopupWrapper implements PopupComponent {

    private final Popup myPopup;
    private final JBPopup myJBPopup;

    public AwtPopupWrapper(Popup popup, JBPopup jbPopup) {
      myPopup = popup;
      myJBPopup = jbPopup;
      //TODO[tav]: should we call A11YFix.invokeFocusGained(getWindow()) on window closing?
    }

    @Override
    public boolean isPopupWindow(Window window) {
      final Window wnd = getWindow();
      return wnd != null && wnd == window;
    }

    @Override
    public void hide(boolean dispose) {
      myPopup.hide();
      if (!dispose) return;

      Window window = getWindow();
      JRootPane rootPane = window instanceof RootPaneContainer ? ((RootPaneContainer)window).getRootPane() : null;
      DialogWrapper.cleanupRootPane(rootPane);
      DialogWrapper.cleanupWindowListeners(window);
    }

    @Override
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

    @Override
    public Window getWindow() {
      Component c = ourComponentField.get(myPopup);
      return c instanceof JWindow ? (JWindow)c : null;
    }

    @Override
    public void setRequestFocus(boolean requestFocus) {
    }

    static final FieldAccessor<Popup, Component> ourComponentField = new FieldAccessor<>(Popup.class, "component", Component.class);
  }
}

