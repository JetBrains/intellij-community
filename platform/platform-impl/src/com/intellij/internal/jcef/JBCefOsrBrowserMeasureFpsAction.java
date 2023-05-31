// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef;

import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.jcef.JBCefApp;
import com.intellij.ui.jcef.JBCefFpsMeter;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Activates {@link JBCefFpsMeter} for the currently focused OSR JCEF browser.
 * <p>
 * Enable the registry key: ide.browser.jcef.osr.measureFPS=true
 * Optionally enable the registry key: ide.browser.jcef.osr.measureFPS.scroll=true
 *
 * @author tav
 */
final class JBCefOsrBrowserMeasureFpsAction extends DumbAwareAction {

  private static final @NotNull String FPS_METER_ID = RegistryManager.getInstance().get("ide.browser.jcef.osr.measureFPS.id").asString();

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner == null || !focusOwner.getClass().getName().contains("JBCefOsrComponent")) {
      throw new IllegalStateException("No JCEF OSR browser is in focus");
    }

    Notification notification = JBCefApp.getNotificationGroup().
      createNotification(IdeBundle.message("notification.title.jcef.measureFPS"),
                         IdeBundle.message("notification.content.jcef.measureFPS"), NotificationType.INFORMATION);
    notification.notify(null);

    final JBCefFpsMeter fpsMeter = JBCefFpsMeter.get(FPS_METER_ID);
    if (fpsMeter == null) return;

    focusOwner.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e1) {
        focusOwner.removeKeyListener(this);
        fpsMeter.setActive(false);
      }
    });
    focusOwner.addFocusListener(new FocusAdapter() {
      @Override
      public void focusLost(FocusEvent e1) {
        focusOwner.removeFocusListener(this);
        fpsMeter.setActive(false);
      }
    });

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Robot robot;

      try {
        robot = new Robot();
      }
      catch (AWTException ex) {
        //noinspection CallToPrintStackTrace
        ex.printStackTrace();
        return;
      }

      fpsMeter.setActive(true);
      boolean scroll = RegistryManager.getInstance().is("ide.browser.jcef.osr.measureFPS.scroll");
      // if (scroll) [tav] todo: check the browser is under mouse

      while (fpsMeter.isActive()) {
        if (scroll) robot.mouseWheel(SystemInfo.isMac ? -1 : 1);
        robot.delay(scroll ? 15 : 1000);
      }
    });
  }
}
