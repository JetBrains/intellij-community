// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.apple.eawt.event.GestureUtilities;
import com.apple.eawt.event.PressureEvent;
import com.apple.eawt.event.PressureListener;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/**
 * @author denis
 */
public class MacGestureSupportForEditor {

  private final ArrayList<AnAction> myActions = new ArrayList<>(1);

  public MacGestureSupportForEditor(JComponent component) {
    GestureUtilities.addGestureListenerTo(component, new PressureListener() {
      @Override
      public void pressure(PressureEvent e) {
        if (IdeMouseEventDispatcher.isForceTouchAllowed() && e.getStage() == 2) {
          ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(
            () -> handleMouseShortcut(e, new PressureShortcut(e.getStage()), component));
        }
      }
    });
  }

  private void handleMouseShortcut(PressureEvent e, MouseShortcut shortcut, JComponent component) {
    fillActionsList(shortcut, IdeKeyEventDispatcher.isModalContext(component));
    ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
    if (actionManager != null) {
      AnAction[] actions = myActions.toArray(AnAction.EMPTY_ARRAY);
      for (AnAction action : actions) {
        DataContext dataContext = DataManager.getInstance().getDataContext(component);
        Presentation presentation = myPresentationFactory.getPresentation(action);
        AnActionEvent actionEvent =
          new AnActionEvent(null, dataContext, ActionPlaces.FORCE_TOUCH, presentation,
                            ActionManager.getInstance(),
                            0);
        action.beforeActionPerformedUpdate(actionEvent);

        if (presentation.isEnabled()) {
          actionManager.fireBeforeActionPerformed(action, dataContext, actionEvent);
          final Component context = PlatformDataKeys.CONTEXT_COMPONENT.getData(dataContext);

          if (context != null && !context.isShowing()) continue;

          action.actionPerformed(actionEvent);
        }
      }
    }
    e.consume();
    IdeMouseEventDispatcher.forbidForceTouch();
  }


  private final PresentationFactory myPresentationFactory = new PresentationFactory();

  private void fillActionsList(MouseShortcut mouseShortcut, boolean isModalContext) {
    myActions.clear();

    // search in main keymap
    if (KeymapManagerImpl.isKeymapManagerInitialized()) {
      final KeymapManager keymapManager = KeymapManager.getInstance();
      if (keymapManager != null) {
        final Keymap keymap = keymapManager.getActiveKeymap();
        final String[] actionIds = keymap.getActionIds(mouseShortcut);

        ActionManager actionManager = ActionManager.getInstance();
        for (String actionId : actionIds) {
          AnAction action = actionManager.getAction(actionId);

          if (action == null) continue;

          if (isModalContext && !action.isEnabledInModalContext()) continue;

          if (!myActions.contains(action)) {
            myActions.add(action);
          }
        }
      }
    }
  }
}
