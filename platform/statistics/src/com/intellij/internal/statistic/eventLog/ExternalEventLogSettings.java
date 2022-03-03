// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Allows overriding certain AP settings from JB plugins.
 *
 * The intention is to let customer organizations acquire and manage both the data collected by the analytics platform in the IDEs
 * and data sharing consent within the organization with the help of Toolbox Enterprise. These settings will be exposed for the
 * customer organization admins to set.
 */
@ApiStatus.Internal
public interface ExternalEventLogSettings {
  ExtensionPointName<ExternalEventLogSettings> EP_NAME = new ExtensionPointName<>("com.intellij.statistic.eventLog.externalEventLogSettings");

  /**
   * Provides a custom endpoint for fetching configuration of a recorder specified by the recorderId
   * @return Remote endpoint URL of the related recorder or null if platform default should be used
   */
  @Nullable String getTemplateUrl(@NotNull String recorderId);

  /**
   * Override global setting that enables log uploading see {@link StatisticsUploadAssistant#isSendAllowed()}
   * @return true if log uploading must be force-enabled
   */
  boolean isSendAllowedOverride();

  /**
   * Override global setting that enables collection of statistics by any logger {@link StatisticsUploadAssistant#isCollectAllowed()}
   * @return true if statistics collection must be force-enabled
   */
  boolean isCollectAllowedOverride();

  /**
   * Provide extra headers to AP log upload requests. E.g. a shared secret to fence off data pollution
   * @return
   */
  @NotNull Map<String, String> getExtraLogUploadHeaders();
}
