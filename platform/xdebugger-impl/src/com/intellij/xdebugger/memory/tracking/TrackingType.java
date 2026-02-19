// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.memory.tracking;

import org.jetbrains.annotations.NotNull;

public enum TrackingType {
  CREATION("Track Constructors");

  private final String myDescription;

  TrackingType(@SuppressWarnings("SameParameterValue") @NotNull String description) {
    myDescription = description;
  }

  public @NotNull String description() {
    return myDescription;
  }
}
