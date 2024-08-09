// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@State(name = "UpdatesConfigurable", storages = @Storage(value = "updates.xml", roamingType = RoamingType.DISABLED, exportable = true))
public class UpdateSettings implements PersistentStateComponentWithModificationTracker<UpdateOptions> {
  public static UpdateSettings getInstance() {
    return ApplicationManager.getApplication().getService(UpdateSettings.class);
  }

  private UpdateOptions myState = new UpdateOptions();

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

  public void setCheckNeeded(boolean value) {
    myState.setCheckNeeded(value);
  }

  public boolean isPluginsAutoUpdateEnabled() { return myState.isPluginsAutoUpdateEnabled(); }

  public void setPluginsAutoUpdateEnabled(boolean value) { myState.setPluginsAutoUpdateEnabled(value); }

  public boolean isPluginsCheckNeeded() {
    return myState.isPluginsCheckNeeded();
  }

  public void setPluginsCheckNeeded(boolean value) {
    myState.setPluginsCheckNeeded(value);
  }

  public boolean isShowWhatsNewEditor() {
    return myState.isShowWhatsNewEditor();
  }

  public int getWhatsNewShownFor() {
    return myState.getWhatsNewShownFor();
  }

  public void setWhatsNewShownFor(int version) {
    myState.setWhatsNewShownFor(version);
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
    var tweaker = UpdateStrategyCustomization.getInstance();
    return Stream.of(ChannelStatus.values())
      .filter(ch -> ch == ChannelStatus.EAP || ch == ChannelStatus.RELEASE || tweaker.isChannelActive(ch))
      .collect(Collectors.toList());
  }

  public @NotNull ChannelStatus getSelectedActiveChannel() {
    var tweaker = UpdateStrategyCustomization.getInstance();
    var current = getSelectedChannelStatus();
    return tweaker.isChannelActive(current)
           ? current
           : getActiveChannels().stream().filter(ch -> ch.compareTo(current) > 0).findFirst().orElse(ChannelStatus.RELEASE);
  }

  /** @deprecated same as {@link #getStoredPluginHosts()} */
  @Deprecated(forRemoval = true)
  public @NotNull List<String> getPluginHosts() {
    return getStoredPluginHosts();
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

  public boolean isObsoleteCustomRepositoriesCleanNeeded() {
    return myState.isObsoleteCustomRepositoriesCleanNeeded();
  }

  public void setObsoleteCustomRepositoriesCleanNeeded(boolean value) {
    myState.setObsoleteCustomRepositoriesCleanNeeded(value);
  }

  @Override
  public long getStateModificationCount() {
    return myState.getModificationCount();
  }
}
