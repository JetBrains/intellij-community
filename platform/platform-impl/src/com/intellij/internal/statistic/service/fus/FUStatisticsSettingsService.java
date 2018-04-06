// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus;

import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

// this service connects to jetbrains.com resources and requests actual info about running statistics services
// 1. url: where to post statistics data.
// 2. white-list-service: WhiteListService url: this service returns approved UsagesCollectors(groups)
// 3. permitted: true/false. statistics could be stopped remotely. if false UsageCollectors won't be started
public class FUStatisticsSettingsService extends StatisticsConnectionService {
  private static final String APPROVED_GROUPS_SERVICE = "white-list-service";
  public  static FUStatisticsSettingsService getInstance() {return  new FUStatisticsSettingsService();}

  private FUStatisticsSettingsService() {
    super(((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getFUStatisticsSettingsUrl(), null);
  }

  @NotNull
  @Override
  public String[] getAttributeNames() {
    return ArrayUtil.mergeArrays(super.getAttributeNames(), APPROVED_GROUPS_SERVICE);
  }

  @NotNull
  public Set<String> getApprovedGroups() {
    final String approvedGroupsServiceUrl = getSettingValue(APPROVED_GROUPS_SERVICE);
    if (approvedGroupsServiceUrl == null) {
      return Collections.emptySet();
    }
    return FUStatisticsWhiteListGroupsService.getApprovedGroups(getProductRelatedUrl(approvedGroupsServiceUrl));
  }

  @NotNull
  public String getProductRelatedUrl(@NotNull  String approvedGroupsServiceUrl) {
    return approvedGroupsServiceUrl + ApplicationInfo.getInstance().getBuild().getProductCode() + ".json";
  }
}
