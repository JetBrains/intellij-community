// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.util.BuildNumber;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CheckForUpdateResult {
  private final UpdateStrategy.State myState;
  private final BuildInfo myNewBuild;
  private final UpdateChannel myUpdatedChannel;
  private final Exception myError;

  public CheckForUpdateResult(@Nullable BuildInfo newBuild, @Nullable UpdateChannel updatedChannel) {
    myState = UpdateStrategy.State.LOADED;
    myNewBuild = newBuild;
    myUpdatedChannel = updatedChannel;
    myError = null;
  }

  public CheckForUpdateResult(@NotNull UpdateStrategy.State state, @Nullable Exception e) {
    myState = state;
    myNewBuild = null;
    myUpdatedChannel = null;
    myError = e;
  }

  @NotNull
  public UpdateStrategy.State getState() {
    return myState;
  }

  @Nullable
  public BuildInfo getNewBuild() {
    return myNewBuild;
  }

  @Nullable
  public PatchInfo findPatchForBuild(@NotNull BuildNumber build) {
    return myNewBuild == null ? null : ContainerUtil.find(myNewBuild.getPatches(), p -> p.isAvailable() && p.getFromBuild().compareTo(build) == 0);
  }

  @Nullable
  public UpdateChannel getUpdatedChannel() {
    return myUpdatedChannel;
  }

  @Nullable
  public Exception getError() {
    return myError;
  }
}