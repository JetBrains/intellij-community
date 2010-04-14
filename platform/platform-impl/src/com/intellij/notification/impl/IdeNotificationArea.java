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
package com.intellij.notification.impl;

import com.intellij.ide.DataManager;
import com.intellij.notification.impl.ui.NotificationComponent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.impl.status.StatusBarImpl;
import com.intellij.openapi.wm.impl.status.StatusBarPatch;
import com.intellij.openapi.wm.impl.status.StatusBarTooltipper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author spleaner
 */
public class IdeNotificationArea implements StatusBarPatch {
  private final NotificationComponent myNotificationComponent;

  public IdeNotificationArea(final StatusBarImpl statusBar) {
    myNotificationComponent = new NotificationComponent(this);

    StatusBarTooltipper.install(this, statusBar);
  }

  public JComponent getComponent() {
    return myNotificationComponent;
  }

  @Nullable
  public String updateStatusBar(final Editor selected, final JComponent componentSelected) {
    final NotificationsManagerImpl manager = NotificationsManagerImpl.getNotificationsManagerImpl();
    if (manager.hasNotifications(getProject())) {
      final int count = manager.count(getProject());
      return String.format("%s notification%s pending", count, count == 1 ? "" : "s");
    }

    return null;
  }

  public void clear() {
  }

  @Nullable
  public Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myNotificationComponent));
  }
}
