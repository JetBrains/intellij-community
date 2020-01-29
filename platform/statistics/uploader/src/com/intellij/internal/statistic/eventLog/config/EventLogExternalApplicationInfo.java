// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.config;

import com.intellij.internal.statistic.eventLog.DataCollectorDebugLogger;
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo;
import org.jetbrains.annotations.NotNull;

public class EventLogExternalApplicationInfo implements EventLogApplicationInfo {
  private final DataCollectorDebugLogger myLogger;

  private final String myTemplateUrl;
  private final String myProductCode;
  private final boolean myIsInternal;
  private final boolean myIsTest;

  public EventLogExternalApplicationInfo(@NotNull String templateUrl, @NotNull String productCode,
                                         boolean isInternal, boolean isTest,
                                         @NotNull DataCollectorDebugLogger logger) {
    myTemplateUrl = templateUrl;
    myProductCode = productCode;
    myIsInternal = isInternal;
    myIsTest = isTest;
    myLogger = logger;
  }

  @NotNull
  @Override
  public String getTemplateUrl() {
    return myTemplateUrl;
  }

  @NotNull
  @Override
  public String getProductCode() {
    return myProductCode;
  }

  @Override
  public boolean isInternal() {
    return myIsInternal;
  }

  @Override
  public boolean isTest() {
    return myIsTest;
  }

  @NotNull
  @Override
  public DataCollectorDebugLogger getLogger() {
    return myLogger;
  }
}
