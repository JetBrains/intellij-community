/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class ActionPopupMenuImpl implements ActionPopupMenu, ApplicationActivationListener {

  private final MyMenu myMenu;
  private final ActionManagerImpl myManager;
  private MessageBusConnection myConnection;

  private final Application myApp;
  private IdeFrame myFrame;
  @Nullable private Getter<DataContext> myDataContextProvider;

  public ActionPopupMenuImpl(String place, @NotNull ActionGroup group, ActionManagerImpl actionManager, @Nullable PresentationFactory factory) {
    myManager = actionManager;
    myMenu = new MyMenu(place, group, factory);
    myApp = ApplicationManager.getApplication();
  }

  public JPopupMenu getComponent() {
    return myMenu;
  }

  public void setDataContextProvider(@Nullable Getter<DataContext> dataContextProvider) {
    myDataContextProvider = dataContextProvider;
  }

  private class MyMenu extends JBPopupMenu {
    private final String myPlace;
    private final ActionGroup myGroup;
    private DataContext myContext;
    private final PresentationFactory myPresentationFactory;

    public MyMenu(String place, @NotNull ActionGroup group, @Nullable PresentationFactory factory) {
      myPlace = place;
      myGroup = group;
      myPresentationFactory = factory != null ? factory : new MenuItemPresentationFactory();
      addPopupMenuListener(new MyPopupMenuListener());
    }

    public void show(final Component component, int x, int y) {
      if (!component.isShowing()) {
        //noinspection HardCodedStringLiteral
        throw new IllegalArgumentException("component must be shown on the screen");
      }

      removeAll();

      // Fill menu. Only after filling menu has non zero size.

      int x2 = Math.max(0, Math.min(x, component.getWidth() - 1)); // fit x into [0, width-1]
      int y2 = Math.max(0, Math.min(y, component.getHeight() - 1)); // fit y into [0, height-1]

      myContext = myDataContextProvider != null ? myDataContextProvider.get() : DataManager.getInstance().getDataContext(component, x2, y2);
      Utils.fillMenu(myGroup, this, true, myPresentationFactory, myContext, myPlace, false, false);
      if (getComponentCount() == 0) {
        return;
      }
      Dimension preferredSize = getPreferredSize();

      // Translate (x,y) into screen coordinate syetem

      int _x, _y; // these are screen coordinates of clicked point
      Point p = component.getLocationOnScreen();
      _x = p.x + x;
      _y = p.y + y;

      // Determine graphics device which contains our point

      GraphicsConfiguration targetGraphicsConfiguration = null;
      GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] devices = env.getScreenDevices();
      for (GraphicsDevice device : devices) {
        GraphicsConfiguration graphicsConfiguration = device.getDefaultConfiguration();
        Rectangle r = graphicsConfiguration.getBounds();
        if (r.x <= _x && _x <= r.x + r.width && r.y <= _y && _y <= r.y + r.height) {
          targetGraphicsConfiguration = graphicsConfiguration;
          break;
        }
      }
      if (targetGraphicsConfiguration == null && devices.length > 0) {
        targetGraphicsConfiguration = env.getDefaultScreenDevice().getDefaultConfiguration();
      }
      if (targetGraphicsConfiguration == null) {
        //noinspection HardCodedStringLiteral
        throw new IllegalStateException("It's impossible to determine target graphics environment for point (" + _x + "," + _y + ")");
      }

      // Determine real client area of target graphics configuration
      Rectangle targetRectangle = ScreenUtil.getScreenRectangle(targetGraphicsConfiguration);

      // Fit popup into targetRectangle.
      // The algorithm is the following:
      // First of all try to move menu up on its height. If menu's left-top corner
      // is inside screen bounds after that, then OK. Otherwise, if menu is too high
      // (left-top corner is outside of screen bounds) then try to move menu up on
      // not visible visible area height.
      if (_x + preferredSize.width > targetRectangle.x + targetRectangle.width) {
        x -= preferredSize.width;
      }
      if (_y + preferredSize.height > targetRectangle.y + targetRectangle.height) {
        int invisibleHeight = _y + preferredSize.height - targetRectangle.y - targetRectangle.height;
        y -= invisibleHeight;
      }

      if (myApp != null) {
        if (myApp.isActive()) {
          Component frame = UIUtil.findUltimateParent(component);
          if (frame instanceof IdeFrame) {
            myFrame = (IdeFrame)frame;
          }
          myConnection = myApp.getMessageBus().connect();
          myConnection.subscribe(ApplicationActivationListener.TOPIC, ActionPopupMenuImpl.this);
       }
      }

      super.show(component, x, y);
    }

    @Override
    public void setVisible(boolean b) {
      super.setVisible(b);
      if (!b) ReflectionUtil.resetField(this, "invoker");
    }

    private class MyPopupMenuListener implements PopupMenuListener {
      public void popupMenuCanceled(PopupMenuEvent e) {
        disposeMenu();
      }

      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        disposeMenu();
      }

      private void disposeMenu() {
        myManager.removeActionPopup(ActionPopupMenuImpl.this);
        MyMenu.this.removeAll();
        if (myConnection != null) {
          myConnection.disconnect();
        }
      }

      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        MyMenu.this.removeAll();
        Utils.fillMenu(myGroup, MyMenu.this, !UISettings.getInstance().DISABLE_MNEMONICS, myPresentationFactory, myContext, myPlace, false,
                       false);
        myManager.addActionPopup(ActionPopupMenuImpl.this);
      }
    }
  }

  @Override
  public void applicationActivated(IdeFrame ideFrame) {
  }

  @Override
  public void applicationDeactivated(IdeFrame ideFrame) {
    if (myFrame == ideFrame) {
      myMenu.setVisible(false);
    }
  }

}
