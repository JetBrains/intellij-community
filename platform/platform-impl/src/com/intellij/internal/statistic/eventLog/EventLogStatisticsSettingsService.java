// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;

public class EventLogStatisticsSettingsService extends StatisticsConnectionService {
  public static EventLogStatisticsSettingsService getInstance() {
    return new EventLogStatisticsSettingsService();
  }

  private EventLogStatisticsSettingsService() {
    super(((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEventLogSettingsUrl(), null);
  }
}
