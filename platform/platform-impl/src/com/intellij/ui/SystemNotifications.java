// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public abstract class SystemNotifications {
  private static final SystemNotifications NULL = new SystemNotifications() {
    @Override
    public void notify(@NotNull String notificationName,
                       @NotNull @NlsContexts.SystemNotificationTitle String title,
                       @NotNull @NlsContexts.SystemNotificationTitle String text) { }
  };

  public static SystemNotifications getInstance() {
    Application app = ApplicationManager.getApplication();
    return app.isHeadlessEnvironment() || app.isUnitTestMode() ? NULL : ApplicationManager.getApplication()
      .getService(SystemNotifications.class);
  }

  public abstract void notify(@NotNull String notificationName,
                              @NotNull @NlsContexts.SystemNotificationTitle String title,
                              @NotNull @NlsContexts.SystemNotificationText String text);
}