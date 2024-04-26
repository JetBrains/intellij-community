// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.Destination;
import org.gradle.tooling.events.test.TestOutputDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationDescriptor;

@ApiStatus.Internal
public class InternalTestOutputDescriptor extends InternalOperationDescriptor implements TestOutputDescriptor {
  private final Destination destination;
  private final String message;

  public InternalTestOutputDescriptor(Object id, String name, String displayName, OperationDescriptor parent, Destination destination,
                                      String message) {
    super(id, name, displayName, parent);
    this.destination = destination;
    this.message = message;
  }

  @Override
  public Destination getDestination() {
    return this.destination;
  }

  @Override
  public String getMessage() {
    return this.message;
  }
}
