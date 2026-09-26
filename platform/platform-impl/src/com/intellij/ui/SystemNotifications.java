// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts.SystemNotificationText;
import com.intellij.openapi.util.NlsContexts.SystemNotificationTitle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SystemNotifications {
  private static final SystemNotifications MUTE = new SystemNotifications() {
    @Override
    public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) { }
  };

  public static @NotNull SystemNotifications getInstance() {
    var app = ApplicationManager.getApplication();
    return app.isHeadlessEnvironment() || app.isUnitTestMode() ? MUTE : ApplicationManager.getApplication().getService(SystemNotifications.class);
  }

  public abstract void notify(
    @NotNull String notificationName,
    @NotNull @SystemNotificationTitle String title,
    @NotNull @SystemNotificationText String text
  );

  public void notify(
    @NotNull String notificationName,
    @NotNull @SystemNotificationTitle String title,
    @NotNull @SystemNotificationText String text,
    @Nullable Runnable onActivated
  ) {
    notify(notificationName, title, text);
  }
}
