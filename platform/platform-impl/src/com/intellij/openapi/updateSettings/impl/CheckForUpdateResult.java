// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CheckForUpdateResult {
  private final UpdateStrategy.State myState;
  private final BuildInfo myNewBuild;
  private final UpdateChannel myUpdatedChannel;
  private final Exception myError;

  CheckForUpdateResult(@Nullable BuildInfo newBuild, @Nullable UpdateChannel updatedChannel) {
    myState = UpdateStrategy.State.LOADED;
    myNewBuild = newBuild;
    myUpdatedChannel = updatedChannel;
    myError = null;
  }

  CheckForUpdateResult(@NotNull UpdateStrategy.State state, @Nullable Exception e) {
    myState = state;
    myNewBuild = null;
    myUpdatedChannel = null;
    myError = e;
  }

  public @NotNull UpdateStrategy.State getState() {
    return myState;
  }

  public @Nullable BuildInfo getNewBuild() {
    return myNewBuild;
  }

  public @Nullable UpdateChannel getUpdatedChannel() {
    return myUpdatedChannel;
  }

  public @Nullable Exception getError() {
    return myError;
  }
}