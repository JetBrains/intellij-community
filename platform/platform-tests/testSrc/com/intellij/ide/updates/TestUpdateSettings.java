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


import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UserUpdateSettings;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestUpdateSettings implements UserUpdateSettings {
  private ChannelStatus myChannelStatus;
  private List<String> myKnownChannelIds;
  private final List<String> myIgnoredBuildNumbers = new ArrayList<String>();

  public TestUpdateSettings(ChannelStatus channelStatus, String... knownChannelIds) {
    myChannelStatus = channelStatus;
    myKnownChannelIds = Arrays.asList(knownChannelIds);
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

  public void addIgnoredBuildNumber(String buildNumber) {
    myIgnoredBuildNumbers.add(buildNumber);
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
