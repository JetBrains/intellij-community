// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.validator.storage.EventLogMetadataLoader;
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Service(Service.Level.APP)
public final class EventLogConfigOptionsService {
  public static final Topic<EventLogConfigOptionsListener> TOPIC
    = new Topic<>(EventLogConfigOptionsListener.class, Topic.BroadcastDirection.NONE);

  private static final String DATA_THRESHOLD = "dataThreshold";
  private static final String GROUP_THRESHOLD = "groupDataThreshold";
  private static final String GROUP_ALERT_THRESHOLD = "groupAlertThreshold";
  public static final String MACHINE_ID_SALT = "id_salt";
  public static final String MACHINE_ID_SALT_REVISION = "id_salt_revision";

  private static final String[] ourOptions = new String[]{DATA_THRESHOLD, GROUP_THRESHOLD, GROUP_ALERT_THRESHOLD,
    MACHINE_ID_SALT_REVISION, MACHINE_ID_SALT};

  public static EventLogConfigOptionsService getInstance() {
    return ApplicationManager.getApplication().getService(EventLogConfigOptionsService.class);
  }

  public void updateOptions(@NotNull String recorderId, @NotNull EventLogMetadataLoader loader) {
    EventLogMetadataSettingsPersistence persisted = EventLogMetadataSettingsPersistence.getInstance();
    Map<String, String> changedOptions = new HashMap<>();
    for (String option : ourOptions) {
      String value = persisted.getOptionValue(recorderId, option);
      String newValue = loader.getOptionValue(option);
      if (newValue != null && !StringUtil.equals(value, newValue)) {
        persisted.setOptionValue(recorderId, option, newValue);
        changedOptions.put(option, newValue);
      }
    }
    if (!changedOptions.isEmpty()) {
      ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).optionsChanged(recorderId, changedOptions);
    }
  }

  public int getThreshold(@NotNull String recorderId) {
    return getPersistedOptionAsInt(recorderId, DATA_THRESHOLD);
  }

  public int getGroupThreshold(@NotNull String recorderId) {
    return getPersistedOptionAsInt(recorderId, GROUP_THRESHOLD);
  }

  public int getGroupAlertThreshold(@NotNull String recorderId) {
    return getPersistedOptionAsInt(recorderId, GROUP_ALERT_THRESHOLD);
  }

  public String getMachineIdSalt(@NotNull String recorderId) {
    return getPersistedOptionAsString(recorderId);
  }

  public int getMachineIdRevision(@NotNull String recorderId) {
    return getPersistedOptionAsInt(recorderId, MACHINE_ID_SALT_REVISION);
  }

  @Nullable
  private static String getPersistedOptionAsString(@NotNull String recorderId) {
    return EventLogMetadataSettingsPersistence.getInstance().getOptionValue(recorderId, MACHINE_ID_SALT);
  }

  private static int getPersistedOptionAsInt(@NotNull String recorderId, @NotNull String name) {
    return tryParseInt(EventLogMetadataSettingsPersistence.getInstance().getOptionValue(recorderId, name));
  }

  static int tryParseInt(@Nullable String value) {
    try {
      if (StringUtil.isNotEmpty(value)) {
        return Integer.parseInt(value);
      }
    }
    catch (NumberFormatException e) {
      //ignore
    }
    return -1;
  }

  public abstract static class EventLogThresholdConfigOptionsListener implements EventLogConfigOptionsListener {
    private final String myRecorderId;

    protected EventLogThresholdConfigOptionsListener(@NotNull String recorderId) {
      myRecorderId = recorderId;
    }

    @Override
    public void optionsChanged(@NotNull String recorderId, @NotNull Map<String, String> options) {
      if (StringUtil.equals(myRecorderId, recorderId)) {
        for (Map.Entry<String, String> entry : options.entrySet()) {
          String name = entry.getKey();
          String value = entry.getValue();
          if (StringUtil.equals(name, DATA_THRESHOLD)) {
            onThresholdChanged(tryParseInt(value));
          }
          if (StringUtil.equals(name, GROUP_THRESHOLD)) {
            onGroupThresholdChanged(tryParseInt(value));
          }
          if (StringUtil.equals(name, GROUP_ALERT_THRESHOLD)) {
            onGroupAlertThresholdChanged(tryParseInt(value));
          }
        }
      }
    }

    public abstract void onThresholdChanged(int newValue);

    public abstract void onGroupThresholdChanged(int newValue);

    public abstract void onGroupAlertThresholdChanged(int newValue);
  }
}
