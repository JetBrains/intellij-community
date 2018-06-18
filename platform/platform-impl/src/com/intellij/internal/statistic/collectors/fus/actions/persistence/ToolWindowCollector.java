// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.components.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ToolWindowCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED)
  }
)
public class ToolWindowCollector implements PersistentStateComponent<ToolWindowCollector.State> {
  private static final String DEFAULT_ID = "third.party.plugin.toolwindow";

  public static ToolWindowCollector getInstance() {
    return ServiceManager.getService(ToolWindowCollector.class);
  }

  public void recordActivation(String toolWindowId) {
    record(toolWindowId, "Activation");
  }

  //todo[kb] provide a proper way to track activations by clicks
  public void recordClick(String toolWindowId) {
    record(toolWindowId, "Click");
  }

  private void record(@Nullable String toolWindowId, @NotNull String source) {
    if (toolWindowId == null) return;
    State state = getState();
    if (state == null) return;

    boolean isJB = isDevelopedByJetBrains(toolWindowId);
    final String key = ConvertUsagesUtil.escapeDescriptorName((isJB ? toolWindowId : DEFAULT_ID) + "_by_" + source);
    FeatureUsageLogger.INSTANCE.log("toolwindow", key);
    final Integer count = state.myValues.get(key);
    int value = count == null ? 1 : count + 1;
    state.myValues.put(key, value);
  }

  public static boolean isDevelopedByJetBrains(@NotNull String toolWindowId) {
    for (ToolWindowEP ep : ToolWindowEP.EP_NAME.getExtensions()) {
      if (StringUtil.equals(toolWindowId, ep.id)) {
        final PluginId id = StringUtil.isNotEmpty(ep.factoryClass) ? PluginManagerCore.getPluginByClassName(ep.factoryClass) : null;
        return PluginManagerMain.isDevelopedByJetBrains(id);
      }
    }
    return false;
  }

  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "toolWindow", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }
}
