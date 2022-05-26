// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.popup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public interface PopupComponent {
  Logger LOG = Logger.getInstance(PopupComponent.class);

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
      if (!UIUtil.isShowing(owner)) {
        throw new IllegalArgumentException("Popup owner must be showing, owner " + owner.getClass());
      }

      Window window = ComponentUtil.getWindow(owner);
      if (window instanceof Frame) {
        myDialog = new JDialog((Frame)window);
      }
      else if (window instanceof Dialog) {
        myDialog = new JDialog((Dialog)window);
      }
      else {
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
      Window window = getWindow();

      if (window != null) {
        fixFlickering(window, false);
      }
      myPopup.show();
      if (window != null) {
        fixFlickering(window, true);
        if (window instanceof JWindow) {
          ((JWindow)window).getRootPane().putClientProperty(JBPopup.KEY, myJBPopup);
        }
      }
    }

    private static void fixFlickering(@NotNull Window window, boolean opaque) {
      try {
        if (StartupUiUtil.isUnderDarcula() && SystemInfoRt.isMac && Registry.is("darcula.fix.native.flickering", false)) {
          window.setOpacity(opaque ? 1.0f : 0.0f);
        }
      }
      catch (Exception ignore) {
      }
    }

    @Override
    public @Nullable Window getWindow() {
      return myPopup instanceof HeavyWeightPopup ? ObjectUtils.tryCast(((HeavyWeightPopup)myPopup).getWindow(), JWindow.class) : null;
    }

    @Override
    public void setRequestFocus(boolean requestFocus) {
    }
  }
}

