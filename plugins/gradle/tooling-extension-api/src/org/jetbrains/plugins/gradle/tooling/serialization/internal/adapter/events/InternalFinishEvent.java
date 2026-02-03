// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.OperationResult;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class InternalFinishEvent extends InternalProgressEvent implements FinishEvent {
  private final OperationResult result;

  public InternalFinishEvent(long eventTime, String displayName, OperationDescriptor descriptor, OperationResult result) {
    super(eventTime, displayName, descriptor);
    this.result = result;
  }

  @Override
  public OperationResult getResult() {
    return this.result;
  }
}
