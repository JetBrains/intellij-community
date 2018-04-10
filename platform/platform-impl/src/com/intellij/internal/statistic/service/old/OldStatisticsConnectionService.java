// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.old;

import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated  // to be removed in 2018.1x
public class OldStatisticsConnectionService extends StatisticsConnectionService {
  public OldStatisticsConnectionService() {
    super(((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getStatisticsSettingsUrl(),
          ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getStatisticsServiceUrl());
  }
}
