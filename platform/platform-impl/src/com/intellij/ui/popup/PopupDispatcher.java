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

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.ui.popup.IdePopupEventDispatcher;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.stream.Stream;

public class PopupDispatcher implements AWTEventListener, KeyEventDispatcher, IdePopupEventDispatcher {

  private static WizardPopup ourActiveWizardRoot;
  private static WizardPopup ourShowingStep;

  private static final PopupDispatcher ourInstance = new PopupDispatcher();

  static {
    if (System.getProperty("is.popup.test") != null ||
      (ApplicationManagerEx.getApplicationEx() != null && ApplicationManagerEx.getApplicationEx().isUnitTestMode())) {
      Toolkit.getDefaultToolkit().addAWTEventListener(ourInstance, MouseEvent.MOUSE_PRESSED);
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ourInstance);
    }
  }

  private PopupDispatcher() {
  }

  public static PopupDispatcher getInstance() {
    return ourInstance;
  }

  public static void setActiveRoot(WizardPopup aRootPopup) {
    disposeActiveWizard();
    ourActiveWizardRoot = aRootPopup;
    ourShowingStep = aRootPopup;
    if (ApplicationManager.getApplication() != null) {
      IdeEventQueue.getInstance().getPopupManager().push(ourInstance);
    }
  }

  public static void clearRootIfNeeded(WizardPopup aRootPopup) {
    if (ourActiveWizardRoot == aRootPopup) {
      ourActiveWizardRoot = null;
      ourShowingStep = null;
      if (ApplicationManager.getApplication() != null) {
        IdeEventQueue.getInstance().getPopupManager().remove(ourInstance);
      }
    }
  }

  public void eventDispatched(AWTEvent event) {
    dispatchMouseEvent(event);
  }

  private boolean dispatchMouseEvent(AWTEvent event) {
    if (event.getID() != MouseEvent.MOUSE_PRESSED) {
      return false;
    }

    if (ourShowingStep == null) {
      return false;
    }

    WizardPopup eachParent = ourShowingStep;
    final MouseEvent mouseEvent = ((MouseEvent) event);

    Point point = (Point) mouseEvent.getPoint().clone();
    SwingUtilities.convertPointToScreen(point, mouseEvent.getComponent());

    while (true) {
      JComponent content = eachParent.getContent();
      if (content == null || !content.isShowing()) {
        getActiveRoot().cancel();
        return false;
      }

      if (eachParent.getBounds().contains(point) || !eachParent.canClose()) {
        return false;
      }

      eachParent = eachParent.getParent();
      if (eachParent == null) {
        getActiveRoot().cancel();
        return false;
      }
    }
  }

  public static boolean disposeActiveWizard() {
    if (ourActiveWizardRoot != null) {
      ourActiveWizardRoot.disposeChildren();
      Disposer.dispose(ourActiveWizardRoot);
      return true;
    }

    return false;
  }

  public boolean dispatchKeyEvent(final KeyEvent e) {
    if (ourShowingStep == null) {
      return false;
    }
    return ourShowingStep.dispatch(e);
  }

  public static void setShowing(WizardPopup aBaseWizardPopup) {
    ourShowingStep = aBaseWizardPopup;
  }

  public static void unsetShowing(WizardPopup aBaseWizardPopup) {
    if (ourActiveWizardRoot != null) {
      for (WizardPopup wp = aBaseWizardPopup; wp != null; wp = wp.getParent()) {
        if (wp == ourActiveWizardRoot) {
          ourShowingStep = aBaseWizardPopup.getParent();
          return;
        }
      }
    }
   }

  public static WizardPopup getActiveRoot() {
    return ourActiveWizardRoot;
  }

  public Component getComponent() {
    return ourShowingStep != null ? ourShowingStep.getContent() : null;
  }

  @Nullable
  @Override
  public Stream<JBPopup> getPopupStream() {
    return Stream.of(ourActiveWizardRoot);
  }

  public boolean dispatch(AWTEvent event) {
   if (event instanceof KeyEvent) {
      return dispatchKeyEvent(((KeyEvent) event));
   } else if (event instanceof MouseEvent) {
     return dispatchMouseEvent(event);
   } else {
     return false;
   }
  }

  public boolean requestFocus() {
    if (ourShowingStep != null) {
      ourShowingStep.requestFocus();
    }

    return true;
  }

  public boolean close() {
    return disposeActiveWizard();
  }

  @Override
  public void setRestoreFocusSilentely() {}
}
