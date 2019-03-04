// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import org.jetbrains.annotations.NotNull;

public class CVPInfo {
  private final String myOrigin;
  private final long myTotalLifeTime;
  private final long myTotalUseCount;
  private final long myCreatedCount;

  public CVPInfo(String origin, long totalLifeTime, long totalUseCount, long createdCount) {
    myOrigin = origin;
    myTotalLifeTime = totalLifeTime;
    myTotalUseCount = totalUseCount;
    myCreatedCount = createdCount;
  }

  @NotNull
  public String getOrigin() {
    return myOrigin;
  }

  public long getTotalLifeTime() {
    return myTotalLifeTime;
  }

  public long getTotalUseCount() {
    return myTotalUseCount;
  }

  public long getCreatedCount() {
    return myCreatedCount;
  }
}
