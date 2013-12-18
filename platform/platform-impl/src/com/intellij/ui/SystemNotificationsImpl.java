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
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )}
)
public class SystemNotificationsImpl extends SystemNotifications implements PersistentStateComponent<SystemNotificationsImpl.State> {
  private State myState = new State();
  private boolean myGrowlDisabled = false;

  public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) {
    if (!areNotificationsEnabled() || ApplicationManager.getApplication().isActive()) return;

    if (SystemInfo.isLinux && Registry.is("ide.linux.gtk.notifications.enabled") ) {
      LibNotifyWrapper.showNotification(title, text);
      return;
    }

    final MacNotifications notifications;
    try {
      notifications = getMacNotifications();
    }
    catch (Throwable e) {
      myGrowlDisabled = true;
      return;
    }

    myState.NOTIFICATIONS.add(notificationName);
    notifications.notify(myState.NOTIFICATIONS, notificationName, title, text);
  }

  private static MacNotifications getMacNotifications() {
    return SystemInfo.isMacOSMountainLion && Registry.is("ide.mac.mountain.lion.notifications.enabled") ?
                      MountainLionNotifications.getNotifications() : GrowlNotifications.getNotifications();
  }

  private boolean areNotificationsEnabled() {
    boolean enabled = false;

    if (SystemInfo.isMac) {
      enabled = !(myGrowlDisabled || "true".equalsIgnoreCase(System.getProperty("growl.disable")));
      if (!enabled) {
        enabled = SystemInfo.isMacOSMountainLion && Registry.is("ide.mac.mountain.lion.notifications.enabled");
      }
    } else {
        enabled = SystemInfo.isLinux && Registry.is("ide.linux.gtk.notifications.enabled");
    }

    return enabled;
  }

  public State getState() {
    return myState;
  }

  public void loadState(final State state) {
    myState = state;
  }


  public static class State {
    public Set<String> NOTIFICATIONS = new HashSet<String>();
  }
}
