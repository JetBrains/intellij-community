// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.OperationDescriptor;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public class InternalOperationDescriptor
  implements OperationDescriptor, org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor, Serializable {
  private final Object id;
  private final String name;
  private final String displayName;
  private final OperationDescriptor parent;

  public InternalOperationDescriptor(Object id, String name, String displayName, OperationDescriptor parent) {
    this.id = id;
    this.name = name;
    this.displayName = displayName;
    this.parent = parent;
  }

  @Override
  public Object getId() {
    return id;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public Object getParentId() {
    return parent instanceof InternalOperationDescriptor ? ((InternalOperationDescriptor)parent).id : null;
  }

  @Override
  public OperationDescriptor getParent() {
    return parent;
  }

  public String toString() {
    return getDisplayName();
  }
}
