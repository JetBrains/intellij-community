// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.facet.frameworks.SettingsConnectionService;
import com.intellij.internal.statistic.service.fus.FUSWhitelist;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.intellij.util.ObjectUtils.notNull;

public class EventLogExternalSettingsService extends SettingsConnectionService implements EventLogSettingsService {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService");
  private static final String APPROVED_GROUPS_SERVICE = "white-list-service";
  private static final String DICTIONARY_SERVICE = "dictionary-service";
  private static final String PERCENT_TRAFFIC = "percent-traffic";

  /**
   * Use {@link EventLogExternalSettingsService#getFeatureUsageSettings()}
   * or create new instance with custom recorder id.
   */
  @Deprecated
  public static EventLogExternalSettingsService getInstance() {
    return getFeatureUsageSettings();
  }

  @NotNull
  public static EventLogExternalSettingsService getFeatureUsageSettings() {
    return new EventLogExternalSettingsService("FUS");
  }

  public EventLogExternalSettingsService(@NotNull String recorderId) {
    super(getConfigUrl(recorderId, false), null);
  }

  public EventLogExternalSettingsService(@NotNull String recorderId, boolean isTest) {
    super(getConfigUrl(recorderId, isTest), null);
  }

  @NotNull
  private static String getConfigUrl(@NotNull String recorderId, boolean isTest) {
    final String templateUrl = ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEventLogSettingsUrl();
    if (isTest) {
      return String.format(templateUrl, "test/" + recorderId);
    }
    return String.format(templateUrl, recorderId);
  }

  @NotNull
  @Override
  public String[] getAttributeNames() {
    return ArrayUtil.mergeArrays(super.getAttributeNames(), PERCENT_TRAFFIC, APPROVED_GROUPS_SERVICE, DICTIONARY_SERVICE);
  }

  @Override
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

  @Override
  @Nullable
  public String getDictionaryServiceUrl() {
    return getSettingValue(DICTIONARY_SERVICE);
  }

  @Override
  @NotNull
  public LogEventFilter getEventFilter() {
    final FUSWhitelist whitelist = notNull(getWhitelistedGroups(), FUSWhitelist.empty());
    return new LogEventCompositeFilter(new LogEventWhitelistFilter(whitelist), LogEventSnapshotBuildFilter.INSTANCE);
  }

  @Override
  public boolean isInternal() {
    return StatisticsUploadAssistant.isTestStatisticsEnabled();
  }

  @Nullable
  protected FUSWhitelist getWhitelistedGroups() {
    final String productUrl = getWhiteListProductUrl();
    if (productUrl == null) return null;
    return FUStatisticsWhiteListGroupsService.getApprovedGroups(productUrl);
  }

  @Nullable
  public String getWhiteListProductUrl() {
    final String approvedGroupsServiceUrl = getSettingValue(APPROVED_GROUPS_SERVICE);
    if (approvedGroupsServiceUrl == null) return null;
    return approvedGroupsServiceUrl + ApplicationInfo.getInstance().getBuild().getProductCode() + ".json";
  }

  @NotNull
  private static BuildNumber getCurrentBuild() {
    return BuildNumber.fromString(EventLogConfiguration.INSTANCE.getBuild());
  }
}
