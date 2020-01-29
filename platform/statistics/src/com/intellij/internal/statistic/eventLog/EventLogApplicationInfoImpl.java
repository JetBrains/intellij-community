// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;

public class EventLogApplicationInfoImpl implements EventLogApplicationInfo {
  private boolean myIsTest;

  public EventLogApplicationInfoImpl(boolean isTest) {
    myIsTest = isTest;
  }

  @Override
  public String getTemplateUrl() {
    return ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getEventLogSettingsUrl();
  }

  @Override
  public String getProductCode() {
    return ApplicationInfo.getInstance().getBuild().getProductCode();
  }

  @Override
  public boolean isInternal() {
    return StatisticsUploadAssistant.isTestStatisticsEnabled();
  }

  @Override
  public boolean isTest() {
    return myIsTest;
  }
}
