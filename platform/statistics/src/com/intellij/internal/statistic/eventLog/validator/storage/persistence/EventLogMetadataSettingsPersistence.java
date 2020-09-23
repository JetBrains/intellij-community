// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(name = "EventLogWhitelist", storages = @Storage(StoragePathMacros.CACHE_FILE))
public class EventLogMetadataSettingsPersistence implements PersistentStateComponent<Element> {
  private final Map<String, Long> myLastModifications = new HashMap<>();
  private final Map<String, EventsSchemePathSettings> myRecorderToPathSettings = new HashMap<>();

  private static final String MODIFY = "update";
  private static final String RECORDER_ID = "recorder-id";
  private static final String LAST_MODIFIED = "last-modified";
  private static final String PATH = "path";
  private static final String CUSTOM_PATH = "custom-path";
  private static final String USE_CUSTOM_PATH = "use-custom-path";

  public static EventLogMetadataSettingsPersistence getInstance() {
    return ApplicationManager.getApplication().getService(EventLogMetadataSettingsPersistence.class);
  }

  public long getLastModified(@NotNull String recorderId) {
    return myLastModifications.containsKey(recorderId) ? Math.max(myLastModifications.get(recorderId), 0) : 0;
  }

  public void setLastModified(@NotNull String recorderId, long lastUpdate) {
    myLastModifications.put(recorderId, Math.max(lastUpdate, 0));
  }

  @Nullable
  public EventsSchemePathSettings getPathSettings(@NotNull String recorderId) {
    return myRecorderToPathSettings.get(recorderId);
  }

  public void setPathSettings(@NotNull String recorderId, @NotNull EventsSchemePathSettings settings) {
    myRecorderToPathSettings.put(recorderId, settings);
  }

  @Override
  public void loadState(@NotNull final Element element) {
    myLastModifications.clear();
    for (Element update : element.getChildren(MODIFY)) {
      final String recorder = update.getAttributeValue(RECORDER_ID);
      if (StringUtil.isNotEmpty(recorder)) {
        final long lastUpdate = parseLastUpdate(update);
        myLastModifications.put(recorder, lastUpdate);
      }
    }

    myRecorderToPathSettings.clear();
    for (Element path : element.getChildren(PATH)) {
      final String recorder = path.getAttributeValue(RECORDER_ID);
      if (StringUtil.isNotEmpty(recorder)) {
        String customPath = path.getAttributeValue(CUSTOM_PATH);
        if (customPath == null) continue;
        boolean useCustomPath = parseUseCustomPath(path);
        myRecorderToPathSettings.put(recorder, new EventsSchemePathSettings(customPath, useCustomPath));
      }
    }
  }

  private static boolean parseUseCustomPath(@NotNull Element update) {
    try {
      return Boolean.parseBoolean(update.getAttributeValue(USE_CUSTOM_PATH, "false"));
    }
    catch (NumberFormatException e) {
      return false;
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
      final Element update = new Element(MODIFY);
      update.setAttribute(RECORDER_ID, entry.getKey());
      update.setAttribute(LAST_MODIFIED, String.valueOf(entry.getValue()));
      element.addContent(update);
    }

    for (Map.Entry<String, EventsSchemePathSettings> entry : myRecorderToPathSettings.entrySet()) {
      final Element path = new Element(PATH);
      path.setAttribute(RECORDER_ID, entry.getKey());
      EventsSchemePathSettings value = entry.getValue();
      path.setAttribute(CUSTOM_PATH, value.getCustomPath());
      path.setAttribute(USE_CUSTOM_PATH, String.valueOf(value.isUseCustomPath()));
      element.addContent(path);
    }

    return element;
  }
}