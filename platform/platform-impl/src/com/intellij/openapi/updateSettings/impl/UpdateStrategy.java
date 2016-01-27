/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class UpdateStrategy {
  public enum State {LOADED, CONNECTION_ERROR, NOTHING_LOADED}

  private final int myMajorVersion;
  private final BuildNumber myCurrentBuild;
  private final UpdatesInfo myUpdatesInfo;
  private final UserUpdateSettings myUpdateSettings;
  private final ChannelStatus myChannelStatus;
  private final UpdateStrategyCustomization myStrategyCustomization;

  /** @deprecated use {@link #UpdateStrategy(int, BuildNumber, UpdatesInfo, UserUpdateSettings, UpdateStrategyCustomization)} */
  @SuppressWarnings("unused")
  public UpdateStrategy(int majorVersion,
                        @NotNull BuildNumber currentBuild,
                        @NotNull UpdatesInfo updatesInfo,
                        @NotNull UserUpdateSettings updateSettings) {
    this(majorVersion, currentBuild, updatesInfo, updateSettings, UpdateStrategyCustomization.getInstance());
  }

  public UpdateStrategy(int majorVersion,
                        @NotNull BuildNumber currentBuild,
                        @NotNull UpdatesInfo updatesInfo,
                        @NotNull UserUpdateSettings updateSettings,
                        @NotNull UpdateStrategyCustomization customization) {
    myMajorVersion = majorVersion;
    myCurrentBuild = currentBuild;
    myUpdatesInfo = updatesInfo;
    myUpdateSettings = updateSettings;
    myChannelStatus = updateSettings.getSelectedChannelStatus();
    myStrategyCustomization = customization;
  }

  public final CheckForUpdateResult checkForUpdates() {
    Product product = myUpdatesInfo.getProduct(myCurrentBuild.getProductCode());

    if (product == null || product.getChannels().isEmpty()) {
      return new CheckForUpdateResult(State.NOTHING_LOADED, null);
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

    UpdateChannel channelToPropose = null;

    /* Android Studio: We don't want to propose "new channels".
       When a user downloads a stable build, the default channel
       is stable, and they'll stay in that channel "forever";
       they'll only be offered new stable versions. (In IntelliJ
       however even stable users seem to be offered the EAP
       channel for the next stable version.)   If a user has chosen
       some other channel, such as canary or beta, they'll stay
       in that channel. (We post more stable bits to the less stable
       channels as well.)

    for (UpdateChannel channel : product.getChannels()) {
      if (!myUpdateSettings.getKnownChannelsIds().contains(channel.getId()) &&
          channel.getMajorVersion() >= myMajorVersion &&
          channel.getStatus().compareTo(myChannelStatus) >= 0 &&
          hasNewVersion(channel) &&
          (channelToPropose == null || isBetter(channelToPropose, channel))) {
        channelToPropose = channel;
      }
    }
    */

    return new CheckForUpdateResult(newBuild, updatedChannel, channelToPropose, product.getAllChannelIds());
  }

  private List<UpdateChannel> getActiveChannels(Product product) {
    List<UpdateChannel> result = new ArrayList<UpdateChannel>();

    for (UpdateChannel channel : product.getChannels()) {
      // If the update is to a new version and on a stabler channel, choose it.
      if ((channel.getMajorVersion() >= myMajorVersion && channel.getStatus().compareTo(myChannelStatus) >= 0) &&
          (myStrategyCustomization.allowMajorVersionUpdate() ||
           channel.getMajorVersion() == myMajorVersion ||
           channel.getStatus() == ChannelStatus.EAP && myChannelStatus == ChannelStatus.EAP)) {
        // Prefer channel that has same status as our selected channel status
        if (channel.getMajorVersion() == myMajorVersion && channel.getStatus().compareTo(myChannelStatus) == 0) {
          result.add(0, channel);
        }
        else {
          result.add(channel);
        }
      }
    }

    return result;
  }

  private boolean hasNewVersion(@NotNull UpdateChannel channel) {
    BuildInfo latestBuild = channel.getLatestBuild();
    return latestBuild != null &&
           latestBuild.getNumber() != null &&
           !myUpdateSettings.getIgnoredBuildNumbers().contains(latestBuild.getNumber().asStringWithoutProductCode()) &&
           myCurrentBuild.compareTo(latestBuild.getNumber()) < 0;
  }

  private static boolean isBetter(UpdateChannel channelToPropose, UpdateChannel channel) {
    return channel.getMajorVersion() > channelToPropose.getMajorVersion() ||
           channel.getMajorVersion() == channelToPropose.getMajorVersion() && channel.getStatus().compareTo(channelToPropose.getStatus()) > 0;
  }
}
