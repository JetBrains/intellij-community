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
public interface ExternalEventLogSettings extends ExternalEventLogListenerProvider {
  ExtensionPointName<ExternalEventLogSettings> EP_NAME = new ExtensionPointName<>("com.intellij.statistic.eventLog.externalEventLogSettings");

  /**
   * Override global setting that enables collection of statistics by any logger, see {@link StatisticsUploadAssistant#isCollectAllowed()}
   * <br/>
   * Does not affect {@link ExternalEventLogSettings#forceLoggingAlwaysEnabled()}
   *
   * @return true if log collection must be force-disabled even with accepted user consent
   * */
  default boolean forceDisableCollectionConsent() {
    return false;
  }

  /**
   * Should be implemented in case {@link ExternalEventLogSettings#forceDisableCollectionConsent()} method is implemented and returns true.
   * Allows to provide custom warning message under Data Sharing consent to explain why it is disabled.
   * If not implemented default warning text will be used.
   *
   *  @return Warning text that will be shown under Data Sharing consent in UI or {@code null}
  * */
  default @Nullable String getConsentWarning() {
    return null;
  }

  /**
   * Provide extra headers to AP log upload requests. E.g. a shared secret to fence off data pollution
   */
  @NotNull Map<String, String> getExtraLogUploadHeaders();

}
