// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.TimerListener;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ToolbarUpdater implements Activatable {
  private final JComponent myComponent;

  private final KeymapManagerListener myKeymapManagerListener = new MyKeymapManagerListener();
  private final TimerListener myTimerListener = new MyTimerListener();
  private final WeakTimerListener myWeakTimerListener;

  private boolean myListenersArmed;

  public ToolbarUpdater(@NotNull JComponent component) {
    myComponent = component;
    myWeakTimerListener = new WeakTimerListener(myTimerListener);
    new UiNotifyConnector(component, this);
  }

  @Override
  public void showNotify() {
    if (myListenersArmed) {
      return;
    }

    myListenersArmed = true;
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    actionManager.addTimerListener(500, myWeakTimerListener);
    actionManager.addTransparentTimerListener(500, myWeakTimerListener);
    KeymapManagerEx.getInstanceEx().addWeakListener(myKeymapManagerListener);
    updateActionTooltips();
  }

  @Override
  public void hideNotify() {
    if (!myListenersArmed) {
      return;
    }

    myListenersArmed = false;
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    actionManager.removeTimerListener(myWeakTimerListener);
    actionManager.removeTransparentTimerListener(myWeakTimerListener);
    KeymapManagerEx.getInstanceEx().removeWeakListener(myKeymapManagerListener);
  }

  public void updateActions(boolean now, boolean forced) {
    updateActions(now, false, forced);
  }

  private void updateActions(boolean now, boolean transparentOnly, boolean forced) {
    Runnable updateRunnable = new MyUpdateRunnable(this, transparentOnly, forced);
    Application app = ApplicationManager.getApplication();
    if (now || (app.isUnitTestMode() && app.isDispatchThread())) {
      updateRunnable.run();
    }
    else if (!app.isHeadlessEnvironment()) {
      IdeFocusManager focusManager = IdeFocusManager.getInstance(null);
      if (app.isDispatchThread() && myComponent.isShowing()) {
        focusManager.doWhenFocusSettlesDown(updateRunnable);
      }
      else {
        UiNotifyConnector.doWhenFirstShown(myComponent, () -> focusManager.doWhenFocusSettlesDown(updateRunnable));
      }
    }
  }

  protected abstract void updateActionsImpl(boolean transparentOnly, boolean forced);

  protected void updateActionTooltips() {
    for (ActionButton actionButton : UIUtil.uiTraverser(myComponent).preOrderDfsTraversal().filter(ActionButton.class)) {
      actionButton.updateToolTipText();
    }
  }

  private final class MyKeymapManagerListener implements KeymapManagerListener {
    @Override
    public void activeKeymapChanged(Keymap keymap) {
      updateActionTooltips();
    }
  }

  private final class MyTimerListener implements TimerListener {

    @Override
    public ModalityState getModalityState() {
      return ModalityState.stateForComponent(myComponent);
    }

    @Override
    public void run() {
      if (!myComponent.isShowing()) {
        return;
      }

      // do not update when a popup menu is shown (if popup menu contains action which is also in the toolbar, it should not be enabled/disabled)
      MenuSelectionManager menuSelectionManager = MenuSelectionManager.defaultManager();
      MenuElement[] selectedPath = menuSelectionManager.getSelectedPath();
      if (selectedPath.length > 0) {
        return;
      }

      // don't update toolbar if there is currently active modal dialog
      Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (window instanceof Dialog && ((Dialog)window).isModal() && !SwingUtilities.isDescendingFrom(myComponent, window)) {
        return;
      }

      updateActions(false, ActionManagerEx.getInstanceEx().isTransparentOnlyActionsUpdateNow(), false);
    }
  }

  private static final class MyUpdateRunnable implements Runnable {
    private final boolean myTransparentOnly;
    private final boolean myForced;

    @NotNull
    private final WeakReference<ToolbarUpdater> myUpdaterRef;
    private final int myHash;

    MyUpdateRunnable(@NotNull ToolbarUpdater updater, boolean transparentOnly, boolean forced) {
      myTransparentOnly = transparentOnly;
      myForced = forced;
      myHash = updater.hashCode();

      myUpdaterRef = new WeakReference<>(updater);
    }

    @Override
    public void run() {
      ToolbarUpdater updater = myUpdaterRef.get();
      if (updater == null || (!updater.myComponent.isVisible() && !ApplicationManager.getApplication().isUnitTestMode())) {
        return;
      }

      updater.updateActionsImpl(myTransparentOnly, myForced);
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof MyUpdateRunnable)) return false;

      MyUpdateRunnable that = (MyUpdateRunnable)obj;
      if (myHash != that.myHash) return false;

      ToolbarUpdater updater1 = myUpdaterRef.get();
      ToolbarUpdater updater2 = that.myUpdaterRef.get();
      return Comparing.equal(updater1, updater2);
    }

    @Override
    public int hashCode() {
      return myHash;
    }
  }
}
