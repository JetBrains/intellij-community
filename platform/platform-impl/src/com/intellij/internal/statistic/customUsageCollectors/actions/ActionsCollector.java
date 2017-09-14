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
package com.intellij.internal.statistic.customUsageCollectors.actions;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ActionsCollector",
  storages = @Storage(value = "statistics.actions.xml", roamingType = RoamingType.DISABLED)
)
public class ActionsCollector implements PersistentStateComponent<ActionsCollector.State> {
  public void record(String actionId) {
    if (actionId == null) return;

    State state = getState();
    if (state == null) return;

    String key = ConvertUsagesUtil.escapeDescriptorName(actionId);
    final Integer count = state.myValues.get(key);
    int value = count == null ? 1 : count + 1;
    state.myValues.put(key, value);
  }

  private State myState = new State();
  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public static ActionsCollector getInstance() {
    return ServiceManager.getService(ActionsCollector.class);
  }

  final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "action", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }

  final static class ActionUsagesCollector extends UsagesCollector {
    private static final GroupDescriptor GROUP = GroupDescriptor.create("Actions", GroupDescriptor.HIGHER_PRIORITY);

    @NotNull
    public Set<UsageDescriptor> getUsages() {
      State state = getInstance().getState();
      assert state != null;
      return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
    }

    @NotNull
    public GroupDescriptor getGroupId() {
      return GROUP;
    }
  }
}
