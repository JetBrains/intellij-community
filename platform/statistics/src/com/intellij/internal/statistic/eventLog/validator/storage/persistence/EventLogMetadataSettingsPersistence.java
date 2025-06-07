// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * EventLogAllowedList storage is stored locally, not shared via Settings Sync, not exportable via Export Settings,
 * but can be migrated to another IDE on the same machine (Migrate Settings).
 * If there are multiple components that store state in the same file, they must have the same roamingType attribute value,
 * so we replaced a cache file to EventLogAllowedList.xml with a local roaming type.
 */
@ApiStatus.Internal
@State(
  name = "EventLogAllowedList",
  storages = @Storage(value = "EventLogAllowedList.xml", roamingType = RoamingType.LOCAL)
)
public final class EventLogMetadataSettingsPersistence implements PersistentStateComponent<Element> {
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
  private static final String INTERNAL = "internal";
  private static final String BUILD = "build";
  private static final String BUILD_NUMBER = "build-number";

  private final Object optionsLock = new Object();

  private boolean internal = false;
  private final Map<String, Long> lastModifications = new HashMap<>();
  private final Map<String, EventsSchemePathSettings> recorderToPathSettings = new HashMap<>();
  private final Map<String, EventLogExternalOptions> options = new HashMap<>();
  private final Map<String, String> recorderToBuildNumber = new HashMap<>();

  public static EventLogMetadataSettingsPersistence getInstance() {
    return ApplicationManager.getApplication().getService(EventLogMetadataSettingsPersistence.class);
  }

  public boolean isInternal() {
    return internal;
  }

  public void setInternal(boolean internal) {
    this.internal = internal;
  }

  public @NotNull Map<String, String> getOptions(@NotNull String recorderId) {
    synchronized (optionsLock) {
      EventLogExternalOptions options = this.options.get(recorderId);
      if (options == null) return Collections.emptyMap();
      return options.getOptions();
    }
  }

  public void setOptions(@NotNull String recorderId, Map<String, String> options) {
    synchronized (optionsLock) {
      if (!this.options.containsKey(recorderId)) {
        this.options.put(recorderId, new EventLogExternalOptions());
      }
      this.options.get(recorderId).putOptions(options);
    }
  }

  public @NotNull Map<String, String> updateOptions(@NotNull String recorderId, @NotNull @Unmodifiable Map<String, String> newOptions) {
    synchronized (optionsLock) {
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
    return lastModifications.containsKey(recorderId) ? Math.max(lastModifications.get(recorderId), 0) : 0;
  }

  public void setLastModified(@NotNull String recorderId, long lastUpdate) {
    lastModifications.put(recorderId, Math.max(lastUpdate, 0));
  }

  public @Nullable EventsSchemePathSettings getPathSettings(@NotNull String recorderId) {
    return recorderToPathSettings.get(recorderId);
  }

  public void setPathSettings(@NotNull String recorderId, @NotNull EventsSchemePathSettings settings) {
    recorderToPathSettings.put(recorderId, settings);
  }

  public @Nullable String getBuildNumber(@NotNull String recorderId) {
    return recorderToBuildNumber.get(recorderId);
  }

  public void setBuildNumber(@NotNull String recorderId, @NotNull String buildNumber) {
    recorderToBuildNumber.put(recorderId, buildNumber);
  }

  @Override
  public void loadState(final @NotNull Element element) {
      Element internalElement = element.getChild(INTERNAL);
      internal = internalElement != null && Boolean.parseBoolean(internalElement.getValue());

      lastModifications.clear();
      for (Element update : element.getChildren(MODIFY)) {
        final String recorder = update.getAttributeValue(RECORDER_ID);
        if (StringUtil.isNotEmpty(recorder)) {
          final long lastUpdate = parseLastUpdate(update);
          lastModifications.put(recorder, lastUpdate);
        }
      }

      recorderToBuildNumber.clear();
      for (Element build : element.getChildren(BUILD)) {
        final String recorder = build.getAttributeValue(RECORDER_ID);
        if (StringUtil.isNotEmpty(recorder)) {
          final String buildNumber = build.getAttributeValue(BUILD_NUMBER);
          recorderToBuildNumber.put(recorder, buildNumber);
        }
      }

      recorderToPathSettings.clear();
      for (Element path : element.getChildren(PATH)) {
        final String recorder = path.getAttributeValue(RECORDER_ID);
        if (StringUtil.isNotEmpty(recorder)) {
          String customPath = path.getAttributeValue(CUSTOM_PATH);
          if (customPath == null) continue;
          boolean useCustomPath = parseUseCustomPath(path);
          recorderToPathSettings.put(recorder, new EventsSchemePathSettings(customPath, useCustomPath));
        }
      }

      synchronized (optionsLock) {
        options.clear();
        for (Element options : element.getChildren(OPTIONS)) {
          String recorderId = options.getAttributeValue(RECORDER_ID);
          if (recorderId != null) {
            this.options.put(recorderId, new EventLogExternalOptions().deserialize(options));
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

    // store only 'true' values
    if (internal) {
      Element internalElement = new Element(INTERNAL);
      internalElement.setText(Boolean.toString(internal));
      element.addContent(internalElement);
    }

    for (Map.Entry<String, Long> entry : lastModifications.entrySet()) {
      final Element update = new Element(MODIFY);
      update.setAttribute(RECORDER_ID, entry.getKey());
      update.setAttribute(LAST_MODIFIED, String.valueOf(entry.getValue()));
      element.addContent(update);
    }

    for (Map.Entry<String, String> entry : recorderToBuildNumber.entrySet()) {
      final Element build = new Element(BUILD);
      build.setAttribute(RECORDER_ID, entry.getKey());
      build.setAttribute(BUILD_NUMBER, entry.getValue());
      element.addContent(build);
    }

    for (Map.Entry<String, EventsSchemePathSettings> entry : recorderToPathSettings.entrySet()) {
      final Element path = new Element(PATH);
      path.setAttribute(RECORDER_ID, entry.getKey());
      EventsSchemePathSettings value = entry.getValue();
      path.setAttribute(CUSTOM_PATH, value.getCustomPath());
      path.setAttribute(USE_CUSTOM_PATH, String.valueOf(value.isUseCustomPath()));
      element.addContent(path);
    }

    for (Map.Entry<String, EventLogExternalOptions> entry : options.entrySet()) {
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

    public @NotNull List<Element> serialize() {
      List<Element> result = new ArrayList<>();
      for (Map.Entry<String, String> entry : myOptions.entrySet()) {
        Element option = new Element(OPTION);
        option.setAttribute(OPTION_NAME, entry.getKey());
        option.setAttribute(OPTION_VALUE, entry.getValue());
        result.add(option);
      }
      return result;
    }

    public @NotNull EventLogExternalOptions deserialize(@NotNull Element root) {
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