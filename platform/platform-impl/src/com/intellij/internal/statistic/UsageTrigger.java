// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@State(
  name = "UsageTrigger",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED),
    @Storage(value = "statistics.application.usages.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  }
)
@Deprecated // to be removed in 2018.2
public class UsageTrigger implements PersistentStateComponent<UsageTrigger.State> {
  final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "feature", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }

  private State myState = new State();

  public static void trigger(@NotNull @NonNls String feature) {
    FeatureUsageLogger.INSTANCE.log("trigger", feature);
    getInstance().doTrigger(feature);
  }

  public static void trigger(@NotNull @NonNls String groupId, @NotNull @NonNls String feature) {
    FeatureUsageLogger.INSTANCE.log(groupId, feature);
    getInstance().doTrigger(feature);
  }

  public static void triggerOnce(@NotNull @NonNls String feature) {
    if (!getInstance().myState.myValues.containsKey(feature)) {
      getInstance().doTrigger(feature);
    }
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

  public void loadState(@NotNull final State state) {
    myState = state;
  }

  final static class MyCollector extends UsagesCollector {
    private static final GroupDescriptor GROUP = GroupDescriptor.create("features counts", GroupDescriptor.HIGHER_PRIORITY);

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
