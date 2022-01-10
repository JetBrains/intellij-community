// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link ExternalEventLogSettings} instead
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
@ApiStatus.Internal
public interface EventLogEndpointSubstitutor {
   ExtensionPointName<EventLogEndpointSubstitutor> EP_NAME = new ExtensionPointName<>("com.intellij.statistic.eventLog.eventLogEndpointSubstitutor");

  /**
   * Provides a custom endpoint for fetching configuration of a recorder specified by the recorderId
   * @return Remote endpoint URL of the related recorder or null if platform default should be used
   */
  @Nullable String getTemplateUrl(@NotNull String recorderId);
}
