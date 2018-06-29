// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;


@State(
    name = "ActionMenuItemUpdateCollector",
    storages = {
        @Storage(value = "menu.item.text.update.xml", roamingType = RoamingType.DISABLED)
    }
)
public class ActionMenuItemUpdateCollector implements PersistentStateComponent<ActionMenuItemUpdateCollector.State> {
  private State state = new State();
  private final Map<String, Map<String, Integer>> myLocalState = new HashMap<>();

  public ActionMenuItemUpdateCollector() {
  }

  public <T> void record(@NotNull AnAction action, @NotNull String place, @NotNull String sourceKey) {
    String actionId = ActionManager.getInstance().getId(action);

    State state = getState();
    if (state == null) return;

    String key = ConvertUsagesUtil.escapeDescriptorName(place) + "@" + (actionId != null ? actionId : action.toString());

    Map<String, Integer> sourceIntegerMap = myLocalState.computeIfAbsent(sourceKey, k -> new HashMap<>());
    final Integer count = sourceIntegerMap.get(key);
    int value = count == null ? 1 : count + 1;
    sourceIntegerMap.put(key, value);

    final Integer stateCount = state.myValues.get(key);

    if (stateCount == null || stateCount < value) {
      state.myValues.put(key, value);
    }
  }

  @Nullable
  @Override
  public State getState() {
    return state;
  }

  @Override
  public void loadState(@NotNull State state) {
    this.state = state;
  }


  public static ActionMenuItemUpdateCollector getInstance() {
    return ServiceManager.getService(ActionMenuItemUpdateCollector.class);
  }

  public final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "actionItem", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }
}
