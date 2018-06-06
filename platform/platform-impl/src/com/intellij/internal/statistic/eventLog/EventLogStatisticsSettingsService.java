// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.facet.frameworks.SettingsConnectionService;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class EventLogStatisticsSettingsService extends SettingsConnectionService {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.EventLogStatisticsSettingsService");
  private static final String PERCENT_TRAFFIC = "percent-traffic";

  public static EventLogStatisticsSettingsService getInstance() {
    return new EventLogStatisticsSettingsService();
  }

  private EventLogStatisticsSettingsService() {
    super(((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEventLogSettingsUrl(), null);
  }

  @NotNull
  @Override
  public String[] getAttributeNames() {
    return ArrayUtil.mergeArrays(super.getAttributeNames(), PERCENT_TRAFFIC);
  }

  public int getPermittedTraffic() {
    final String permitted = getSettingValue(PERCENT_TRAFFIC);
    if (permitted != null) {
      try {
        return Integer.parseInt(permitted);
      }
      catch (NumberFormatException e) {
        LOG.trace("Permitted traffic is not defined or has invalid format: '" + permitted + "'");
      }
    }
    return 0;
  }
}
