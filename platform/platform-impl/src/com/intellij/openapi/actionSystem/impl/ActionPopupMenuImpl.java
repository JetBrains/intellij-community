// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.diagnostic.UILatencyLogger;
import com.intellij.ide.DataManager;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeEventQueue;
import com.intellij.internal.inspector.UiInspectorActionUtil;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.PlaceProvider;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.plaf.beg.BegMenuItemUI;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.messages.MessageBusConnection;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.util.Objects;
import java.util.function.Supplier;

final class ActionPopupMenuImpl implements ActionPopupMenu, ApplicationActivationListener {
  private static final Logger LOG = Logger.getInstance(ActionPopupMenuImpl.class);
  private static final IntSet SEEN_ACTION_GROUPS = new IntOpenHashSet(50);
  private final MyMenu myMenu;
  private final ActionManagerImpl myManager;

  private Supplier<? extends DataContext> myDataContextProvider;
  private MessageBusConnection myConnection;

  private IdeFrame myFrame;

  ActionPopupMenuImpl(@NotNull String place, @NotNull ActionGroup group,
                      @NotNull ActionManagerImpl actionManager,
                      @Nullable PresentationFactory factory) {
    if (ActionPlaces.UNKNOWN.equals(place) || place.isEmpty()) {
      LOG.warn("Please do not use ActionPlaces.UNKNOWN or the empty place. " +
               "Any string unique enough to deduce the popup menu location will do.", new Throwable("popup menu creation trace"));
    }
    else if (!ActionPlaces.isPopupPlace(place)) {
      LOG.info("isPopupOrMainMenuPlace(" + place + ")==false. Use ActionPlaces.getPopupPlace.");
    }
    myManager = actionManager;
    myMenu = new MyMenu(place, group, factory);
  }

  @Override
  public @NotNull JPopupMenu getComponent() {
    return myMenu;
  }

  @Override
  public @NotNull String getPlace() {
    return myMenu.myPlace;
  }

  @Override
  public @NotNull ActionGroup getActionGroup() {
    return myMenu.myGroup;
  }

  @Override
  public void setTargetComponent(@NotNull JComponent component) {
    setDataContext(() -> DataManager.getInstance().getDataContext(component));
  }

  @Override
  public void setDataContext(@NotNull Supplier<? extends DataContext> dataProvider) {
    myDataContextProvider = dataProvider;
  }

  private final class MyMenu extends JBPopupMenu implements PlaceProvider {
    private final @NotNull String myPlace;
    private final @NotNull ActionGroup myGroup;
    private DataContext myContext;
    private final PresentationFactory myPresentationFactory;
    private final @NotNull MyPopupMenuListener myListener;
    private long myPopupTriggeredNanos;

    MyMenu(@NotNull String place, @NotNull ActionGroup group, @Nullable PresentationFactory factory) {
      myPlace = place;
      myGroup = group;
      myPresentationFactory = factory != null ? factory : new MenuItemPresentationFactory();
      myListener = new MyPopupMenuListener();
      addPopupMenuListener(myListener);
      BegMenuItemUI.registerMultiChoiceSupport(this, popupMenu -> {
        Utils.updateMenuItems(popupMenu, myContext, myPlace, myPresentationFactory);
      });
      UiInspectorUtil.registerProvider(this, () -> UiInspectorActionUtil.collectActionGroupInfo(
        "Menu", myGroup, myPlace, myPresentationFactory));
    }

    @Override
    public @NotNull String getPlace() {
      return myPlace;
    }

    @Override
    public void show(@NotNull Component component, int x, int y) {
      if (!component.isShowing()) {
        throw new IllegalArgumentException("component must be shown on the screen (" + component + ")");
      }
      myPopupTriggeredNanos = IdeEventQueue.getInstance().getPopupTriggerTime();
      Utils.showPopupElapsedMillisIfConfigured(myPopupTriggeredNanos, this);

      int x2 = Math.max(0, Math.min(x, component.getWidth() - 1)); // fit x into [0, width-1]
      int y2 = Math.max(0, Math.min(y, component.getHeight() - 1)); // fit y into [0, height-1]
      myContext = Utils.createAsyncDataContext(
        myDataContextProvider != null ? myDataContextProvider.get() :
        DataManager.getInstance().getDataContext(component, x2, y2));
      updateChildren(new RelativePoint(component, new Point(x, y)));
      if (getComponentCount() == 0) {
        LOG.warn("'" + myPlace + "' popup menu fails to show: no menu items");
        return;
      }
      if (!component.isShowing()) {
        LOG.warn("'" + myPlace + "' popup menu fails to show: component is not showing (" + component.getClass().getName() + ")");
        return;
      }

      Application application = ApplicationManager.getApplication();
      if (application != null && application.isActive()) {
        Component parent = ComponentUtil.findUltimateParent(component);
        myFrame = parent instanceof IdeFrame ? (IdeFrame)parent : null;
        if (myConnection == null) {
          myConnection = application.getMessageBus().connect();
          myConnection.subscribe(ApplicationActivationListener.TOPIC, ActionPopupMenuImpl.this);
        }
      }
      myListener.targetComponent = component;
      super.show(component, x, y);
    }

    @Override
    public void addNotify() {
      super.addNotify();
      long time = myPopupTriggeredNanos > 0 ? TimeoutUtil.getDurationMillis(myPopupTriggeredNanos) : -1;
      PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(Utils.getCachedOnlyDataContext(myContext));
      Language language = psiFile == null ? null : psiFile.getLanguage();
      boolean coldStart = SEEN_ACTION_GROUPS.add(Objects.hash(myGroup, language));
      UILatencyLogger.ACTION_POPUP_LATENCY.log(EventFields.DurationMs.with(time),
                                               EventFields.ActionPlace.with(myPlace),
                                               UILatencyLogger.COLD_START.with(coldStart),
                                               EventFields.Language.with(language));
    }

    @Override
    public void setVisible(boolean b) {
      super.setVisible(b);
      if (!b) ReflectionUtil.resetField(this, "invoker");
    }

    private void updateChildren(@Nullable RelativePoint point) {
      removeAll();
      Utils.INSTANCE.fillPopupMenu(myGroup, this, myPresentationFactory, myContext, myPlace, point);
    }

    private void disposeMenu() {
      MessageBusConnection connection = myConnection;
      myFrame = null;
      myConnection = null;
      myManager.removeActionPopup(ActionPopupMenuImpl.this);
      removeAll();
      if (connection != null) {
        connection.disconnect();
      }
    }

    private final class MyPopupMenuListener implements PopupMenuListener {
      Component targetComponent;

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        disposeMenu();
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        HelpTooltip.enableTooltip(targetComponent);
        if (targetComponent instanceof Tree tree) {
          tree.unblockAutoScrollFromSource();
        }
        disposeMenu();
      }

      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        HelpTooltip.disableTooltip(targetComponent);
        if (getComponentCount() == 0) {
          updateChildren(null);
        }
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
