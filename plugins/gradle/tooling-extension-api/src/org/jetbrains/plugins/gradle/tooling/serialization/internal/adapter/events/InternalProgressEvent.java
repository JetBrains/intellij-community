// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.ProgressEvent;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public abstract class InternalProgressEvent implements ProgressEvent, Serializable {
  private final long eventTime;
  private final String displayName;
  private final OperationDescriptor descriptor;

  public InternalProgressEvent(long eventTime, String displayName, OperationDescriptor descriptor) {
    this.eventTime = eventTime;
    this.displayName = displayName;
    this.descriptor = descriptor;
  }

  @Override
  public long getEventTime() {
    return this.eventTime;
  }

  @Override
  public String getDisplayName() {
    return this.displayName;
  }

  @Override
  public OperationDescriptor getDescriptor() {
    return this.descriptor;
  }

  public String toString() {
    return this.getDisplayName();
  }
}
