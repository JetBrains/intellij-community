// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.IdePopupEventDispatcher;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.stream.Stream;

public final class PopupDispatcher implements AWTEventListener, KeyEventDispatcher, IdePopupEventDispatcher {

  private static WizardPopup ourActiveWizardRoot;
  private static WizardPopup ourShowingStep;

  private static final PopupDispatcher ourInstance = new PopupDispatcher();

  static {
    if (System.getProperty("is.popup.test") != null ||
        ApplicationManager.getApplication() != null && ApplicationManager.getApplication().isUnitTestMode()) {
      Toolkit.getDefaultToolkit().addAWTEventListener(ourInstance, MouseEvent.MOUSE_PRESSED);
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(ourInstance);
    }
  }

  private PopupDispatcher() {
  }

  public static PopupDispatcher getInstance() {
    return ourInstance;
  }

  static void setActiveRoot(@NotNull WizardPopup aRootPopup) {
    disposeActiveWizard();
    ourActiveWizardRoot = aRootPopup;
    ourShowingStep = aRootPopup;
    if (ApplicationManager.getApplication() != null) {
      IdeEventQueue.getInstance().getPopupManager().push(ourInstance);
    }
  }

  static void clearRootIfNeeded(@NotNull WizardPopup aRootPopup) {
    if (ourActiveWizardRoot == aRootPopup) {
      ourActiveWizardRoot = null;
      ourShowingStep = null;
      if (ApplicationManager.getApplication() != null) {
        IdeEventQueue.getInstance().getPopupManager().remove(ourInstance);
      }
    }
  }

  @Override
  public void eventDispatched(AWTEvent event) {
    dispatchMouseEvent(event);
  }

  private static boolean dispatchMouseEvent(@NotNull AWTEvent event) {
    if (event.getID() != MouseEvent.MOUSE_PRESSED) {
      return false;
    }

    if (ourShowingStep == null) {
      return false;
    }

    WizardPopup eachParent = ourShowingStep;
    final MouseEvent mouseEvent = (MouseEvent) event;

    Point point = (Point) mouseEvent.getPoint().clone();
    SwingUtilities.convertPointToScreen(point, mouseEvent.getComponent());

    while (true) {
      if (eachParent.isDisposed() || !eachParent.getContent().isShowing()) {
        WizardPopup currentActiveRoot = getActiveRoot();
        if (eachParent.getParent() == currentActiveRoot) {
          currentActiveRoot.cancel();
        }
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

  private static boolean disposeActiveWizard() {
    if (ourActiveWizardRoot != null) {
      ourActiveWizardRoot.disposeChildren();
      Disposer.dispose(ourActiveWizardRoot);
      return true;
    }

    return false;
  }

  @Override
  public boolean dispatchKeyEvent(final KeyEvent e) {
    if (ourShowingStep == null) {
      return false;
    }
    return ourShowingStep.dispatch(e);
  }

  static void setShowing(@NotNull WizardPopup aBaseWizardPopup) {
    ourShowingStep = aBaseWizardPopup;
  }

  static void unsetShowing(@NotNull WizardPopup aBaseWizardPopup) {
    if (ourActiveWizardRoot != null) {
      for (WizardPopup wp = aBaseWizardPopup; wp != null; wp = wp.getParent()) {
        if (wp == ourActiveWizardRoot) {
          ourShowingStep = aBaseWizardPopup.getParent();
          return;
        }
      }
      WizardPopup activeRoot = getActiveRoot();
      if (aBaseWizardPopup != activeRoot && ourShowingStep != activeRoot) {
        // even if no parent popup exist (e.g. it's ActionGroupPopup), sync showing step with possible changed active root.
        // set visible active root to correctly dispatch subsequent events.
        ourShowingStep = activeRoot;
      }
    }
  }

  static WizardPopup getActiveRoot() {
    return ourActiveWizardRoot;
  }

  @Override
  public Component getComponent() {
    return ourShowingStep != null && !ourShowingStep.isDisposed() ? ourShowingStep.getContent() : null;
  }

  @Override
  public @NotNull Stream<JBPopup> getPopupStream() {
    return Stream.of(ourActiveWizardRoot);
  }

  @Override
  public boolean dispatch(AWTEvent event) {
    if (event instanceof KeyEvent) {
      return dispatchKeyEvent((KeyEvent)event);
    }
    if (event instanceof MouseEvent) {
      return dispatchMouseEvent(event);
    }
    return false;
  }

  @Override
  public boolean requestFocus() {
    if (ourShowingStep != null) {
      ourShowingStep.requestFocus();
    }

    return true;
  }

  @Override
  public boolean close() {
    return disposeActiveWizard();
  }
}
