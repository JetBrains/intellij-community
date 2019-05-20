// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.persistence;

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

@State(
  name = "EventLogWhitelist",
  storages = @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED)
)
public class EventLogWhitelistSettingsPersistence implements PersistentStateComponent<Element> {
  private final Map<String, Long> myLastModifications = new HashMap<>();

  private static final String WHITELIST_MODIFY = "update";
  private static final String RECORDER_ID = "recorder-id";
  private static final String LAST_MODIFIED = "last-modified";

  public static EventLogWhitelistSettingsPersistence getInstance() {
    return ServiceManager.getService(EventLogWhitelistSettingsPersistence.class);
  }

  public long getLastModified(@NotNull String recorderId) {
    return myLastModifications.containsKey(recorderId) ? Math.max(myLastModifications.get(recorderId), 0) : 0;
  }

  public void setLastModified(@NotNull String recorderId, long lastUpdate) {
    myLastModifications.put(recorderId, Math.max(lastUpdate, 0));
  }

  @Override
  public void loadState(@NotNull final Element element) {
    myLastModifications.clear();
    for (Element update : element.getChildren(WHITELIST_MODIFY)) {
      final String recorder = update.getAttributeValue(RECORDER_ID);
      if (StringUtil.isNotEmpty(recorder)) {
        final long lastUpdate = parseLastUpdate(update);
        myLastModifications.put(recorder, lastUpdate);
      }
    }
  }

  private static long parseLastUpdate(@NotNull Element update) {
    try {
      return Long.parseLong(update.getAttributeValue(LAST_MODIFIED, "0"));
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  @Override
  public Element getState() {
    final Element element = new Element("state");

    for (Map.Entry<String, Long> entry : myLastModifications.entrySet()) {
      final Element update = new Element(WHITELIST_MODIFY);
      update.setAttribute(RECORDER_ID, entry.getKey());
      update.setAttribute(LAST_MODIFIED, String.valueOf(entry.getValue()));
      element.addContent(update);
    }
    return element;
  }

  @Override
  public void noStateLoaded() {

  }
}