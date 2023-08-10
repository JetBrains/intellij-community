// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.config.EventLogOptions;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.internal.statistic.config.EventLogOptions.MACHINE_ID_SALT;
import static com.intellij.internal.statistic.config.EventLogOptions.MACHINE_ID_SALT_REVISION;

public abstract class EventLogRecorderConfigOptionsListener implements EventLogConfigOptionsListener {
  private final String myRecorderId;

  protected EventLogRecorderConfigOptionsListener(@NotNull String recorderId) {
    myRecorderId = recorderId;
  }

  @Override
  public void optionsChanged(@NotNull String recorderId, @NotNull Map<String, String> options) {
    if (StringUtil.equals(myRecorderId, recorderId)) {
      String machineIdSalt = options.get(MACHINE_ID_SALT);
      String machineIdSaltRevision = options.get(MACHINE_ID_SALT_REVISION);
      if (machineIdSalt != null && machineIdSaltRevision != null) {
        onMachineIdConfigurationChanged(machineIdSalt, EventLogOptions.tryParseInt(machineIdSaltRevision));
      }
    }
  }

  public abstract void onMachineIdConfigurationChanged(@Nullable String salt, int revision);
}
