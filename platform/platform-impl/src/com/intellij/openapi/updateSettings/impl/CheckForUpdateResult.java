/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class CheckForUpdateResult {
  private final BuildInfo myNewBuildInSelectedChannel;
  private final UpdateChannel myUpdatedChannel;
  private final List<String> myAllChannelIds;
  private final UpdateStrategy.State myState;
  private final Exception myError;
  private UpdateChannel myChannelToPropose = null;

  public CheckForUpdateResult(@Nullable UpdateChannel updated,
                              @Nullable BuildInfo newBuildInSelectedChannel,
                              @NotNull List<String> allChannelsIds) {
    myNewBuildInSelectedChannel = newBuildInSelectedChannel;
    myUpdatedChannel = updated;
    myAllChannelIds = allChannelsIds;
    myState = UpdateStrategy.State.LOADED;
    myError = null;
  }

  public CheckForUpdateResult(@NotNull UpdateStrategy.State state, @Nullable Exception e) {
    myNewBuildInSelectedChannel = null;
    myUpdatedChannel = null;
    myAllChannelIds = Collections.emptyList();
    myState = state;
    myError = e;
  }

  public CheckForUpdateResult(@NotNull UpdateStrategy.State state) {
    this(state, null);
  }

  @Nullable
  public BuildInfo getNewBuildInSelectedChannel() {
    return myNewBuildInSelectedChannel;
  }

  @Nullable
  public UpdateChannel getUpdatedChannel() {
    return myUpdatedChannel;
  }

  @NotNull
  public List<String> getAllChannelsIds() {
    return myAllChannelIds;
  }

  @NotNull
  public UpdateStrategy.State getState() {
    return myState;
  }

  @Nullable
  public Exception getError() {
    return myError;
  }

  @Nullable
  public UpdateChannel getChannelToPropose() {
    return myChannelToPropose;
  }

  public void setChannelToPropose(@Nullable UpdateChannel channelToPropose) {
    myChannelToPropose = channelToPropose;
  }
}
