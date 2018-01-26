// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.tracking;

import org.jetbrains.annotations.NotNull;

public enum TrackingType {
  CREATION("Track Constructors");

  private final String myDescription;

  TrackingType(@SuppressWarnings("SameParameterValue") @NotNull String description) {
    myDescription = description;
  }

  @NotNull
  public String description() {
    return myDescription;
  }
}
