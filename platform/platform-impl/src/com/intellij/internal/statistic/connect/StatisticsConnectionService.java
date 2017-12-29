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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class StatisticsConnectionService extends SettingsConnectionService {
  private static final String PERMISSION_ATTR_NAME = "permitted";
  private static final String DISABLED = "disabled";

  public StatisticsConnectionService() {
    this(((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getStatisticsSettingsUrl(),
         ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getStatisticsServiceUrl());
  }

  public StatisticsConnectionService(@NotNull String settingsUrl, @Nullable String defaultServiceUrl) {
    super(settingsUrl, defaultServiceUrl);
  }

  @NotNull
  @Override
  public String[] getAttributeNames() {
    return ArrayUtil.mergeArrays(super.getAttributeNames(), PERMISSION_ATTR_NAME, DISABLED);
  }

  public boolean isTransmissionPermitted() {
    final String permitted = getSettingValue(PERMISSION_ATTR_NAME);
    return permitted == null || Boolean.parseBoolean(permitted);
  }

  @NotNull
  public Set<String> getDisabledGroups() {
    final String disabledGroupsString = getSettingValue(DISABLED);
    if (disabledGroupsString == null) {
      return Collections.emptySet();
    }

    final List<String> disabledGroupsList = StringUtil.split(disabledGroupsString, ",");
    return ContainerUtil.map2Set(disabledGroupsList, s -> s.trim());
  }
}
