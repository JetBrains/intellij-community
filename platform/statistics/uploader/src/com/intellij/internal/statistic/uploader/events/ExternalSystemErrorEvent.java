// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ExternalSystemErrorEvent extends ExternalSystemEvent {
  private final String myErrorClass;

  public ExternalSystemErrorEvent(long timestamp, @NotNull Throwable th, @Nullable String recorder) {
    this(timestamp, th.getClass().getName(), recorder);
  }

  public ExternalSystemErrorEvent(long timestamp, @NotNull String errorClass, @Nullable String recorder) {
    super(ExternalSystemEventType.ERROR, timestamp, recorder);
    myErrorClass = errorClass;
  }

  public @NotNull String getErrorClass() {
    return myErrorClass;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExternalSystemErrorEvent event = (ExternalSystemErrorEvent)o;
    return Objects.equals(myErrorClass, event.myErrorClass);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myErrorClass);
  }
}
