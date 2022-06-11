// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts.SystemNotificationText;
import com.intellij.openapi.util.NlsContexts.SystemNotificationTitle;
import org.jetbrains.annotations.NotNull;

public abstract class SystemNotifications {
  private static final SystemNotifications NULL = new SystemNotifications() {
    @Override
    public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) { }
  };

  public static SystemNotifications getInstance() {
    Application app = ApplicationManager.getApplication();
    return app.isHeadlessEnvironment() || app.isUnitTestMode() ? NULL : ApplicationManager.getApplication().getService(SystemNotifications.class);
  }

  public abstract void notify(@NotNull String notificationName,
                              @NotNull @SystemNotificationTitle String title,
                              @NotNull @SystemNotificationText String text);
}
