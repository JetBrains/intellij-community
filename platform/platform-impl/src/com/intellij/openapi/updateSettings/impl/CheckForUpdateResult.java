/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.List;

public class CheckForUpdateResult {
  @Nullable
  private final UpdateChannel myUpdatedChannel;

  @Nullable
  private final BuildInfo newBuildInSelectedChannel;

  @Nullable
  private final Collection<UpdateChannel> newChannels;

  @Nullable
  private final List<String> allChannelsIds;

  @Nullable
  private final UpdateChannel newChannelToPropose;

  @NotNull
  private final UpdateStrategy.State state;
  @Nullable
  private final Exception error;


  public CheckForUpdateResult(@Nullable UpdateChannel updated,
                              @Nullable BuildInfo newBuildInSelectedChannel,
                              @Nullable Collection<UpdateChannel> newChannels, List<String> allChannelsIds,
                              @Nullable UpdateChannel channelToPropose) {
    this.newBuildInSelectedChannel = newBuildInSelectedChannel;
    myUpdatedChannel = updated;
    this.newChannels = newChannels;
    this.allChannelsIds = allChannelsIds;
    this.newChannelToPropose = channelToPropose;
    this.state = UpdateStrategy.State.LOADED;
    this.error = null;
  }

  public CheckForUpdateResult(UpdateStrategy.State state, Exception e) {
    this.newBuildInSelectedChannel = null;
    this.newChannels = null;
    this.allChannelsIds = null;
    this.newChannelToPropose = null;
    this.myUpdatedChannel = null;
    this.state = state;
    this.error = e;
  }

  public CheckForUpdateResult(UpdateStrategy.State state) {
    this(state,null);
  }

  @Nullable
  public BuildInfo getNewBuildInSelectedChannel() {
    return newBuildInSelectedChannel;
  }

  public boolean hasNewBuildInSelectedChannel(){
    return newBuildInSelectedChannel!=null;
  }

  @Nullable
  public Collection<UpdateChannel> getNewChannels() {
    return newChannels;
  }

  @Nullable
  public List<String> getAllChannelsIds() {
    return allChannelsIds;
  }

  @Nullable
  public UpdateChannel getNewChannelToPropose() {
    return newChannelToPropose;
  }

  @NotNull
  public UpdateStrategy.State getState() {
    return state;
  }

  @Nullable
  public Exception getError() {
    return error;
  }

  @NotNull
  public UpdateChannel getUpdatedChannel() {
    return myUpdatedChannel;
  }
}
