// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator.storage.persistence;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
  private static final String RECORDER_ID = "recorder-id";
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

  /**
   * Every write replaces the whole snapshot, so {@link #getState()} serializes one consistent state without a lock.
   */
  private final AtomicReference<EventLogOptionsSnapshot> snapshot = new AtomicReference<>(EventLogOptionsSnapshot.EMPTY);

  public static EventLogMetadataSettingsPersistence getInstance() {
    return ApplicationManager.getApplication().getService(EventLogMetadataSettingsPersistence.class);
  }

  public boolean isInternal() {
    return snapshot.get().internal();
  }

  public void setInternal(boolean internal) {
    snapshot.updateAndGet(current -> current.withInternal(internal));
  }

  public @NotNull @Unmodifiable Map<String, String> getOptions(@NotNull String recorderId) {
    return snapshot.get().options().getOrDefault(recorderId, Collections.emptyMap());
  }

  public void setOptions(@NotNull String recorderId, Map<String, String> options) {
    snapshot.updateAndGet(current -> current.withOptions(recorderId, merge(current.options().get(recorderId), options)));
  }

  public @NotNull Map<String, String> updateOptions(@NotNull String recorderId, @NotNull @Unmodifiable Map<String, String> newOptions) {
    while (true) {
      EventLogOptionsSnapshot current = snapshot.get();
      Map<String, String> persistedOptions = current.options().get(recorderId);
      Map<String, String> changedOptions = new HashMap<>();
      for (Map.Entry<String, String> newOption : newOptions.entrySet()) {
        String value = persistedOptions == null ? null : persistedOptions.get(newOption.getKey());
        String newValue = newOption.getValue();
        if (newValue != null && !StringUtil.equals(value, newValue)) {
          changedOptions.put(newOption.getKey(), newValue);
        }
      }
      if (persistedOptions != null && changedOptions.isEmpty()) {
        return changedOptions;
      }
      EventLogOptionsSnapshot updated = current.withOptions(recorderId, merge(persistedOptions, changedOptions));
      if (snapshot.compareAndSet(current, updated)) {
        return changedOptions;
      }
    }
  }

  public @Nullable EventsSchemePathSettings getPathSettings(@NotNull String recorderId) {
    return snapshot.get().recorderToPathSettings().get(recorderId);
  }

  public void setPathSettings(@NotNull String recorderId, @NotNull EventsSchemePathSettings settings) {
    snapshot.updateAndGet(current -> current.withPathSettings(recorderId, settings));
  }

  public @Nullable String getBuildNumber(@NotNull String recorderId) {
    return snapshot.get().recorderToBuildNumber().get(recorderId);
  }

  public void setBuildNumber(@NotNull String recorderId, @NotNull String buildNumber) {
    snapshot.updateAndGet(current -> current.withBuildNumber(recorderId, buildNumber));
  }

  @Override
  public void loadState(final @NotNull Element element) {
    Element internalElement = element.getChild(INTERNAL);
    boolean internal = internalElement != null && Boolean.parseBoolean(internalElement.getValue());

    Map<String, String> recorderToBuildNumber = new HashMap<>();
    for (Element build : element.getChildren(BUILD)) {
      final String recorder = build.getAttributeValue(RECORDER_ID);
      if (StringUtil.isNotEmpty(recorder)) {
        final String buildNumber = build.getAttributeValue(BUILD_NUMBER);
        recorderToBuildNumber.put(recorder, buildNumber);
      }
    }

    Map<String, EventsSchemePathSettings> recorderToPathSettings = new HashMap<>();
    for (Element path : element.getChildren(PATH)) {
      final String recorder = path.getAttributeValue(RECORDER_ID);
      if (StringUtil.isNotEmpty(recorder)) {
        String customPath = path.getAttributeValue(CUSTOM_PATH);
        if (customPath == null) continue;
        boolean useCustomPath = parseUseCustomPath(path);
        recorderToPathSettings.put(recorder, new EventsSchemePathSettings(customPath, useCustomPath));
      }
    }

    Map<String, Map<String, String>> options = new HashMap<>();
    for (Element recorderOptions : element.getChildren(OPTIONS)) {
      String recorderId = recorderOptions.getAttributeValue(RECORDER_ID);
      if (recorderId != null) {
        options.put(recorderId, deserializeOptions(recorderOptions));
      }
    }

    snapshot.set(new EventLogOptionsSnapshot(internal,
                                             Collections.unmodifiableMap(recorderToPathSettings),
                                             Collections.unmodifiableMap(options),
                                             Collections.unmodifiableMap(recorderToBuildNumber)));
  }

  private static boolean parseUseCustomPath(@NotNull Element update) {
    try {
      return Boolean.parseBoolean(update.getAttributeValue(USE_CUSTOM_PATH, "false"));
    }
    catch (NumberFormatException e) {
      return false;
    }
  }

  @Override
  public Element getState() {
    final EventLogOptionsSnapshot current = snapshot.get();
    final Element element = new Element("state");

    // store only 'true' values
    if (current.internal()) {
      Element internalElement = new Element(INTERNAL);
      internalElement.setText(Boolean.toString(true));
      element.addContent(internalElement);
    }

    for (Map.Entry<String, String> entry : current.recorderToBuildNumber().entrySet()) {
      final Element build = new Element(BUILD);
      build.setAttribute(RECORDER_ID, entry.getKey());
      build.setAttribute(BUILD_NUMBER, entry.getValue());
      element.addContent(build);
    }

    for (Map.Entry<String, EventsSchemePathSettings> entry : current.recorderToPathSettings().entrySet()) {
      final Element path = new Element(PATH);
      path.setAttribute(RECORDER_ID, entry.getKey());
      EventsSchemePathSettings value = entry.getValue();
      path.setAttribute(CUSTOM_PATH, value.getCustomPath());
      path.setAttribute(USE_CUSTOM_PATH, String.valueOf(value.isUseCustomPath()));
      element.addContent(path);
    }

    for (Map.Entry<String, Map<String, String>> entry : current.options().entrySet()) {
      Element options = new Element(OPTIONS);
      options.setAttribute(RECORDER_ID, entry.getKey());
      for (Map.Entry<String, String> option : entry.getValue().entrySet()) {
        Element optionElement = new Element(OPTION);
        optionElement.setAttribute(OPTION_NAME, option.getKey());
        optionElement.setAttribute(OPTION_VALUE, option.getValue());
        options.addContent(optionElement);
      }
      element.addContent(options);
    }

    return element;
  }

  private static @NotNull @Unmodifiable Map<String, String> deserializeOptions(@NotNull Element root) {
    Map<String, String> options = new HashMap<>();
    for (Element option : root.getChildren(OPTION)) {
      String name = option.getAttributeValue(OPTION_NAME);
      String value = option.getAttributeValue(OPTION_VALUE);
      if (name != null && value != null) {
        options.put(name, value);
      }
    }
    return Collections.unmodifiableMap(options);
  }

  private static @NotNull @Unmodifiable Map<String, String> merge(@Nullable Map<String, String> persisted,
                                                                  @NotNull Map<String, String> options) {
    Map<String, String> merged = persisted == null ? new HashMap<>() : new HashMap<>(persisted);
    merged.putAll(options);
    return Collections.unmodifiableMap(merged);
  }

  private static <V> @NotNull @Unmodifiable Map<String, V> with(@NotNull Map<String, V> map, @NotNull String key, V value) {
    Map<String, V> copy = new HashMap<>(map);
    copy.put(key, value);
    return Collections.unmodifiableMap(copy);
  }

  /**
   * An immutable state of the component. The maps are unmodifiable, and so are the option maps in {@code options}.
   */
  private record EventLogOptionsSnapshot(
    boolean internal,
    @NotNull @Unmodifiable Map<String, EventsSchemePathSettings> recorderToPathSettings,
    @NotNull @Unmodifiable Map<String, @Unmodifiable Map<String, String>> options,
    @NotNull @Unmodifiable Map<String, String> recorderToBuildNumber
  ) {
    static final EventLogOptionsSnapshot EMPTY = new EventLogOptionsSnapshot(false, Map.of(), Map.of(), Map.of());

    @NotNull EventLogOptionsSnapshot withInternal(boolean internal) {
      return new EventLogOptionsSnapshot(internal, recorderToPathSettings, options, recorderToBuildNumber);
    }

    @NotNull EventLogOptionsSnapshot withPathSettings(@NotNull String recorderId, @NotNull EventsSchemePathSettings settings) {
      return new EventLogOptionsSnapshot(internal, with(recorderToPathSettings, recorderId, settings), options, recorderToBuildNumber);
    }

    @NotNull EventLogOptionsSnapshot withOptions(@NotNull String recorderId, @NotNull @Unmodifiable Map<String, String> recorderOptions) {
      return new EventLogOptionsSnapshot(internal, recorderToPathSettings, with(options, recorderId, recorderOptions), recorderToBuildNumber);
    }

    @NotNull EventLogOptionsSnapshot withBuildNumber(@NotNull String recorderId, @NotNull String buildNumber) {
      return new EventLogOptionsSnapshot(internal, recorderToPathSettings, options, with(recorderToBuildNumber, recorderId, buildNumber));
    }
  }
}
