// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.validator.storage.EventLogMetadataLoader;
import com.intellij.internal.statistic.eventLog.validator.storage.persistence.EventLogMetadataSettingsPersistence;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EventLogConfigOptionsService {
  public static final Topic<EventLogConfigOptionsListener> TOPIC
    = Topic.create("EventLogExternalConfigOptionsListener", EventLogConfigOptionsListener.class);

  private static final String DATA_THRESHOLD = "dataThreshold";
  private static final String GROUP_THRESHOLD = "groupDataThreshold";
  private static final String GROUP_ALERT_THRESHOLD = "groupAlertThreshold";

  private static final String[] ourOptions = new String[]{DATA_THRESHOLD, GROUP_THRESHOLD, GROUP_ALERT_THRESHOLD};

  public static EventLogConfigOptionsService getInstance() {
    return ApplicationManager.getApplication().getService(EventLogConfigOptionsService.class);
  }

  public void updateOptions(@NotNull String recorderId, @NotNull EventLogMetadataLoader loader) {
    EventLogMetadataSettingsPersistence persisted = EventLogMetadataSettingsPersistence.getInstance();
    for (String option : ourOptions) {
      String value = persisted.getOptionValue(recorderId, option);
      String newValue = loader.getOptionValue(option);
      if (newValue != null && !StringUtil.equals(value, newValue)) {
        persisted.setOptionValue(recorderId, option, newValue);

        ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).optionChanged(recorderId, option, newValue);
      }
    }
  }

  public int getThreshold(@NotNull String recorderId) {
    return getPersistedOption(recorderId, DATA_THRESHOLD);
  }

  public int getGroupThreshold(@NotNull String recorderId) {
    return getPersistedOption(recorderId, GROUP_THRESHOLD);
  }

  public int getGroupAlertThreshold(@NotNull String recorderId) {
    return getPersistedOption(recorderId, GROUP_ALERT_THRESHOLD);
  }

  private static int getPersistedOption(@NotNull String recorderId, @NotNull String name) {
    return tryParseInt(EventLogMetadataSettingsPersistence.getInstance().getOptionValue(recorderId, name));
  }

  private static int tryParseInt(@Nullable String value) {
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

  public interface EventLogConfigOptionsListener {
    void optionChanged(@NotNull String recorderId, @NotNull String name, @NotNull String value);
  }

  public abstract static class EventLogThresholdConfigOptionsListener implements EventLogConfigOptionsListener {
    private final String myRecorderId;

    protected EventLogThresholdConfigOptionsListener(@NotNull String recorderId) {
      myRecorderId = recorderId;
    }

    @Override
    public void optionChanged(@NotNull String recorderId, @NotNull String name, @NotNull String value) {
      if (StringUtil.equals(myRecorderId, recorderId)) {
        if (StringUtil.equals(name, DATA_THRESHOLD)) {
          onThresholdChanged(tryParseInt(value));
        }
        else if (StringUtil.equals(name, GROUP_THRESHOLD)) {
          onGroupThresholdChanged(tryParseInt(value));
        }
        else if (StringUtil.equals(name, GROUP_ALERT_THRESHOLD)) {
          onGroupAlertThresholdChanged(tryParseInt(value));
        }
      }
    }

    public abstract void onThresholdChanged(int newValue);

    public abstract void onGroupThresholdChanged(int newValue);

    public abstract void onGroupAlertThresholdChanged(int newValue);
  }
}
