// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.validator.rules;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class EventContext {
  public final String eventId;
  public final Map<String, Object> eventData;
  public final Map<String, Object> myPayload = new HashMap<>();

  private EventContext(@NotNull String eventId, @NotNull Map<String, Object> eventData) {
    this.eventId = eventId;
    this.eventData = unmodifiableOrEmptyMap(eventData);
  }

  @Contract(pure = true)
  public static @NotNull Map<String, Object> unmodifiableOrEmptyMap(@NotNull Map<String, Object> original) {
    int size = original.size();
    if (size == 0) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(original);
  }

  public static EventContext create(@NotNull String eventId, @NotNull Map<String, Object> eventData) {
    return new EventContext(eventId, eventData);
  }

  public <T> T getPayload(PayloadKey<T> key) {
    //noinspection unchecked
    return (T)myPayload.get(key.getKey());
  }

  public <T> void setPayload(PayloadKey<T> key, T value) {
    myPayload.put(key.getKey(), value);
  }
}
