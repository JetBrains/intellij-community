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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CheckForUpdateResult {
  @Nullable
  private final UpdateChannel myUpdatedChannel;

  @Nullable
  private final BuildInfo newBuildInSelectedChannel;

  @NotNull
  private final Collection<UpdateChannel> myNewChannels;

  @NotNull
  private final List<String> myAllChannelIds;

  @Nullable
  private UpdateChannel myChannelToPropose;

  @NotNull
  private final UpdateStrategy.State state;
  @Nullable
  private final Exception error;


  public CheckForUpdateResult(@Nullable UpdateChannel updated,
                              @Nullable BuildInfo newBuildInSelectedChannel,
                              @NotNull List<String> allChannelsIds) {
    this.newBuildInSelectedChannel = newBuildInSelectedChannel;
    myUpdatedChannel = updated;
    myNewChannels = new ArrayList<UpdateChannel>();
    this.myAllChannelIds = allChannelsIds;
    this.state = UpdateStrategy.State.LOADED;
    this.error = null;
  }

  public CheckForUpdateResult(UpdateStrategy.State state, Exception e) {
    this.newBuildInSelectedChannel = null;
    myNewChannels = Collections.emptyList();
    myAllChannelIds = Collections.emptyList();
    this.myChannelToPropose = null;
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

  public void addNewChannel(UpdateChannel channel) {
    myNewChannels.add(channel);
  }

  @NotNull
  public Collection<UpdateChannel> getNewChannels() {
    return myNewChannels;
  }

  @NotNull
  public List<String> getAllChannelsIds() {
    return myAllChannelIds;
  }

  @Nullable
  public UpdateChannel getChannelToPropose() {
    return myChannelToPropose;
  }

  public void setChannelToPropose(@Nullable UpdateChannel channelToPropose) {
    myChannelToPropose = channelToPropose;
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
