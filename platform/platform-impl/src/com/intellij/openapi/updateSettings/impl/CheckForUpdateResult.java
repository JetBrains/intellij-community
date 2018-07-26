// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CheckForUpdateResult {
  private final UpdateStrategy.State myState;
  private final BuildInfo myNewBuild;
  private final UpdateChannel myUpdatedChannel;
  private final PatchInfo myPatch;
  private final ChainInfo myPatchChain;
  private final Exception myError;

  CheckForUpdateResult(@Nullable BuildInfo newBuild,
                       @Nullable UpdateChannel updatedChannel,
                       @Nullable PatchInfo patch,
                       @Nullable ChainInfo chain) {
    myState = UpdateStrategy.State.LOADED;
    myNewBuild = newBuild;
    myUpdatedChannel = updatedChannel;
    myPatch = patch;
    myPatchChain = chain;
    myError = null;
  }

  CheckForUpdateResult(@NotNull UpdateStrategy.State state, @Nullable Exception e) {
    myState = state;
    myNewBuild = null;
    myUpdatedChannel = null;
    myPatch = null;
    myPatchChain = null;
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

  public @Nullable PatchInfo getPatch() {
    return myPatch;
  }

  public @Nullable ChainInfo getPatchChain() {
    return myPatchChain;
  }

  public @Nullable Exception getError() {
    return myError;
  }
}