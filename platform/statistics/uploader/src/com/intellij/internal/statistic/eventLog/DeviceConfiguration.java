// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class DeviceConfiguration implements DeviceConfigurationHolder {
  private final String myDeviceId;
  private final int myBucket;
  private final MachineId myMachineId;

  public DeviceConfiguration(@NotNull String deviceId, int bucket, @NotNull MachineId machineId) {
    myDeviceId = deviceId;
    myBucket = bucket;
    myMachineId = machineId;
  }

  @Override
  public String getDeviceId() {
    return myDeviceId;
  }

  @Override
  public int getBucket() {
    return myBucket;
  }

  @Override
  @NotNull
  public MachineId getMachineId() {
    return myMachineId;
  }
}
