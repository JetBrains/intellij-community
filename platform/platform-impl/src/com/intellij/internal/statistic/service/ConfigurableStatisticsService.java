// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service;

import com.intellij.internal.statistic.connect.*;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ConfigurableStatisticsService<T extends StatisticsConnectionService> implements
                                                                                           StatisticsService {
  @Override
  public final StatisticsResult send() {
    final String serviceUrl = getConnectionService().getServiceUrl();
    if (serviceUrl == null) {
      return new StatisticsResult(StatisticsResult.ResultCode.ERROR_IN_CONFIG, "ERROR: unknown Statistics Service URL.");
    }

    if (!getConnectionService().isTransmissionPermitted()) {
      return new StatisticsResult(StatisticsResult.ResultCode.NOT_PERMITTED_SERVER, "NOT_PERMITTED");
    }

    try {
      String sentData = sendData();
      StatisticsUploadAssistant.updateSentTime();
      return new StatisticsResult(StatisticsResult.ResultCode.SEND, sentData);
    }
    catch (Exception e) {
      return new StatisticsResult(StatisticsResult.ResultCode.SENT_WITH_ERRORS, e.getMessage() != null ? e.getMessage() : "NPE");
    }
  }

  @Override
  public Notification createNotification(@NotNull final String groupDisplayId, @Nullable NotificationListener listener) {
    return new StatisticsNotification(groupDisplayId, listener);
  }

  @NotNull
  // result: the content was sent.
  protected abstract String sendData();

  public abstract T getConnectionService();
}
