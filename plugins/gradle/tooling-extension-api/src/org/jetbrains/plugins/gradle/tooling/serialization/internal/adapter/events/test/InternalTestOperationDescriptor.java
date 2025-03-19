// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.TestOperationDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.InternalOperationDescriptor;

@ApiStatus.Internal
public class InternalTestOperationDescriptor extends InternalOperationDescriptor implements TestOperationDescriptor {
  public InternalTestOperationDescriptor(Object id, String name, String displayName, OperationDescriptor parent) {
    super(id, name, displayName, parent);
  }

  @Override
  public String getTestDisplayName() {
    return super.getDisplayName();
  }
}
