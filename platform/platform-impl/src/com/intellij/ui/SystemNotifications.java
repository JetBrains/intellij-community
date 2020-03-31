// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.SystemNotificationText;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.util.SystemNotificationTitle;

public abstract class SystemNotifications {
  private static final SystemNotifications NULL = new SystemNotifications() {
    @Override
    public void notify(@NotNull String notificationName,
                       @NotNull @SystemNotificationTitle String title,
                       @NotNull @SystemNotificationTitle String text) { }
  };

  public static SystemNotifications getInstance() {
    Application app = ApplicationManager.getApplication();
    return app.isHeadlessEnvironment() || app.isUnitTestMode() ? NULL : ServiceManager.getService(SystemNotifications.class);
  }

  public abstract void notify(@NotNull String notificationName,
                              @NotNull @SystemNotificationTitle String title,
                              @NotNull @SystemNotificationText String text);
}