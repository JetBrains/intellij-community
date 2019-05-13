/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  public boolean isAvailable() {
    return true;
  }

  public abstract void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text);
}
