/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.internal.statistic.connect;

import com.intellij.internal.statistic.StatisticsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationActionProvider;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationNamesInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkListener;

/**
 * @author Sergey.Malenkov
 */
public final class StatisticsNotification extends Notification implements NotificationActionProvider {
  public StatisticsNotification(@NotNull String groupDisplayId, NotificationListener listener) {
    super(groupDisplayId,
          StatisticsBundle.message("stats.notification.title", ApplicationNamesInfo.getInstance().getFullProductName()),
          StatisticsBundle.message("stats.notification.content", ApplicationInfo.getInstance().getCompanyName()),
          NotificationType.INFORMATION, listener);
  }

  @NotNull
  public Action[] getActions(HyperlinkListener listener) {
    return new Action[]{
      new Action(listener, "allow", StatisticsBundle.message("stats.notification.button.allow")),
      new Action(listener, "decline", StatisticsBundle.message("stats.notification.button.decline")),
    };
  }
}
