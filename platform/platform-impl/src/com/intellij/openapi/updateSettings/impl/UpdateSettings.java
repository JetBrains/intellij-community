// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.*;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(name = "UpdatesConfigurable", storages = @Storage(value = "updates.xml", roamingType = RoamingType.DISABLED, exportable = true), reportStatistic = true)
public class UpdateSettings implements PersistentStateComponent<UpdateOptions> {
  public static UpdateSettings getInstance() {
    return ServiceManager.getService(UpdateSettings.class);
  }

  private UpdateOptions myState = new UpdateOptions();

  public boolean isPlatformUpdateEnabled() {
    return ExternalUpdateManager.ACTUAL == null;
  }

  @NotNull
  @Override
  public UpdateOptions getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull UpdateOptions state) {
    myState = state;
  }

  @Nullable
  public String getLastBuildChecked() {
    return myState.getLastBuildChecked();
  }

  @NotNull
  public List<String> getStoredPluginHosts() {
    return myState.getPluginHosts();
  }

  public boolean isCheckNeeded() {
    return myState.isCheckNeeded();
  }

  public void setCheckNeeded(boolean value) {
    myState.setCheckNeeded(value);
  }

  public List<String> getEnabledExternalUpdateSources() {
    return myState.getEnabledExternalComponentSources();
  }

  public List<String> getKnownExternalUpdateSources() {
    return myState.getKnownExternalComponentSources();
  }

  public Map<String, String> getExternalUpdateChannels() {
    return myState.getExternalUpdateChannels();
  }

  public long getLastTimeChecked() {
    return myState.getLastTimeChecked();
  }

  @NotNull
  public List<String> getIgnoredBuildNumbers() {
    return myState.getIgnoredBuildNumbers();
  }

  @NotNull
  public ChannelStatus getSelectedChannelStatus() {
    return ChannelStatus.fromCode(myState.getUpdateChannelType());
  }

  public void setSelectedChannelStatus(@NotNull ChannelStatus channel) {
    myState.setUpdateChannelType(channel.getCode());
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

  @NotNull
  public List<String> getPluginHosts() {
    List<String> hosts = new ArrayList<>(myState.getPluginHosts());
    String pluginHosts = System.getProperty("idea.plugin.hosts");
    if (pluginHosts != null) {
      ContainerUtil.addAll(hosts, pluginHosts.split(";"));
    }

    UpdateSettingsProviderHelper.addPluginRepositories(hosts);
    ContainerUtil.removeDuplicates(hosts);
    return hosts;
  }

  public void forceCheckForUpdateAfterRestart() {
    myState.setLastTimeChecked(0);
  }

  public void saveLastCheckedInfo() {
    myState.setLastTimeChecked(System.currentTimeMillis());
    myState.setLastBuildChecked(ApplicationInfo.getInstance().getBuild().asString());
  }

  public boolean isThirdPartyPluginsAllowed() {
    return myState.isThirdPartyPluginsAllowed();
  }

  public void setThirdPartyPluginsAllowed(boolean value) {
    myState.setThirdPartyPluginsAllowed(value);
  }
}