// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link ExternalEventLogSettings} instead
 */
@Deprecated(forRemoval = true)
@ApiStatus.Internal
public interface EventLogEndpointSubstitutor {
   ExtensionPointName<EventLogEndpointSubstitutor> EP_NAME = new ExtensionPointName<>("com.intellij.statistic.eventLog.eventLogEndpointSubstitutor");

  /**
   * @deprecated Endpoint shouldn't depend on recorder id. Use {@link #getTemplateUrl()}
   */
  @Deprecated
  @Nullable String getTemplateUrl(@NotNull String recorderId);

  /**
   * Provides a custom endpoint for fetching configuration
   * @return Remote endpoint URL or null if platform default should be used
   */
  default @Nullable String getTemplateUrl() {
    return getTemplateUrl("UNDEFINED");
  }
}
