// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.connect.SettingsConnectionService;
import com.intellij.internal.statistic.service.fus.FUSWhitelist;
import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.notNull;

public class EventLogUploadSettingsService extends SettingsConnectionService implements EventLogSettingsService {
  private static final Logger LOG = Logger.getInstance(EventLogUploadSettingsService.class);

  private static final String APPROVED_GROUPS_SERVICE = "white-list-service";
  private static final String DICTIONARY_SERVICE = "dictionary-service";
  private static final String PERCENT_TRAFFIC = "percent-traffic";

  @NotNull
  private EventLogApplicationInfo myApplicationInfo;

  public EventLogUploadSettingsService(@NotNull String recorderId, @NotNull EventLogApplicationInfo application) {
    super(getConfigUrl(recorderId, application.getTemplateUrl(), application.isTest()), null);
    myApplicationInfo = application;
  }

  @NotNull
  private static String getConfigUrl(@NotNull String recorderId, @NotNull String templateUrl, boolean isTest) {
    if (isTest) {
      return String.format(templateUrl, "test/" + recorderId);
    }
    return String.format(templateUrl, recorderId);
  }

  @Override
  public String @NotNull [] getAttributeNames() {
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
  public @NotNull EventLogApplicationInfo getApplicationInfo() {
    return myApplicationInfo;
  }

  @Nullable
  protected FUSWhitelist getWhitelistedGroups() {
    final String productUrl = getWhiteListProductUrl();
    if (productUrl == null) return null;
    return FUStatisticsWhiteListGroupsService.getApprovedGroups(productUrl);
  }

  @NonNls
  @Nullable
  public String getWhiteListProductUrl() {
    final String approvedGroupsServiceUrl = getSettingValue(APPROVED_GROUPS_SERVICE);
    if (approvedGroupsServiceUrl == null) return null;
    return approvedGroupsServiceUrl + myApplicationInfo.getProductCode() + ".json";
  }
}
