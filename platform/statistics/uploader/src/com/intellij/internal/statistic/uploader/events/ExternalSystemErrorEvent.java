// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ExternalSystemErrorEvent extends ExternalSystemEvent {
  private final String myEvent;
  private final String myErrorClass;

  public ExternalSystemErrorEvent(long timestamp, @NotNull String event, @NotNull Throwable th, @Nullable String recorder) {
    this(timestamp, event, th.getClass().getName(), recorder);
  }

  public ExternalSystemErrorEvent(long timestamp, @NotNull String event, @NotNull String errorClass, @Nullable String recorder) {
    super(ExternalSystemEventType.ERROR, timestamp, recorder);
    myEvent = event;
    myErrorClass = errorClass;
  }

  @NotNull
  public String getEvent() {
    return myEvent;
  }

  @NotNull
  public String getErrorClass() {
    return myErrorClass;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExternalSystemErrorEvent event = (ExternalSystemErrorEvent)o;
    return Objects.equals(myEvent, event.myEvent) &&
           Objects.equals(myErrorClass, event.myErrorClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myEvent, myErrorClass);
  }
}
