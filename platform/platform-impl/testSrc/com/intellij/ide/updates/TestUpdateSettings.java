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

import java.util.Arrays;
import java.util.List;

public class TestUpdateSettings implements UserUpdateSettings {
  private ChannelStatus myChannelStatus;
  private boolean disabled;
  private List<String> knowsChannels;

  public TestUpdateSettings(ChannelStatus channelStatus, boolean disabled, String[] knowsChannels) {
    myChannelStatus = channelStatus;
    this.disabled = disabled;
    this.knowsChannels = knowsChannels!=null?Arrays.asList(knowsChannels):null;
  }

  @Override
  public boolean isCheckingDisabled() {
    return disabled;
  }

  @Override
  public List<String> getKnownChannelsIds() {
    return knowsChannels;
  }

  @NotNull
  @Override
  public String getAppDefaultChannelId() {
    return "IDEA10EAP";
  }

  @Override
  public void setKnownChannelIds(List<String> ids) {
    knowsChannels = ids;
  }

  @NotNull
  @Override
  public ChannelStatus getSelectedChannelStatus() {
    return myChannelStatus;
  }
}
