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
import com.intellij.openapi.util.IdRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.ui.JBSwingUtilities;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ToolbarUpdater implements Activatable {
  private final KeymapManagerListener myKeymapManagerListener;
  private final WeakTimerListener myWeakTimerListener;
  /** @noinspection FieldCanBeLocal*/
  private final TimerListener myTimerListener;
  private final ActionManagerEx myActionManager;
  private final KeymapManagerEx myKeymapManager;
  private final JComponent myComponent;

  public ToolbarUpdater(@NotNull JComponent component) {
    this(ActionManagerEx.getInstanceEx(), KeymapManagerEx.getInstanceEx(), component);
  }

  public ToolbarUpdater(@NotNull ActionManagerEx actionManager, @NotNull KeymapManagerEx keymapManager, @NotNull JComponent component) {
    new UiNotifyConnector(component, this);
    myActionManager = actionManager;
    myKeymapManager = keymapManager;
    myComponent = component;
    myKeymapManagerListener = new MyKeymapManagerListener();
    keymapManager.addWeakListener(myKeymapManagerListener);
    myTimerListener = new MyTimerListener();
    myWeakTimerListener = new WeakTimerListener(actionManager, myTimerListener);
  }

  @Override
  public void showNotify() {
    myActionManager.addTimerListener(500, myWeakTimerListener);
    myActionManager.addTransparentTimerListener(500, myWeakTimerListener);
  }

  @Override
  public void hideNotify() {
    //noinspection ConstantConditions
    if (myActionManager == null) return; // not yet initialized
    myActionManager.removeTimerListener(myWeakTimerListener);
    myActionManager.removeTransparentTimerListener(myWeakTimerListener);
    if (ScreenUtil.isStandardAddRemoveNotify(myComponent)) {
      myKeymapManager.removeWeakListener(myKeymapManagerListener);
    }
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
    final IdRunnable updateRunnable = new IdRunnable(this) {
      @Override
      public void run() {
        if (!myComponent.isVisible()) {
          return;
        }

        updateActionsImpl(transparentOnly, forced);
      }
    };

    if (now) {
      updateRunnable.run();
    }
    else {
      final Application app = ApplicationManager.getApplication();
      final IdeFocusManager fm = IdeFocusManager.getInstance(null);

      if (!app.isUnitTestMode() && !app.isHeadlessEnvironment()) {
        if (app.isDispatchThread()) {
          fm.doWhenFocusSettlesDown(updateRunnable);
        }
        else {
          UiNotifyConnector.doWhenFirstShown(myComponent, new Runnable() {
            @Override
            public void run() {
              fm.doWhenFocusSettlesDown(updateRunnable);
            }
          });
        }
      }
    }
  }

  protected abstract void updateActionsImpl(boolean transparentOnly, boolean forced);

  protected void updateActionTooltips() {
    for (ActionButton actionButton : JBSwingUtilities.uiTraverser().preOrderTraversal(myComponent).filter(ActionButton.class)) {
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
}
