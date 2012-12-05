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
package com.intellij.ide.updates;


import com.intellij.openapi.updateSettings.impl.*;
import com.intellij.openapi.util.BuildNumber;
import junit.framework.Assert;
import junit.framework.TestCase;


public class UpdateStrategyTest extends TestCase {

  //could be if somebody used before previous version of IDEA
  public void testWithUndefinedSelection() {
    final TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-98.520"), UpdatesInfoXppParserTest.InfoReader.read("idea-same.xml"), settings);

    final CheckForUpdateResult result1 = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result1.getState());
    Assert.assertNull(result1.getNewBuildInSelectedChannel());
  }


  public void testWithUserSelection() {
    //assume user has version 9 eap - and used eap channel - we want to introduce new eap
    final TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-95.429"), UpdatesInfoXppParserTest.InfoReader.read("idea-new9eap.xml"), settings);

    final CheckForUpdateResult result = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result.getState());
    final BuildInfo update = result.getNewBuildInSelectedChannel();
    Assert.assertNotNull(update);
    Assert.assertEquals("95.627", update.getNumber().toString());
  }

  public void testIgnore() {
    //assume user has version 9 eap - and used eap channel - we want to introduce new eap
    final TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    settings.addIgnoredBuildNumber("95.627");
    settings.addIgnoredBuildNumber("98.620");
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-95.429"), UpdatesInfoXppParserTest.InfoReader.read("idea-new9eap.xml"), settings);

    final CheckForUpdateResult result = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result.getState());
    final BuildInfo update = result.getNewBuildInSelectedChannel();
    Assert.assertNull(update);
  }

  public void testNewChannelAppears() {
    // assume user has version 9 eap subscription (default or selected)
    // and new channel appears - eap of version 10 is there
    final TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.RELEASE);
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-95.627"), UpdatesInfoXppParserTest.InfoReader.read("idea-newChannel-release.xml"), settings);


    final CheckForUpdateResult result = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result.getState());
    final BuildInfo update = result.getNewBuildInSelectedChannel();
    Assert.assertNull(update);

    final UpdateChannel newChannel = result.getChannelToPropose();
    Assert.assertNotNull(newChannel);
    Assert.assertEquals("IDEA10EAP", newChannel.getId());
    Assert.assertEquals("IntelliJ IDEA X EAP", newChannel.getName());
  }

  public void testNewChannelWithOlderBuild() {
    final TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(10, BuildNumber.fromString("IU-107.80"), UpdatesInfoXppParserTest.InfoReader.read("idea-newChannel.xml"), settings);

    final CheckForUpdateResult result = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result.getState());
    final BuildInfo update = result.getNewBuildInSelectedChannel();
    Assert.assertNull(update);

    final UpdateChannel newChannel = result.getChannelToPropose();
    Assert.assertNull(newChannel);
  }

  public void testNewChannelAndNewBuildAppear() {
    //assume user has version 9 eap subscription (default or selected)
    //and new channels appears - eap of version 10 is there
    //and new build withing old channel appears also
    //we need to show only one dialog
    final TestUpdateSettings settings = new TestUpdateSettings(ChannelStatus.EAP);
    //first time load
    UpdateStrategy strategy = new UpdateStrategy(9, BuildNumber.fromString("IU-95.429"), UpdatesInfoXppParserTest.InfoReader.read("idea-newChannel.xml"), settings);

    final CheckForUpdateResult result = strategy.checkForUpdates();
    Assert.assertEquals(UpdateStrategy.State.LOADED, result.getState());
    final BuildInfo update = result.getNewBuildInSelectedChannel();
    Assert.assertNotNull(update);
    Assert.assertEquals("95.627", update.getNumber().toString());

    final UpdateChannel newChannel = result.getChannelToPropose();
    Assert.assertNotNull(newChannel);
    Assert.assertEquals("IDEA10EAP", newChannel.getId());
    Assert.assertEquals("IntelliJ IDEA X EAP", newChannel.getName());
  }
}
