// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.IntellijInternalApi;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Globally ignored unknown features per product, e.g. plugins suggested by project dependencies.
 */
@ApiStatus.Internal
@IntellijInternalApi
@State(
  name = "IgnoredPluginSuggestions",
  storages = @Storage("ignored-suggested-plugins.xml"),
  category = SettingsCategory.PLUGINS)
final class GlobalIgnoredPluginSuggestionState implements PersistentStateComponent<GlobalIgnoredPluginSuggestionState.State> {

  private State myState = new State();

  @SuppressWarnings("unused")
  public static final class State {
    public List<String> pluginIds = new ArrayList<>();
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public static GlobalIgnoredPluginSuggestionState getInstance() {
    return ApplicationManager.getApplication().getService(GlobalIgnoredPluginSuggestionState.class);
  }

  public boolean isIgnored(@NotNull PluginId pluginId) {
    return myState.pluginIds.contains(pluginId.getIdString());
  }

  public void ignoreFeature(@NotNull PluginId pluginId) {
    myState.pluginIds.add(pluginId.getIdString());
  }
}
