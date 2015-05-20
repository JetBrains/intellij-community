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

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.xmlb.annotations.CollectionBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
  name = "UpdatesConfigurable",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/updates.xml", roamingType = RoamingType.DISABLED),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true)
  }
)
public class UpdateSettings implements PersistentStateComponent<UpdateSettings.State>, UserUpdateSettings {
  private State myState = new State();

  public UpdateSettings() {
    updateDefaultChannel();
  }

  public static UpdateSettings getInstance() {
    return ServiceManager.getService(UpdateSettings.class);
  }

  static class State {
    @CollectionBean
    public final List<String> pluginHosts = new SmartList<String>();
    @CollectionBean
    public final List<String> knownUpdateChannels = new SmartList<String>();
    @CollectionBean
    public final List<String> ignoredBuildNumbers = new SmartList<String>();
    @CollectionBean
    public final List<String> outdatedPlugins = new SmartList<String>();

    public boolean CHECK_NEEDED = true;
    public long LAST_TIME_CHECKED = 0;

    public String LAST_BUILD_CHECKED;
    public String UPDATE_CHANNEL_TYPE = ChannelStatus.RELEASE_CODE;
    public boolean SECURE_CONNECTION = false;
  }

  @Nullable
  public String getLasBuildChecked() {
    return myState.LAST_BUILD_CHECKED;
  }

  @NotNull
  public List<String> getStoredPluginHosts() {
    return myState.pluginHosts;
  }

  public boolean isCheckNeeded() {
    return myState.CHECK_NEEDED;
  }

  public void setCheckNeeded(boolean value) {
    myState.CHECK_NEEDED = value;
  }

  public boolean isSecureConnection() {
    return myState.SECURE_CONNECTION;
  }

  public void setSecureConnection(boolean value) {
    myState.SECURE_CONNECTION = value;
  }

  @NotNull
  public String getUpdateChannelType() {
    return myState.UPDATE_CHANNEL_TYPE;
  }

  public long getLastTimeChecked() {
    return myState.LAST_TIME_CHECKED;
  }

  public void setUpdateChannelType(@NotNull String value) {
    myState.UPDATE_CHANNEL_TYPE = value;
  }

  @NotNull
  public List<String> getOutdatedPlugins() {
    return myState.outdatedPlugins;
  }

  private void updateDefaultChannel() {
    if (ApplicationInfoImpl.getShadowInstance().isEAP()) {
      myState.UPDATE_CHANNEL_TYPE = ChannelStatus.EAP_CODE;
    }
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
    myState.LAST_BUILD_CHECKED = StringUtil.nullize(myState.LAST_BUILD_CHECKED);
    updateDefaultChannel();
  }

  @NotNull
  @Override
  public List<String> getKnownChannelsIds() {
    return new ArrayList<String>(myState.knownUpdateChannels);
  }

  @Override
  public void setKnownChannelIds(@NotNull List<String> ids) {
    myState.knownUpdateChannels.clear();
    for (String id : ids) {
      myState.knownUpdateChannels.add(id);
    }
  }

  public void forgetChannelId(String id) {
    myState.knownUpdateChannels.remove(id);
  }

  @Override
  public List<String> getIgnoredBuildNumbers() {
    return myState.ignoredBuildNumbers;
  }

  @NotNull
  @Override
  public ChannelStatus getSelectedChannelStatus() {
    return ChannelStatus.fromCode(myState.UPDATE_CHANNEL_TYPE);
  }

  public List<String> getPluginHosts() {
    List<String> hosts = new ArrayList<String>(myState.pluginHosts);
    String pluginHosts = System.getProperty("idea.plugin.hosts");
    if (pluginHosts != null) {
      ContainerUtil.addAll(hosts, pluginHosts.split(";"));
    }
    return hosts;
  }

  public void forceCheckForUpdateAfterRestart() {
    myState.LAST_TIME_CHECKED = 0;
  }

  public void saveLastCheckedInfo() {
    myState.LAST_TIME_CHECKED = System.currentTimeMillis();
    myState.LAST_BUILD_CHECKED = ApplicationInfo.getInstance().getBuild().asString();
  }

  public boolean canUseSecureConnection() {
    return myState.SECURE_CONNECTION && NetUtils.isSniEnabled();
  }
}