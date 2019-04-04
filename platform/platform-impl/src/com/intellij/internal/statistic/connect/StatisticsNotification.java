// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  @NotNull
  public Action[] getActions(HyperlinkListener listener) {
    return new Action[]{
      new Action(listener, "allow", StatisticsBundle.message("stats.notification.button.allow")),
      new Action(listener, "decline", StatisticsBundle.message("stats.notification.button.decline")),
    };
  }
}
