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
package com.intellij.ui.popup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public interface PopupComponent {

  Logger LOG = Logger.getInstance("#com.intellij.ui.popup.PopupComponent");

  void hide(boolean dispose);

  void show();

  Window getWindow();

  void setRequestFocus(boolean requestFocus);

  interface Factory {
    PopupComponent getPopup(Component owner, Component content, int x, int y);

    boolean isNativePopup();

    class AwtDefault implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y) {
        return new AwtPopupWrapper(PopupFactory.getSharedInstance().getPopup(owner, content, x, y));
      }

      public boolean isNativePopup() {
        return true;
      }
    }

    class AwtHeavyweight implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y) {
        final PopupFactory factory = PopupFactory.getSharedInstance();

        final int oldType = PopupUtil.getPopupType(factory);
        PopupUtil.setPopupType(factory, 2);
        final Popup popup = factory.getPopup(owner, content, x, y);
        if (oldType >= 0) PopupUtil.setPopupType(factory, oldType);

        return new AwtPopupWrapper(popup);
      }

      public boolean isNativePopup() {
        return true;
      }
    }

    class Dialog implements Factory {
      public PopupComponent getPopup(Component owner, Component content, int x, int y) {
        return new DialogPopupWrapper(owner, content, x, y);
      }public boolean isNativePopup() {
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

    public DialogPopupWrapper(Component owner, Component content, int x, int y) {
      if (!owner.isShowing()) {
        throw new IllegalArgumentException("Popup owner must be showing");
      }

      final Window wnd = owner instanceof Window ? (Window)owner: SwingUtilities.getWindowAncestor(owner);
      if (wnd instanceof Frame) {
        myDialog = new JDialog((Frame)wnd, false);
      } else {
        myDialog = new JDialog((Dialog)wnd, false);
      }

      myDialog.getContentPane().setLayout(new BorderLayout());
      myDialog.getContentPane().add(content, BorderLayout.CENTER);

      myDialog.setUndecorated(true);
      myDialog.pack();
      myDialog.setLocation(x, y);
    }

    public Window getWindow() {
      return myDialog;
    }

    public void hide(boolean dispose) {
      myDialog.setVisible(false);
      if (dispose) {
        myDialog.dispose();
      }
    }

    public void show() {
      if (!myRequestFocus) {
        myDialog.setFocusableWindowState(false);
      }
      myDialog.setVisible(true);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myDialog.setFocusableWindowState(true);
        }
      });
    }
  }

  class AwtPopupWrapper implements PopupComponent {

    private final Popup myPopup;

    public AwtPopupWrapper(Popup popup) {
      myPopup = popup;

      if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) {
        final Component c = (Component)ReflectionUtil.getField(Popup.class, myPopup, Component.class, "component");
        c.setBackground(UIUtil.getPanelBackgound());
      }
    }

    public void hide(boolean dispose) {
      myPopup.hide();
    }

    public void show() {
      myPopup.show();
    }

    public Window getWindow() {
      final Component c = (Component)ReflectionUtil.getField(Popup.class, myPopup, Component.class, "component");
      return c instanceof JWindow ? (JWindow)c : null;
    }

    public void setRequestFocus(boolean requestFocus) {
    }
  }

}
