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
package com.intellij.ide.updates;

import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.updateSettings.impl.*;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class UpdateStrategyTest {
  @Test
  public void testWithUndefinedSelection() {
    // could be if somebody used before previous version of IDEA
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-98.520"), InfoReader.read("idea-same.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    assertNull(result.getNewBuildInSelectedChannel());
  }

  @Test
  public void testWithUserSelection() {
    // assume user has version 9 eap - and used eap channel - we want to introduce new eap
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-95.429"), InfoReader.read("idea-new9eap.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    BuildInfo update = result.getNewBuildInSelectedChannel();
    assertNotNull(update);
    assertEquals("95.627", update.getNumber().toString());
  }

  @Test
  public void testIgnore() {
    // assume user has version 9 eap - and used eap channel - we want to introduce new eap
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP, "95.627", "98.620");
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-95.429"), InfoReader.read("idea-new9eap.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    BuildInfo update = result.getNewBuildInSelectedChannel();
    assertNull(update);
  }

  @Test
  public void testNewChannelAppears() {
    // assume user has version 9 eap subscription (default or selected)
    // and new channel appears - eap of version 10 is there
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.RELEASE);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-95.627"), InfoReader.read("idea-newChannel-release.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    BuildInfo update = result.getNewBuildInSelectedChannel();
    assertNull(update);

    UpdateChannel newChannel = result.getChannelToPropose();
    assertNotNull(newChannel);
    assertEquals("IDEA10EAP", newChannel.getId());
    assertEquals("IntelliJ IDEA X EAP", newChannel.getName());
  }

  @Test
  public void testNewChannelWithOlderBuild() {
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(10, BuildNumber.fromString("IU-107.80"), InfoReader.read("idea-newChannel.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    BuildInfo update = result.getNewBuildInSelectedChannel();
    assertNull(update);

    UpdateChannel newChannel = result.getChannelToPropose();
    assertNull(newChannel);
  }

  @Test
  public void testNewChannelAndNewBuildAppear() {
    // assume user has version 9 eap subscription (default or selected)
    // and new channels appears - eap of version 10 is there
    // and new build withing old channel appears also
    // we need to show only one dialog
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-95.429"), InfoReader.read("idea-newChannel.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    BuildInfo update = result.getNewBuildInSelectedChannel();
    assertNotNull(update);
    assertEquals("95.627", update.getNumber().toString());

    UpdateChannel newChannel = result.getChannelToPropose();
    assertNotNull(newChannel);
    assertEquals("IDEA10EAP", newChannel.getId());
    assertEquals("IntelliJ IDEA X EAP", newChannel.getName());
  }

  @Test
  public void testChannelWithCurrentStatusPreferred() {
    BuildNumber currentBuild = BuildNumber.fromString("IU-139.658");
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(14, currentBuild, InfoReader.read("idea-patchAvailable.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());

    UpdateChannel channel = result.getUpdatedChannel();
    assertNotNull(channel);
    assertEquals(ChannelStatus.EAP, channel.getStatus());

    BuildInfo selectedChannel = result.getNewBuildInSelectedChannel();
    assertNotNull(selectedChannel);
    assertNotNull(selectedChannel.findPatchForBuild(currentBuild));
  }

  @Test
  public void testStableUpdatePreferredByDefault() {
    BuildNumber currentBuild = BuildNumber.fromString("IU-143.381");
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.RELEASE);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(15, currentBuild, InfoReader.read("idea-123280.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());

    UpdateChannel channel = result.getUpdatedChannel();
    assertNotNull(channel);
    assertEquals(ChannelStatus.RELEASE, channel.getStatus());

    BuildInfo build = result.getNewBuildInSelectedChannel();
    assertNotNull(build);
    assertEquals("143.382", build.getNumber().toString());
    assertNotNull(build.findPatchForBuild(currentBuild));
  }

  @Test
  public void testStableChannelProposedByDefault() {
    BuildNumber currentBuild = BuildNumber.fromString("IU-143.381");
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.RELEASE);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(15, currentBuild, InfoReader.read("idea-123280.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    UpdateChannel channelToPropose = result.getChannelToPropose();
    assertNotNull(channelToPropose);
    assertEquals("IDEA_Release", channelToPropose.getId());

    settings.setKnownChannelIds(Collections.singletonList(channelToPropose.getId()));
    result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    assertNull(result.getChannelToPropose());
  }

  @Test
  public void testParallelCampaignsInChannel() {
    BuildNumber currentBuild = BuildNumber.fromString("IU-143.381");
    TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    UpdateStrategyCustomization customization = new UpdateStrategyCustomization();
    UpdateStrategy strategy = new UpdateStrategy(15, currentBuild, InfoReader.read("idea-2eap.xml"), settings, customization);

    CheckForUpdateResult result = strategy.checkForUpdates();
    assertEquals(UpdateStrategy.State.LOADED, result.getState());
    BuildInfo build = result.getNewBuildInSelectedChannel();
    assertNotNull(build);
    assertEquals("143.888", build.getNumber().toString());
  }

  private static class TestUpdateSettings implements UserUpdateSettings {
    private final ChannelStatus myChannelStatus;
    private final List<String> myIgnoredBuildNumbers;
    private List<String> myKnownChannelIds = Collections.emptyList();

    public TestUpdateSettings(ChannelStatus channelStatus, String... ignoredBuilds) {
      myChannelStatus = channelStatus;
      myIgnoredBuildNumbers = Arrays.asList(ignoredBuilds);
    }

    @NotNull
    @Override
    public List<String> getKnownChannelsIds() {
      return myKnownChannelIds;
    }

    @Override
    public List<String> getIgnoredBuildNumbers() {
      return myIgnoredBuildNumbers;
    }

    @Override
    public void setKnownChannelIds(List<String> ids) {
      myKnownChannelIds = ids;
    }

    @NotNull
    @Override
    public ChannelStatus getSelectedChannelStatus() {
      return myChannelStatus;
    }
  }
}