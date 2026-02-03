// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.test.TestOutputDescriptor;
import org.gradle.tooling.events.test.TestOutputEvent;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public class InternalTestOutputEvent implements TestOutputEvent, Serializable {
  private final long eventTime;
  private final TestOutputDescriptor descriptor;

  public InternalTestOutputEvent(long eventTime, TestOutputDescriptor descriptor) {
    this.eventTime = eventTime;
    this.descriptor = descriptor;
  }

  @Override
  public long getEventTime() {
    return this.eventTime;
  }

  @Override
  public String getDisplayName() {
    return this.descriptor.getDestination().toString() + ": " + this.descriptor.getMessage();
  }

  @Override
  public TestOutputDescriptor getDescriptor() {
    return this.descriptor;
  }
}
