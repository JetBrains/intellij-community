// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans;

import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public abstract class FSContextProvider {

  @Nullable
  public Map<String, String> context;

  protected FSContextProvider(@Nullable FUSUsageContext fusUsageContext) {
   context = fusUsageContext != null ? fusUsageContext.getData() : null;
  }

}
