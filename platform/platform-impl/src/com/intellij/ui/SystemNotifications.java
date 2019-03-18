// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author mike
 */
public abstract class SystemNotifications {
  private static final SystemNotifications NULL = new SystemNotifications() {
    @Override
    public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) { }
  };

  public static SystemNotifications getInstance() {
    Application app = ApplicationManager.getApplication();
    return app.isHeadlessEnvironment() || app.isUnitTestMode() ? NULL : ServiceManager.getService(SystemNotifications.class);
  }

  public abstract void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text);
}