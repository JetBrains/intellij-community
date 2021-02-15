// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.StatusEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class InternalStatusEvent extends InternalProgressEvent implements StatusEvent {
  private final long total;
  private final long progress;
  private final String unit;

  public InternalStatusEvent(long eventTime, String displayName, OperationDescriptor descriptor, long total, long progress, String unit) {
    super(eventTime, displayName, descriptor);
    this.total = total;
    this.progress = progress;
    this.unit = unit;
  }

  @Override
  public long getProgress() {
    return this.progress;
  }

  @Override
  public long getTotal() {
    return this.total;
  }

  @Override
  public String getUnit() {
    return this.unit;
  }
}
