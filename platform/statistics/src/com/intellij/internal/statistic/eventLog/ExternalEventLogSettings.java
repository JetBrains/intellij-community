// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Allows overriding certain AP settings from JB plugins.
 * <br/>
 * The intention is to let customer organizations acquire and manage both the data collected by the analytics platform in the IDEs
 * and data sharing consent within the organization with the help of Toolbox Enterprise. These settings will be exposed for the
 * customer organization admins to set.
 * <br/>
 * Only one instance of this EP can be used - provided by Toolbox Enterprise plugin.
 */
@ApiStatus.Internal
public interface ExternalEventLogSettings {
  ExtensionPointName<ExternalEventLogSettings> EP_NAME = new ExtensionPointName<>("com.intellij.statistic.eventLog.externalEventLogSettings");

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

  /**
   * Override global setting that enables log uploading see {@link StatisticsUploadAssistant#isSendAllowed()}
   *
   * @return true if log uploading must be force-enabled
   * @deprecated overriding setting to enable uploading is no longer possible -
   * only force disable is supported, see {@link ExternalEventLogSettings#forceDisableCollectionConsent()}
   */
  @Deprecated(since = "2023.1")
  default boolean isSendAllowedOverride() {
    return false;
  }

  /**
   * Override global setting that enables collection of statistics by any logger {@link StatisticsUploadAssistant#isCollectAllowed()}
   *
   * @return true if statistics collection must be force-enabled
   * @deprecated overriding setting to enable collection and recording is no longer possible -
   * only force collection not connected with recording to file is supported, see {@link ExternalEventLogSettings#forceCollectionWithoutRecord()}
   */
  @Deprecated(since = "2023.1")
  default boolean isCollectAllowedOverride() {
    return false;
  }

  /**
   * Override global setting that enables collection of statistics by any logger, see {@link StatisticsUploadAssistant#isCollectAllowed()}
   * <br/>
   * Does not affect {@link ExternalEventLogSettings#forceCollectionWithoutRecord()}
   *
   * @return true if log collection must be force-disabled even with accepted user consent
   * */
  default boolean forceDisableCollectionConsent() {
    return false;
  }

  /**
   * Enables statistics logs collection independently of recording to file ({@link StatisticsEventLoggerProvider#isRecordEnabled()}) for <b>supported</b> loggers.
   * <br/>
   * Logger must implement {@link StatisticsEventLoggerProviderExt}.
   * <br/>
   * Is not affected by {@link ExternalEventLogSettings#forceDisableCollectionConsent()}
   *
   * @return true if statistics collection must be force-enabled by supported logger
   */
  default boolean forceCollectionWithoutRecord() {
    return false;
  }

  /**
   * Provide extra headers to AP log upload requests. E.g. a shared secret to fence off data pollution
   */
  @NotNull Map<String, String> getExtraLogUploadHeaders();

  /**
   * Provides implementations of {@link StatisticsEventLogListener} to be used in {@link EventLogListenersManager}
   * <br/>
   * This method will be called only once.
   * */
  default @Nullable StatisticsEventLogListener getEventLogListener() {
    return null;
  }
}
