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


import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"ConstantConditions"})

public class UpdateStrategy {
  public static enum State {LOADED, CONNECTION_ERROR, NOTHING_LOADED}

  private UserUpdateSettings updateSettings;
  private int myMajorVersion;
  private BuildNumber myCurrentBuild;

  private ChannelStatus myChannelStatus;
  private UpdatesInfo myUpdatesInfo;


  public UpdateStrategy(int majorVersion, @NotNull BuildNumber currentBuild, @NotNull UpdatesInfo updatesInfo,
                        @NotNull UserUpdateSettings updateSettings) {
    myMajorVersion = majorVersion;
    myUpdatesInfo = updatesInfo;
    this.updateSettings = updateSettings;
    this.myCurrentBuild = currentBuild;
    myChannelStatus = updateSettings.getSelectedChannelStatus();
  }

  public final CheckForUpdateResult checkForUpdates() {
    final Product product = myUpdatesInfo.getProduct(myCurrentBuild.getProductCode());

    if (product == null || product.getChannels().isEmpty()) {
      return new CheckForUpdateResult(State.NOTHING_LOADED);
    }

    UpdateChannel updatedChannel = null;
    BuildInfo newBuild = null;
    List<UpdateChannel> activeChannels = getActiveChannels(product);
    for (UpdateChannel channel : activeChannels) {
      if (hasNewVersion(channel)) {
        updatedChannel = channel;
        newBuild = updatedChannel.getLatestBuild();
        break;
      }
    }

    return new CheckForUpdateResult(updatedChannel, newBuild, null, Collections.<String>emptyList(), null);

    /*
    boolean replacedWithAppDef = false;

    UpdateChannel channel = getChannelByIds(selectedChannel, product);

    if (!forced && (channel == null || isChannelIsOlderThenBuild(channel))) {
      selectedChannel = updateSettings.getAppDefaultChannelId();
      replacedWithAppDef = true;
      channel = getChannelByIds(selectedChannel, product);
    }

    if (channel == null) {
      return new CheckForUpdateResult(State.NOTHING_LOADED);
    }

    final Pair<List<UpdateChannel>, UpdateChannel> newChannels = getNewChannels(product);



    return new CheckForUpdateResult(
      replacedWithAppDef, channel,
      getNewVersionInSelectedChannel(channel),
      newChannels.getFirst(), getFilteredKnownChannels(product),
      newChannels.getSecond());
    */
  }

  private List<UpdateChannel> getActiveChannels(Product product) {
    List<UpdateChannel> channels = product.getChannels();
    List<UpdateChannel> result = new ArrayList<UpdateChannel>();
    for (UpdateChannel channel : channels) {
      if (channel.getMajorVersion() == myMajorVersion && channel.getStatus().compareTo(myChannelStatus) >= 0) {
        result.add(channel);
      }
    }
    return result;
  }

  @Nullable
  private List<String> getFilteredKnownChannels(@NotNull Product product) {
    List<String> result = new ArrayList<String>();
    for (UpdateChannel channel : product.getChannels()) {
      //if (!isChannelIsOlderThenBuild(channel)){
        result.add(channel.getId());
      //}
    }
    return result;
  }

  private boolean hasNewVersion(@NotNull UpdateChannel channel) {
    if (channel.getLatestBuild() == null || channel.getLatestBuild().getNumber() == null) {
      return false;
    }
    return myCurrentBuild.compareTo(channel.getLatestBuild().getNumber()) < 0;
  }

  @Nullable
  private static UpdateChannel getChannelByIds(@Nullable String selectedChannel, @NotNull Product product) {
    if (selectedChannel == null) {
      return null;
    }
    for (UpdateChannel channel : product.getChannels()) {
      if (selectedChannel.equals(channel.getId())) {
        return channel;
      }
    }
    return null;
  }


  @Nullable
  private BuildInfo getNewVersionInSelectedChannel(@NotNull UpdateChannel channel) {
    final BuildInfo latestBuild = channel.getLatestBuild();
    if (myCurrentBuild.compareTo(latestBuild.getNumber()) < 0) {
      return latestBuild;
    }
    return null;
  }


  /**
   *
   * @param product
   * @return pair: list of all new channel + one channel could be proposed to user specially
   */
  @NotNull
  private Pair<List<UpdateChannel>, UpdateChannel> getNewChannels(@NotNull Product product) {
    List<String> knownChannels =
      updateSettings.getKnownChannelsIds() != null ? updateSettings.getKnownChannelsIds() : Collections.<String>emptyList();

    UpdateChannel versionUpgradeChannel = null;
    List<UpdateChannel> newChannels = new ArrayList<UpdateChannel>();
    final List<UpdateChannel> loadedChannels = product.getChannels();

    for (UpdateChannel channel : loadedChannels) {
      if (knownChannels.contains(channel.getId()) || !isInteresting(channel)) {
        continue;
      }
      newChannels.add(channel);
      if (isNewer(channel)) {
        versionUpgradeChannel = channel;
      }
    }
    return new Pair<List<UpdateChannel>, UpdateChannel>(newChannels.size() > 0 ? newChannels : null, versionUpgradeChannel);
  }



  private boolean isInteresting(UpdateChannel channel) {
    return myCurrentBuild.getBaselineVersion() <= channel.getLatestBuild().getNumber().getBaselineVersion();
  }

  private boolean isNewer(UpdateChannel channel) {
    return channel.getLatestBuild().getNumber().getBaselineVersion() > myCurrentBuild.getBaselineVersion();
  }
}
