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
package com.intellij.internal.statistic.customUsageCollectors.ui;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ToolbarClicksCollector",
  storages = @Storage(value = "statistics.toolbar.clicks.xml", roamingType = RoamingType.DISABLED)
)
public class ToolbarClicksCollector implements PersistentStateComponent<ToolbarClicksCollector.ClicksState> {
  final static class ClicksState {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "action", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }

  private ClicksState myState = new ClicksState();

  public ClicksState getState() {
    return myState;
  }

  public void loadState(final ClicksState state) {
    myState = state;
  }

  public static void record(@NotNull AnAction action, String place) {
    String id = ActionManager.getInstance().getId(action);
    if (id == null) {
      id = action.getClass().getName();
    }
    ToolbarClicksCollector collector = getInstance();
    if (collector != null) {
      String key = ConvertUsagesUtil.escapeDescriptorName(id + "@" + place);
      ClicksState state = collector.getState();
      if (state != null) {
        final Integer count = state.myValues.get(key);
        int value = count == null ? 1 : count + 1;
        state.myValues.put(key, value);
      }
    }
  }

  private static ToolbarClicksCollector getInstance() {
    return ServiceManager.getService(ToolbarClicksCollector.class);
  }

  final static class ToolbarClicksUsagesCollector extends UsagesCollector {
    private static final GroupDescriptor GROUP = GroupDescriptor.create("Toolbar Clicks", GroupDescriptor.HIGHER_PRIORITY);

    @NotNull
    public Set<UsageDescriptor> getUsages() {
      ClicksState state = getInstance().getState();
      assert state != null;
      return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
    }

    @NotNull
    public GroupDescriptor getGroupId() {
      return GROUP;
    }
  }
}
