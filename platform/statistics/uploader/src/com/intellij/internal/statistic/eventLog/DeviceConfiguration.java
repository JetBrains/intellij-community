// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class DeviceConfiguration {
  private final String myDeviceId;
  private final int myBucket;

  public DeviceConfiguration(@NotNull String deviceId, int bucket) {
    myDeviceId = deviceId;
    myBucket = bucket;
  }

  public String getDeviceId() {
    return myDeviceId;
  }

  public int getBucket() {
    return myBucket;
  }
}
