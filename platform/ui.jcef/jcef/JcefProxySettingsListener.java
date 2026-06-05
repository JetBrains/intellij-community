// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.net.ProxySettingsListener;

public final class JcefProxySettingsListener implements ProxySettingsListener {
  @Override
  public void proxySettingsChanged() {
    if (JBCefApp.isStarted()) {
      JBCefApp.getNotificationGroup()
        .createNotification(IdeBundle.message("notification.title.jcef.proxyChanged"),
                            IdeBundle.message("notification.content.jcef.applySettings"),
                            NotificationType.WARNING)
        .addAction(NotificationAction.createSimple(IdeBundle.message("action.jcef.restart"),
                                                   () -> ApplicationManager.getApplication().restart()))
        .notify(null);
    }
  }
}
