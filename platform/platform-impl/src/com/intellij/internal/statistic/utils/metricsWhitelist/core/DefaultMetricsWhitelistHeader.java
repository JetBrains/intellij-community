// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils.metricsWhitelist.core;

import org.jetbrains.annotations.NotNull;

public class DefaultMetricsWhitelistHeader implements MetricsWhitelistHeader {
  private final boolean myIsDeprecated;
  private final String myVersion;

  public DefaultMetricsWhitelistHeader(boolean deprecated, @NotNull String version) {
    myIsDeprecated = deprecated;
    myVersion = version;
  }

  @Override
  public boolean isDeprecated() {
    return myIsDeprecated;
  }

  @NotNull
  @Override
  public String getVersion() {
    return myVersion;
  }
}
