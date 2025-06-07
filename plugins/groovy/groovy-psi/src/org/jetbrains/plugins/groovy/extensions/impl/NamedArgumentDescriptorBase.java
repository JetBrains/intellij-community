// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.extensions.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor;

public class NamedArgumentDescriptorBase implements NamedArgumentDescriptor {

  private final @NotNull Priority myPriority;

  public NamedArgumentDescriptorBase() {
    myPriority = Priority.ALWAYS_ON_TOP;
  }

  public NamedArgumentDescriptorBase(@NotNull Priority priority) {
    myPriority = priority;
  }

  @Override
  public @NotNull Priority getPriority() {
    return myPriority;
  }
}
