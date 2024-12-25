// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader.events;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ExternalUploadFinishedEvent extends ExternalSystemEvent {
  private final String myError;

  public ExternalUploadFinishedEvent(long timestamp, @Nullable String error, @Nullable String recorderId) {
    super(ExternalSystemEventType.FINISHED, timestamp, recorderId);
    myError = error;
  }

  public @Nullable String getError() {
    return myError;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ExternalUploadFinishedEvent event = (ExternalUploadFinishedEvent)o;
    return Objects.equals(myError, event.myError);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myError);
  }
}
