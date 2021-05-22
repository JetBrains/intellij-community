// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(name = "UpdatesConfigurable", storages = @Storage(value = "updates.xml", roamingType = RoamingType.DISABLED, exportable = true))
public class UpdateSettings implements PersistentStateComponent<UpdateOptions> {
  public static UpdateSettings getInstance() {
    return ApplicationManager.getApplication().getService(UpdateSettings.class);
  }

  private UpdateOptions myState = new UpdateOptions();

  public boolean isPlatformUpdateEnabled() {
    return ExternalUpdateManager.ACTUAL == null;
  }

  @Override
  public @NotNull UpdateOptions getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull UpdateOptions state) {
    myState = state;
  }

  public @Nullable String getLastBuildChecked() {
    return myState.getLastBuildChecked();
  }

  public @NotNull List<String> getStoredPluginHosts() {
    return myState.getPluginHosts();
  }

  public boolean isCheckNeeded() {
    return myState.isCheckNeeded();
  }

  public boolean isPluginsCheckNeeded() {
    return myState.isPluginsCheckNeeded();
  }

  public void setCheckNeeded(boolean value) {
    myState.setCheckNeeded(value);
  }

  public boolean isShowWhatsNewEditor() {
    return myState.isShowWhatsNewEditor();
  }

  public void setShowWhatsNewEditor(boolean value) {
    myState.setShowWhatsNewEditor(value);
  }

  public long getLastTimeChecked() {
    return myState.getLastTimeChecked();
  }

  public @NotNull List<String> getIgnoredBuildNumbers() {
    return myState.getIgnoredBuildNumbers();
  }

  public @NotNull ChannelStatus getSelectedChannelStatus() {
    return ChannelStatus.fromCode(myState.getUpdateChannelType());
  }

  public void setSelectedChannelStatus(@NotNull ChannelStatus channel) {
    myState.setUpdateChannelType(channel.getCode());
  }

  public @NotNull List<ChannelStatus> getActiveChannels() {
    UpdateStrategyCustomization tweaker = UpdateStrategyCustomization.getInstance();
    return Stream.of(ChannelStatus.values())
      .filter(ch -> ch == ChannelStatus.EAP || ch == ChannelStatus.RELEASE || tweaker.isChannelActive(ch))
      .collect(Collectors.toList());
  }

  public @NotNull ChannelStatus getSelectedActiveChannel() {
    UpdateStrategyCustomization tweaker = UpdateStrategyCustomization.getInstance();
    ChannelStatus current = getSelectedChannelStatus();
    return tweaker.isChannelActive(current)
           ? current
           : getActiveChannels().stream().filter(ch -> ch.compareTo(current) > 0).findFirst().orElse(ChannelStatus.RELEASE);
  }

  public @NotNull List<String> getPluginHosts() {
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
    myState.setLastBuildChecked(ApplicationInfo.getInstance().getBuild().asString());
    myState.setLastTimeChecked(System.currentTimeMillis());
  }

  public boolean isThirdPartyPluginsAllowed() {
    return myState.isThirdPartyPluginsAllowed();
  }

  public void setThirdPartyPluginsAllowed(boolean value) {
    myState.setThirdPartyPluginsAllowed(value);
  }
}
