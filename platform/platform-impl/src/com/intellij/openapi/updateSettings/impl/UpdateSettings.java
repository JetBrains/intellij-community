/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.NetUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
public class UpdateSettings implements PersistentStateComponent<UpdateOptions>, UserUpdateSettings {
  public static UpdateSettings getInstance() {
    return ServiceManager.getService(UpdateSettings.class);
  }

  private final String myPackageManager = System.getProperty("ide.no.platform.update");
  private UpdateOptions myState = new UpdateOptions();

  public boolean isPlatformUpdateEnabled() {
    return myPackageManager == null && !PathManager.isSnap();
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

  public boolean isSecureConnection() {
    return myState.isUseSecureConnection();
  }

  public void setSecureConnection(boolean value) {
    myState.setUseSecureConnection(value);
  }

  public long getLastTimeChecked() {
    return myState.getLastTimeChecked();
  }

  @NotNull
  @Override
  public List<String> getIgnoredBuildNumbers() {
    return myState.getIgnoredBuildNumbers();
  }

  @NotNull
  @Override
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
    return hosts;
  }

  public void forceCheckForUpdateAfterRestart() {
    myState.setLastTimeChecked(0);
  }

  public void saveLastCheckedInfo() {
    myState.setLastTimeChecked(System.currentTimeMillis());
    myState.setLastBuildChecked(ApplicationInfo.getInstance().getBuild().asString());
  }

  public boolean canUseSecureConnection() {
    return myState.isUseSecureConnection() && NetUtils.isSniEnabled();
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #getSelectedChannelStatus()} (to be removed in IDEA 2018) */
  @SuppressWarnings("unused")
  public String getUpdateChannelType() {
    return myState.getUpdateChannelType();
  }

  /** @deprecated use {@link #setSelectedChannelStatus(ChannelStatus)} (to be removed in IDEA 2018) */
  @SuppressWarnings("unused")
  public void setUpdateChannelType(@NotNull String value) {
    myState.setUpdateChannelType(value);
  }
  //</editor-fold>
}