/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * User: ksafonov
 */
@State(name = "UsageTrigger", roamingType = RoamingType.DISABLED, storages = {@Storage(file = StoragePathMacros.APP_CONFIG + "/statistics.application.usages.xml")})
public class UsageTrigger implements PersistentStateComponent<UsageTrigger.State> {

  public static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "feature", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<String, Integer>();
  }

  private State myState = new State();

  public static void trigger(@NotNull String feature) {
    getInstance().doTrigger(feature);
  }

  private static UsageTrigger getInstance() {
    return ServiceManager.getService(UsageTrigger.class);
  }

  private void doTrigger(String feature) {
    ConvertUsagesUtil.assertDescriptorName(feature);
    final Integer count = myState.myValues.get(feature);
    if (count == null) {
      myState.myValues.put(feature, 1);
    }
    else {
      myState.myValues.put(feature, count + 1);
    }
  }

  public State getState() {
    return myState;
  }

  public void loadState(final State state) {
    myState = state;
  }

  
  public static class MyCollector extends UsagesCollector {

    private static final GroupDescriptor GROUP = GroupDescriptor.create("features counts", GroupDescriptor.HIGHER_PRIORITY);

    @NotNull
    public Set<UsageDescriptor> getUsages(@Nullable final Project project) {
      final State state = UsageTrigger.getInstance().getState();
      return ContainerUtil.map2Set(state.myValues.entrySet(), new Function<Map.Entry<String, Integer>, UsageDescriptor>() {
        public UsageDescriptor fun(final Map.Entry<String, Integer> e) {
          return new UsageDescriptor(e.getKey(), e.getValue());
        }
      });
    }

    @NotNull
    public GroupDescriptor getGroupId() {
      return GROUP;
    }
  }
  
}
