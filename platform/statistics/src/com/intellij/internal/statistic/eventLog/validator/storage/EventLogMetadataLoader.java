// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage;

import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataLoadException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface EventLogMetadataLoader {

  long getLastModifiedOnServer();

  @NotNull
  String loadMetadataFromServer() throws EventLogMetadataLoadException;

  @NotNull Map<String, String> getOptionValues();
}
