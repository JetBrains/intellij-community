// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Use {@link com.intellij.internal.statistic.eventLog.FeatureUsageData} instead because it supports named data.
 */
@Deprecated
public class FUSUsageContext {
  public static final FUSUsageContext OS_CONTEXT = create(getOSNameContextData());

  private static final byte MAX_DATA_SIZE = 5; // restricted by server
  private final Map<String, String> data;

  private FUSUsageContext(@NotNull String... data) {
    this.data = new LinkedHashMap<>(data.length);
    for (int i = 1; i < data.length + 1; i++) {
      String contextData = data[i - 1];
      if (StringUtil.isEmptyOrSpaces(contextData)) continue;
      this.data.put(getContextDataKey(i), contextData);
    }
  }

  @NotNull
  public Map<String, String> getData() {
    return Collections.unmodifiableMap(data);
  }

  public static String getOSNameContextData() {
    if (SystemInfo.isWindows) return "Windows";
    if (SystemInfo.isMac) return "Mac";
    if (SystemInfo.isLinux) return "Linux";
    return "Other";
  }

  public static FUSUsageContext create(@NotNull String... data) {
    assert data.length <= MAX_DATA_SIZE;
    return new FUSUsageContext(data);
  }

  @NotNull
  private static String getContextDataKey(int i) {
    return "data_" + i;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FUSUsageContext)) return false;
    return Objects.equals(data, ((FUSUsageContext)o).data);
  }

  @Override
  public int hashCode() {
    return data.hashCode();
  }
}