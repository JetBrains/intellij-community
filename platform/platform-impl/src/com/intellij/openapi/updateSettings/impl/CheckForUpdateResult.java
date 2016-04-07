/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

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
    List<PatchInfo> patches = myNewBuild != null ? myNewBuild.getPatches() : Collections.emptyList();
    return patches.stream().filter(p -> p.isAvailable() && p.getFromBuild().compareTo(build) == 0).findFirst().orElse(null);
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