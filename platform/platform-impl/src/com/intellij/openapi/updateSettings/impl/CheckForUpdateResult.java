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

  private final boolean replacedWithAppDef;

  @NotNull
  private final UpdateChannel selected;

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


  public CheckForUpdateResult(boolean replacedWithAppDef, @NotNull UpdateChannel selected,
                              @Nullable BuildInfo newBuildInSelectedChannel,
                              @Nullable Collection<UpdateChannel> newChannels, List<String> allChannelsIds,
                              @Nullable UpdateChannel channelToPropose) {
    this.replacedWithAppDef = replacedWithAppDef;
    this.newBuildInSelectedChannel = newBuildInSelectedChannel;
    this.selected = selected;
    this.newChannels = newChannels;
    this.allChannelsIds = allChannelsIds;
    this.newChannelToPropose = channelToPropose;
    this.state = UpdateStrategy.State.LOADED;
    this.error = null;
  }

  public CheckForUpdateResult(UpdateStrategy.State state, Exception e) {
    this.replacedWithAppDef = false;
    this.newBuildInSelectedChannel = null;
    this.newChannels = null;
    this.allChannelsIds = null;
    this.newChannelToPropose = null;
    this.selected = null;
    this.state = state;
    this.error = e;
  }

  public CheckForUpdateResult(UpdateStrategy.State state) {
    this(state,null);
  }


  /**
   * @return true - if we need to override user channel selection, for example:
   *         - user has no preferences defined (old settings config)
   *         - user selection has no sense any more, e.g. user selected IDEA 9.x eap, then he/she upgraded his IDEA to version 10.x,
   *         it means that we need to override his channel with new one (should we ask him or just override the channel silently?
   *         if we could select the channel with correct type - we could do it silently, if user setting pointed to eap - we could pick new eap chanel
   *         also we could show an notification (not modal))
   */
  private boolean isReplacedWithAddDefault() {
    return replacedWithAppDef;
  }

  public boolean isReplacedWithAppDef() {
    return replacedWithAppDef;
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
  public UpdateChannel getSelected() {
    return selected;
  }
}
