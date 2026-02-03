// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.TestStartEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalStartEvent;

@ApiStatus.Internal
public class InternalTestStartEvent extends InternalStartEvent implements TestStartEvent {
  public InternalTestStartEvent(long eventTime, String displayName, OperationDescriptor descriptor) {
    super(eventTime, displayName, descriptor);
  }

  @Override
  public TestOperationDescriptor getDescriptor() {
    return (TestOperationDescriptor)super.getDescriptor();
  }
}
