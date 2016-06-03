/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.mac;

import com.apple.eawt.event.GestureUtilities;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher;

import java.awt.*;
import java.util.ArrayList;
import com.apple.eawt.event.PressureEvent;
import com.apple.eawt.event.PressureListener;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;

import javax.swing.*;

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
          MouseShortcut shortcut = new PressureShortcut(e.getStage());
          fillActionsList(shortcut, IdeKeyEventDispatcher.isModalContext(component));
          ActionManagerEx actionManager = ActionManagerEx.getInstanceEx();
          if (actionManager != null) {
            AnAction[] actions = myActions.toArray(new AnAction[myActions.size()]);
            for (AnAction action : actions) {
              DataContext dataContext = DataManager.getInstance().getDataContext(component);
              Presentation presentation = myPresentationFactory.getPresentation(action);
              AnActionEvent actionEvent = new AnActionEvent(null, dataContext, ActionPlaces.MAIN_MENU, presentation,
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
      }
    });
  }


  private final PresentationFactory myPresentationFactory = new PresentationFactory();

  private void fillActionsList(MouseShortcut mouseShortcut, boolean isModalContext) {
    myActions.clear();

    // search in main keymap
    if (KeymapManagerImpl.ourKeymapManagerInitialized) {
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
