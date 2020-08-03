// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.messages.MessageBusConnection;
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
final class ActionPopupMenuImpl implements ActionPopupMenu, ApplicationActivationListener {
  private static final Logger LOG = Logger.getInstance(ActionPopupMenuImpl.class);
  private final Application myApp;
  private final MyMenu myMenu;
  private final ActionManagerImpl myManager;

  private Getter<? extends DataContext> myDataContextProvider;
  private MessageBusConnection myConnection;

  private IdeFrame myFrame;
  private boolean myIsToolWindowContextMenu;

  ActionPopupMenuImpl(@NotNull String place, @NotNull ActionGroup group,
                      @NotNull ActionManagerImpl actionManager,
                      @Nullable PresentationFactory factory) {
    myManager = actionManager;
    myMenu = new MyMenu(place, group, factory);
    myApp = ApplicationManager.getApplication();
  }

  @NotNull
  @Override
  public JPopupMenu getComponent() {
    return myMenu;
  }

  @Override
  @NotNull
  public String getPlace() {
    return myMenu.myPlace;
  }

  @NotNull
  @Override
  public ActionGroup getActionGroup() {
    return myMenu.myGroup;
  }

  void setDataContextProvider(@NotNull Getter<? extends DataContext> dataContextProvider) {
    myDataContextProvider = dataContextProvider;
  }

  @Override
  public void setTargetComponent(@NotNull JComponent component) {
    myDataContextProvider = () -> DataManager.getInstance().getDataContext(component);
    myIsToolWindowContextMenu = ComponentUtil.getParentOfType(InternalDecorator.class, component) != null;
  }

  boolean isToolWindowContextMenu() {
    return myIsToolWindowContextMenu;
  }

  private class MyMenu extends JBPopupMenu {
    @NotNull
    private final String myPlace;
    @NotNull
    private final ActionGroup myGroup;
    private DataContext myContext;
    private final PresentationFactory myPresentationFactory;

    MyMenu(@NotNull String place, @NotNull ActionGroup group, @Nullable PresentationFactory factory) {
      myPlace = place;
      myGroup = group;
      myPresentationFactory = factory != null ? factory : new MenuItemPresentationFactory();
      addPopupMenuListener(new MyPopupMenuListener());

      UiInspectorUtil.registerProvider(this, () -> UiInspectorUtil.collectActionGroupInfo("Menu", myGroup, myPlace));
    }

    @Override
    public void show(@NotNull Component component, int x, int y) {
      if (!component.isShowing()) {
        throw new IllegalArgumentException("component must be shown on the screen (" + component + ")");
      }

      removeAll();

      // Fill menu. Only after filling menu has non zero size.

      int x2 = Math.max(0, Math.min(x, component.getWidth() - 1)); // fit x into [0, width-1]
      int y2 = Math.max(0, Math.min(y, component.getHeight() - 1)); // fit y into [0, height-1]

      myContext = myDataContextProvider != null ? myDataContextProvider.get() : DataManager.getInstance().getDataContext(component, x2, y2);
      long time = -System.currentTimeMillis();
      Utils.fillMenu(myGroup, this, !UISettings.getInstance().getDisableMnemonics(), myPresentationFactory, myContext, myPlace, false, LaterInvocator.isInModalContext(), false);
      time += System.currentTimeMillis();
      if (time > 1000) LOG.warn(time + "ms to fill popup menu " + myPlace);
      if (getComponentCount() == 0) {
        LOG.warn("no components in popup menu " + myPlace);
        return;
      }
      if (myApp != null) {
        if (myApp.isActive()) {
          Component frame = ComponentUtil.findUltimateParent(component);
          if (frame instanceof IdeFrame) {
            myFrame = (IdeFrame)frame;
          }
          myConnection = myApp.getMessageBus().connect();
          myConnection.subscribe(ApplicationActivationListener.TOPIC, ActionPopupMenuImpl.this);
       }
      }
      assert component.isShowing() : "Component: " + component;
      super.show(component, x, y);
    }

    @Override
    public void setVisible(boolean b) {
      super.setVisible(b);
      if (!b) ReflectionUtil.resetField(this, "invoker");
    }

    private class MyPopupMenuListener implements PopupMenuListener {
      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        disposeMenu();
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        disposeMenu();
      }

      private void disposeMenu() {
        myManager.removeActionPopup(ActionPopupMenuImpl.this);
        removeAll();
        if (myConnection != null) {
          myConnection.disconnect();
        }
      }

      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        removeAll();
        Utils.fillMenu(myGroup, MyMenu.this, !UISettings.getInstance().getDisableMnemonics(), myPresentationFactory, myContext, myPlace, false,
                       LaterInvocator.isInModalContext(), false);
        myManager.addActionPopup(ActionPopupMenuImpl.this);
      }
    }
  }

  @Override
  public void applicationDeactivated(@NotNull IdeFrame ideFrame) {
    if (myFrame == ideFrame) {
      myMenu.setVisible(false);
    }
  }
}
