/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.SystemInfo;
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
      id="SystemNotifications",
      file="$APP_CONFIG$/other.xml"
    )}
)
public class SystemNotificationsImpl extends SystemNotifications implements PersistentStateComponent<SystemNotificationsImpl.State> {
  private State myState = new State();
  private boolean myGrowlDisabled = false;

  public void notify(@NotNull String notificationName, @NotNull String title, @NotNull String text) {
    if (!isGrowlEnabled() || ApplicationManager.getApplication().isActive()) return;

    final GrowlNotifications nofications;
    try {
      nofications = GrowlNotifications.getNotifications();
    }
    catch (Throwable e) {
      myGrowlDisabled = true;
      return;
    }

    myState.NOTIFICATIONS.add(notificationName);
    nofications.notify(myState.NOTIFICATIONS, notificationName, title, text);
  }

  private boolean isGrowlEnabled() {
    if (myGrowlDisabled || !SystemInfo.isMac) return false;

    if ("true".equalsIgnoreCase(System.getProperty("growl.disable"))) {
      myGrowlDisabled = true;
    }

    return !myGrowlDisabled;
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
