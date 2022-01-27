// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.persistence;

import com.intellij.ide.ConsentOptionsProvider;
import com.intellij.internal.statistic.eventLog.StatisticsSystemEventIdProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(
  name = "UsagesStatistic",
  storages = {
  @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, usePathMacroManager = false),
  @Storage(value = StoragePathMacros.CACHE_FILE, deprecated = true)
})
@Service
public final class UsageStatisticsPersistenceComponent implements PersistentStateComponent<Element>, StatisticsSystemEventIdProvider {
  public static final String USAGE_STATISTICS_XML = "usage.statistics.xml";

  private boolean isAllowedForEAP = true;
  private boolean isShowNotification = true;

  private static final String LAST_TIME_ATTR = "time";
  private static final String IS_ALLOWED_ATTR = "allowed";
  private static final String IS_ALLOWED_EAP_ATTR = "allowedEap";
  private static final String SHOW_NOTIFICATION_ATTR = "show-notification";
  private static final String SYSTEM_EVENT_ATTR = "system-event-id";
  private static final String EVENT_ID_ATTR = "id";
  private static final String RECORDER_ATTR = "recorder";
  private long mySentTime = 0;
  private final Map<String, Long> myRecorderToSystemEventIds = new HashMap<>();

  public long getLastTimeSent() {
    return mySentTime;
  }

  public void setSentTime(long time) {
    mySentTime = time;
  }

  public static UsageStatisticsPersistenceComponent getInstance() {
    return ApplicationManager.getApplication().getService(UsageStatisticsPersistenceComponent.class);
  }

  @Override
  public void loadState(@NotNull final Element element) {
    try {
      setSentTime(Long.parseLong(element.getAttributeValue(LAST_TIME_ATTR, "0")));
    }
    catch (NumberFormatException e) {
      setSentTime(0);
    }

    final String isAllowedEapValue = element.getAttributeValue(IS_ALLOWED_EAP_ATTR, "true");
    isAllowedForEAP = StringUtil.isEmptyOrSpaces(isAllowedEapValue) || Boolean.parseBoolean(isAllowedEapValue);

    // compatibility: if was previously allowed, transfer the setting to the new place
    final String isAllowedValue = element.getAttributeValue(IS_ALLOWED_ATTR);
    if (!StringUtil.isEmptyOrSpaces(isAllowedValue) && Boolean.parseBoolean(isAllowedValue)) {
      final ConsentOptionsProvider options = getConsentOptionsProvider();
      if (options != null) {
        options.setSendingUsageStatsAllowed(true);
      }
    }

    final String isShowNotificationValue = element.getAttributeValue(SHOW_NOTIFICATION_ATTR);
    setShowNotification(StringUtil.isEmptyOrSpaces(isShowNotificationValue) || Boolean.parseBoolean(isShowNotificationValue));

    myRecorderToSystemEventIds.clear();
    for (Element path : element.getChildren(SYSTEM_EVENT_ATTR)) {
      final String recorder = path.getAttributeValue(RECORDER_ATTR);
      if (StringUtil.isNotEmpty(recorder)) {
        try {
          long eventId = Long.parseLong(path.getAttributeValue(EVENT_ID_ATTR, "0"));
          myRecorderToSystemEventIds.put(recorder, eventId);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
  }

  @Override
  public Element getState() {
    Element element = new Element("state");

    long lastTimeSent = getLastTimeSent();
    if (lastTimeSent > 0) {
      element.setAttribute(LAST_TIME_ATTR, String.valueOf(lastTimeSent));
    }

    if (!isShowNotification()) {
      element.setAttribute(SHOW_NOTIFICATION_ATTR, "false");
    }

    if (!isAllowedForEAP) {
      element.setAttribute(IS_ALLOWED_EAP_ATTR, "false");
    }

    for (Map.Entry<String, Long> entry : myRecorderToSystemEventIds.entrySet()) {
      final Element event = new Element(SYSTEM_EVENT_ATTR);
      event.setAttribute(RECORDER_ATTR, entry.getKey());
      event.setAttribute(EVENT_ID_ATTR, String.valueOf(entry.getValue()));
      element.addContent(event);
    }

    return element;
  }

  public void setAllowed(boolean allowed) {
    final ConsentOptionsProvider options = getConsentOptionsProvider();
    if (options != null) {
      if (options.isEAP()) {
        isAllowedForEAP = allowed;
      }
      else {
        options.setSendingUsageStatsAllowed(allowed);
      }
    }
  }

  public boolean isAllowed() {
    final ConsentOptionsProvider options = getConsentOptionsProvider();
    if (options == null) {
      return false;
    }
    return options.isEAP() ? isAllowedForEAP : options.isSendingUsageStatsAllowed();
  }

  public void setShowNotification(boolean showNotification) {
    isShowNotification = showNotification;
  }

  public boolean isShowNotification() {
    return isShowNotification && !ApplicationManager.getApplication().isInternal();
  }

  @Nullable
  private static ConsentOptionsProvider getConsentOptionsProvider() {
    return ApplicationManager.getApplication().getService(ConsentOptionsProvider.class);
  }

  @Override
  public long getSystemEventId(@NotNull String recorderId) {
    Long eventId = myRecorderToSystemEventIds.get(recorderId);
    return eventId != null ? eventId : 0;
  }

  @Override
  public void setSystemEventId(@NotNull String recorderId, long eventId) {
    myRecorderToSystemEventIds.put(recorderId, eventId);
  }
}