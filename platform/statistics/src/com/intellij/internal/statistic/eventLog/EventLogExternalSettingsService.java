// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.NotNull;

/**
 * This class is a wrapper around {@link EventLogUploadSettingsService} it isn't deleted yet for backward compatibility.
 *
 * @deprecated Use {@link EventLogUploadSettingsService} directly.
 */
@Deprecated
public class EventLogExternalSettingsService extends EventLogUploadSettingsService {
  public EventLogExternalSettingsService(@NotNull String recorderId) {
    super(recorderId, new EventLogInternalApplicationInfo(recorderId, false));
  }

  /**
   * @deprecated Use {@link EventLogExternalSettingsService#getFeatureUsageSettings()} or create new instance with custom recorder id.
   */
  @Deprecated
  public static EventLogExternalSettingsService getInstance() {
    return getFeatureUsageSettings();
  }

  @NotNull
  public static EventLogExternalSettingsService getFeatureUsageSettings() {
    return new EventLogExternalSettingsService("FUS");
  }
}