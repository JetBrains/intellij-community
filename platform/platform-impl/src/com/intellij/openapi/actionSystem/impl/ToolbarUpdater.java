/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  private final ActionManagerEx myActionManager;
  private final KeymapManagerEx myKeymapManager;
  private final JComponent myComponent;

  private final KeymapManagerListener myKeymapManagerListener = new MyKeymapManagerListener();
  private final TimerListener myTimerListener = new MyTimerListener();
  private final WeakTimerListener myWeakTimerListener;

  private boolean myListenersArmed;

  public ToolbarUpdater(@NotNull JComponent component) {
    this(ActionManagerEx.getInstanceEx(), KeymapManagerEx.getInstanceEx(), component);
  }

  public ToolbarUpdater(@NotNull ActionManagerEx actionManager, @NotNull KeymapManagerEx keymapManager, @NotNull JComponent component) {
    myActionManager = actionManager;
    myKeymapManager = keymapManager;
    myComponent = component;
    myWeakTimerListener = new WeakTimerListener(actionManager, myTimerListener);
    new UiNotifyConnector(component, this);
  }

  @Override
  public void showNotify() {
    if (myListenersArmed) return;
    myListenersArmed = true;
    myActionManager.addTimerListener(500, myWeakTimerListener);
    myActionManager.addTransparentTimerListener(500, myWeakTimerListener);
    myKeymapManager.addWeakListener(myKeymapManagerListener);
    updateActionTooltips();
  }

  @Override
  public void hideNotify() {
    if (!myListenersArmed) return;
    myListenersArmed = false;
    myActionManager.removeTimerListener(myWeakTimerListener);
    myActionManager.removeTransparentTimerListener(myWeakTimerListener);
    myKeymapManager.removeWeakListener(myKeymapManagerListener);
  }

  @NotNull
  public KeymapManagerEx getKeymapManager() {
    return myKeymapManager;
  }

  @NotNull
  public ActionManagerEx getActionManager() {
    return myActionManager;
  }

  public void updateActions(boolean now, boolean forced) {
    updateActions(now, false, forced);
  }

  private void updateActions(boolean now, final boolean transparentOnly, final boolean forced) {
    final Runnable updateRunnable = new MyUpdateRunnable(this, transparentOnly, forced);
    final Application app = ApplicationManager.getApplication();

    if (now || app.isUnitTestMode()) {
      updateRunnable.run();
    }
    else {
      final IdeFocusManager fm = IdeFocusManager.getInstance(null);

      if (!app.isHeadlessEnvironment()) {
        if (app.isDispatchThread() && myComponent.isShowing()) {
          fm.doWhenFocusSettlesDown(updateRunnable);
        }
        else {
          UiNotifyConnector.doWhenFirstShown(myComponent, () -> fm.doWhenFocusSettlesDown(updateRunnable));
        }
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

      updateActions(false, myActionManager.isTransparentOnlyActionsUpdateNow(), false);
    }
  }

  private static class MyUpdateRunnable implements Runnable {
    private final boolean myTransparentOnly;
    private final boolean myForced;

    @NotNull private final WeakReference<ToolbarUpdater> myUpdaterRef;
    private final int myHash;

    public MyUpdateRunnable(@NotNull ToolbarUpdater updater, boolean transparentOnly, boolean forced) {
      myTransparentOnly = transparentOnly;
      myForced = forced;
      myHash = updater.hashCode();

      myUpdaterRef = new WeakReference<>(updater);
    }

    @Override
    public void run() {
      ToolbarUpdater updater = myUpdaterRef.get();
      if (updater == null) return;

      if (!updater.myComponent.isVisible() && !ApplicationManager.getApplication().isUnitTestMode()) {
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
