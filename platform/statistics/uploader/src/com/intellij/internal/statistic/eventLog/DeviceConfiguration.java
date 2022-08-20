// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Use {@link EventLogInternalSendConfig} because it contains both information about recorder and device
 * Kept for compatibility with TBE.
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public class DeviceConfiguration {
  private final String myDeviceId;
  private final int myBucket;
  private final MachineId myMachineId;

  public DeviceConfiguration(@NotNull String deviceId, int bucket, @NotNull MachineId machineId) {
    myDeviceId = deviceId;
    myBucket = bucket;
    myMachineId = machineId;
  }

  public String getDeviceId() {
    return myDeviceId;
  }

  public int getBucket() {
    return myBucket;
  }

  @NotNull
  public MachineId getMachineId() {
    return myMachineId;
  }
}
