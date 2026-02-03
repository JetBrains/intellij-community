// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.StartEvent;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class InternalStartEvent extends InternalProgressEvent implements StartEvent {
  public InternalStartEvent(long eventTime, String displayName, OperationDescriptor descriptor) {
    super(eventTime, displayName, descriptor);
  }
}
