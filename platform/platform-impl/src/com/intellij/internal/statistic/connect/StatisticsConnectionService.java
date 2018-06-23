/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.internal.statistic.connect;

import com.intellij.facet.frameworks.SettingsConnectionService;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// this service connects to jetbrains.com resources and gets actual info
// about statistics service connections
// 1. url: wheren to post statistics data
// 2. permitted: true/false. statistics could be stopped remotely. if false UsageCollectors won't be started
public class StatisticsConnectionService extends SettingsConnectionService {
  private static final String PERMISSION_ATTR_NAME = "permitted";

  public StatisticsConnectionService() {
    this("localhost", null);
  }

  public StatisticsConnectionService(@Nullable String settingsUrl, @Nullable String defaultServiceUrl) {
    super(settingsUrl, defaultServiceUrl);
  }

  @NotNull
  @Override
  public String[] getAttributeNames() {
    return ArrayUtil.mergeArrays(super.getAttributeNames(), PERMISSION_ATTR_NAME);
  }

  public boolean isTransmissionPermitted() {
    final String permitted = getSettingValue(PERMISSION_ATTR_NAME);
    return permitted == null || Boolean.parseBoolean(permitted);
  }
}
