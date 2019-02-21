// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.facet.frameworks.SettingsConnectionService;
import com.intellij.internal.statistic.persistence.ApprovedGroupsCacheConfigurable;
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

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.ObjectUtils.notNull;

public class EventLogExternalSettingsService extends SettingsConnectionService implements EventLogSettingsService {
  private static final Logger LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService");
  private static final String APPROVED_GROUPS_SERVICE = "white-list-service";
  private static final String DICTIONARY_SERVICE = "dictionary-service";
  private static final String PERCENT_TRAFFIC = "percent-traffic";
  private static final long ACCEPTED_CACHE_AGE_MS = TimeUnit.MILLISECONDS.convert(7, TimeUnit.DAYS);
  private static final long DONT_REQUIRE_UPDATE_AGE_MS = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);

  public static EventLogExternalSettingsService getInstance() {
    return new EventLogExternalSettingsService();
  }

  protected EventLogExternalSettingsService() {
    super(((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEventLogSettingsUrl(), null);
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

  @NotNull
  public FUSWhitelist getApprovedGroups() {
    return getApprovedGroups(ApprovedGroupsCacheConfigurable.getInstance());
  }

  @NotNull
  public FUSWhitelist getApprovedGroups(ApprovedGroupsCacheConfigurable cache) {
    final BuildNumber currentBuild = getCurrentBuild();
    final Date currentDate = new Date();
    final FUSWhitelist cachedGroups = cache.getCachedGroups(currentDate, DONT_REQUIRE_UPDATE_AGE_MS, currentBuild);
    if (cachedGroups != null) return cachedGroups;

    final FUSWhitelist groups = getWhitelistedGroups();
    if (groups != null) {
      return cache.cacheGroups(currentDate, groups, currentBuild);
    }
    return notNull(cache.getCachedGroups(currentDate, ACCEPTED_CACHE_AGE_MS), FUSWhitelist.empty());
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
    final String approvedGroupsServiceUrl = getSettingValue(APPROVED_GROUPS_SERVICE);
    if (approvedGroupsServiceUrl == null) {
      return null;
    }
    final String productUrl = approvedGroupsServiceUrl + ApplicationInfo.getInstance().getBuild().getProductCode() + ".json";
    return FUStatisticsWhiteListGroupsService.getApprovedGroups(productUrl, getCurrentBuild());
  }

  @NotNull
  private static BuildNumber getCurrentBuild() {
    return BuildNumber.fromString(EventLogConfiguration.INSTANCE.getBuild());
  }
}
