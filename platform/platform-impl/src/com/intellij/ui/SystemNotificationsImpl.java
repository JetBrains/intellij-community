/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author mike
 */
@State(
  name = "SystemNotifications",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml")
  }
)
public class SystemNotificationsImpl extends SystemNotifications implements PersistentStateComponent<SystemNotificationsImpl.State> {
  public static class State {
    public Set<String> NOTIFICATIONS = new HashSet<String>();
  }

  interface Notifier {
    void notify(@NotNull Set<String> allNames, @NotNull String name, @NotNull String title, @NotNull String description);
  }

  private final Notifier myNotifier = getPlatformNotifier();
  private State myState = new State();

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(final State state) {
    myState = state;
  }

  @Override
  public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) {
    if (myNotifier != null && !ApplicationManager.getApplication().isActive()) {
      myState.NOTIFICATIONS.add(notificationName);
      myNotifier.notify(myState.NOTIFICATIONS, notificationName, title, text);
    }
  }

  private static Notifier getPlatformNotifier() {
    try {
      if (SystemInfo.isMac) {
        if (SystemInfo.isMacOSMountainLion && Registry.is("ide.mac.mountain.lion.notifications.enabled")) {
          return MountainLionNotifications.getInstance();
        }
        if (!Boolean.getBoolean("growl.disable")) {
          return GrowlNotifications.getInstance();
        }
      }

      if (SystemInfo.isXWindow && Registry.is("ide.libnotify.enabled") ) {
        return LibNotifyWrapper.getInstance();
      }
    }
    catch (Throwable t) {
      Logger.getInstance(SystemNotifications.class).error(t);
    }

    return null;
  }
}
