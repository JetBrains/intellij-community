// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.test.TestFinishEvent;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.gradle.tooling.events.test.TestOperationResult;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalFinishEvent;

@ApiStatus.Internal
public class InternalTestFinishEvent extends InternalFinishEvent implements TestFinishEvent {
  public InternalTestFinishEvent(long eventTime,
                                 String displayName,
                                 OperationDescriptor descriptor,
                                 OperationResult result) {
    super(eventTime, displayName, descriptor, result);
  }

  @Override
  public TestOperationResult getResult() {
    return (TestOperationResult)super.getResult();
  }

  @Override
  public TestOperationDescriptor getDescriptor() {
    return (TestOperationDescriptor)super.getDescriptor();
  }
}
