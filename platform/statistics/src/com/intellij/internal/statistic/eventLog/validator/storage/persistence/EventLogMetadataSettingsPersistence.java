// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import java.util.*;

@State(name = "EventLogWhitelist", storages = @Storage(StoragePathMacros.CACHE_FILE))
public final class EventLogMetadataSettingsPersistence implements PersistentStateComponent<Element> {
  private final Map<String, Long> myLastModifications = new HashMap<>();
  private final Map<String, EventsSchemePathSettings> myRecorderToPathSettings = new HashMap<>();
  private final Map<String, EventLogExternalOptions> myOptions = new HashMap<>();

  private static final String MODIFY = "update";
  private static final String RECORDER_ID = "recorder-id";
  private static final String LAST_MODIFIED = "last-modified";
  private static final String PATH = "path";
  private static final String CUSTOM_PATH = "custom-path";
  private static final String USE_CUSTOM_PATH = "use-custom-path";
  private static final String OPTIONS = "options";
  private static final String OPTION = "option";
  private static final String OPTION_NAME = "name";
  private static final String OPTION_VALUE = "value";
  private final Object myOptionsLock = new Object();

  public static EventLogMetadataSettingsPersistence getInstance() {
    return ApplicationManager.getApplication().getService(EventLogMetadataSettingsPersistence.class);
  }

  public @NotNull Map<String, String> getOptions(@NotNull String recorderId) {
    synchronized (myOptionsLock) {
      EventLogExternalOptions options = myOptions.get(recorderId);
      if (options == null) return Collections.emptyMap();
      return options.getOptions();
    }
  }

  public void setOptions(@NotNull String recorderId, Map<String, String> options) {
    synchronized (myOptionsLock) {
      if (!myOptions.containsKey(recorderId)) {
        myOptions.put(recorderId, new EventLogExternalOptions());
      }
      myOptions.get(recorderId).putOptions(options);
    }
  }

  public @NotNull Map<String, String> updateOptions(@NotNull String recorderId, @NotNull Map<String, String> newOptions) {
    synchronized (myOptionsLock) {
      Map<String, String> persistedOptions = getOptions(recorderId);
      Map<String, String> changedOptions = new HashMap<>();
      for (Map.Entry<String, String> newOption : newOptions.entrySet()) {
        String value = persistedOptions.get(newOption.getKey());
        String newValue = newOption.getValue();
        if (newValue != null && !StringUtil.equals(value, newValue)) {
          changedOptions.put(newOption.getKey(), newValue);
        }
      }
      setOptions(recorderId, changedOptions);
      return changedOptions;
    }
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

    synchronized (myOptionsLock) {
      myOptions.clear();
      for (Element options : element.getChildren(OPTIONS)) {
        String recorderId = options.getAttributeValue(RECORDER_ID);
        if (recorderId != null) {
          myOptions.put(recorderId, new EventLogExternalOptions().deserialize(options));
        }
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

    for (Map.Entry<String, EventLogExternalOptions> entry : myOptions.entrySet()) {
      Element options = new Element(OPTIONS);
      options.setAttribute(RECORDER_ID, entry.getKey());
      for (Element option : entry.getValue().serialize()) {
        options.addContent(option);
      }
      element.addContent(options);
    }

    return element;
  }

  private static class EventLogExternalOptions {
    private final Map<String, String> myOptions = new HashMap<>();

    public @NotNull Map<String, String> getOptions() {
      return new HashMap<>(myOptions);
    }

    public void putOptions(@NotNull Map<String, String> options) {
      myOptions.putAll(options);
    }

    @NotNull
    public List<Element> serialize() {
      List<Element> result = new ArrayList<>();
      for (Map.Entry<String, String> entry : myOptions.entrySet()) {
        Element option = new Element(OPTION);
        option.setAttribute(OPTION_NAME, entry.getKey());
        option.setAttribute(OPTION_VALUE, entry.getValue());
        result.add(option);
      }
      return result;
    }

    @NotNull
    public EventLogExternalOptions deserialize(@NotNull Element root) {
      myOptions.clear();
      for (Element option : root.getChildren(OPTION)) {
        String name = option.getAttributeValue(OPTION_NAME);
        String value = option.getAttributeValue(OPTION_VALUE);
        if (name != null && value != null) {
          myOptions.put(name, value);
        }
      }
      return this;
    }
  }
}