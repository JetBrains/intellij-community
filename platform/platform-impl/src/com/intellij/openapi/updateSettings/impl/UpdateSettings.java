/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.components.*;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import com.intellij.util.xmlb.annotations.CollectionBean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(
  name = "UpdatesConfigurable",
  storages = {
    @Storage(value = "updates.xml", roamingType = RoamingType.DISABLED),
    @Storage(value = "other.xml", deprecated = true)
  }
)
public class UpdateSettings implements PersistentStateComponent<UpdateSettings.State>, UserUpdateSettings {
  public static class State {
    @CollectionBean public final List<String> pluginHosts = new SmartList<>();
    @CollectionBean public final List<String> ignoredBuildNumbers = new SmartList<>();

    @CollectionBean public final List<String> enabledExternalComponentSources = new SmartList<>();
    @CollectionBean public final List<String> knownExternalComponentSources = new SmartList<>();
    @CollectionBean public final Map<String, String> externalUpdateChannels = new HashMap<>();

    public boolean CHECK_NEEDED = true;
    public long LAST_TIME_CHECKED = 0;

    public String LAST_BUILD_CHECKED;
    public String UPDATE_CHANNEL_TYPE = ChannelStatus.RELEASE.getCode();
    public boolean SECURE_CONNECTION = true;
  }

  public static UpdateSettings getInstance() {
    return ServiceManager.getService(UpdateSettings.class);
  }

  private final String myPackageManager = System.getProperty("ide.no.platform.update");
  private State myState = new State();

  public boolean isPlatformUpdateEnabled() {
    return myPackageManager == null;
  }

  @NotNull
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
    myState.LAST_BUILD_CHECKED = StringUtil.nullize(myState.LAST_BUILD_CHECKED);
  }

  @Nullable
  public String getLastBuildChecked() {
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

  public List<String> getEnabledExternalUpdateSources() {
    return myState.enabledExternalComponentSources;
  }

  public List<String> getKnownExternalUpdateSources() {
    return myState.knownExternalComponentSources;
  }

  public Map<String, String> getExternalUpdateChannels() {
    return myState.externalUpdateChannels;
  }

  public boolean isSecureConnection() {
    return myState.SECURE_CONNECTION;
  }

  public void setSecureConnection(boolean value) {
    myState.SECURE_CONNECTION = value;
  }

  public long getLastTimeChecked() {
    return myState.LAST_TIME_CHECKED;
  }

  @NotNull
  @Override
  public List<String> getIgnoredBuildNumbers() {
    return myState.ignoredBuildNumbers;
  }

  @NotNull
  @Override
  public ChannelStatus getSelectedChannelStatus() {
    return ChannelStatus.fromCode(myState.UPDATE_CHANNEL_TYPE);
  }

  public void setSelectedChannelStatus(@NotNull ChannelStatus channel) {
    myState.UPDATE_CHANNEL_TYPE = channel.getCode();
  }

  @NotNull
  public List<ChannelStatus> getActiveChannels() {
    UpdateStrategyCustomization tweaker = UpdateStrategyCustomization.getInstance();
    return Stream.of(ChannelStatus.values())
      .filter(ch -> ch == ChannelStatus.EAP || ch == ChannelStatus.RELEASE || tweaker.isChannelActive(ch))
      .collect(Collectors.toList());
  }

  @NotNull
  public ChannelStatus getSelectedActiveChannel() {
    UpdateStrategyCustomization tweaker = UpdateStrategyCustomization.getInstance();
    ChannelStatus current = getSelectedChannelStatus();
    return tweaker.isChannelActive(current)
           ? current
           : getActiveChannels().stream().filter(ch -> ch.compareTo(current) > 0).findFirst().orElse(ChannelStatus.RELEASE);
  }

  public List<String> getPluginHosts() {
    List<String> hosts = new ArrayList<>(myState.pluginHosts);
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

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #getSelectedChannelStatus()} (to be removed in IDEA 2018) */
  @SuppressWarnings("unused")
  public String getUpdateChannelType() {
    return myState.UPDATE_CHANNEL_TYPE;
  }

  /** @deprecated use {@link #setSelectedChannelStatus(ChannelStatus)} (to be removed in IDEA 2018) */
  @SuppressWarnings("unused")
  public void setUpdateChannelType(@NotNull String value) {
    myState.UPDATE_CHANNEL_TYPE = value;
  }
  //</editor-fold>
}