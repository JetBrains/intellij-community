// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ShortcutsCollector;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowWhitelistEP;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.internal.statistic.beans.ConvertUsagesUtil.escapeDescriptorName;
import static com.intellij.openapi.wm.ToolWindowId.*;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ToolWindowsCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true),
  }
)
public class ToolWindowCollector implements PersistentStateComponent<ToolWindowCollector.State> {
  private static final String UNKNOWN = "unknown_by_";

  public static ToolWindowCollector getInstance() {
    return ServiceManager.getService(ToolWindowCollector.class);
  }

  public static final Set<String> ourToolwindowWhitelist = new HashSet<>();
  static {
    ourToolwindowWhitelist.add(COMMANDER);
    ourToolwindowWhitelist.add(MESSAGES_WINDOW);
    ourToolwindowWhitelist.add(PROJECT_VIEW);
    ourToolwindowWhitelist.add(STRUCTURE_VIEW);
    ourToolwindowWhitelist.add(FAVORITES_VIEW);
    ourToolwindowWhitelist.add(ANT_BUILD);
    ourToolwindowWhitelist.add(DEBUG);
    ourToolwindowWhitelist.add(RUN);
    ourToolwindowWhitelist.add(BUILD);
    ourToolwindowWhitelist.add(FIND);
    ourToolwindowWhitelist.add(CVS);
    ourToolwindowWhitelist.add(HIERARCHY);
    ourToolwindowWhitelist.add(INSPECTION);
    ourToolwindowWhitelist.add(TODO_VIEW);
    ourToolwindowWhitelist.add(DEPENDENCIES);
    ourToolwindowWhitelist.add(VCS);
    ourToolwindowWhitelist.add(MODULES_DEPENDENCIES);
    ourToolwindowWhitelist.add(DUPLICATES);
    ourToolwindowWhitelist.add(EXTRACT_METHOD);
    ourToolwindowWhitelist.add(DOCUMENTATION);
    ourToolwindowWhitelist.add(TASKS);
    ourToolwindowWhitelist.add(DATABASE_VIEW);
    ourToolwindowWhitelist.add(PREVIEW);
    ourToolwindowWhitelist.add(RUN_DASHBOARD);
  }

  public ToolWindowCollector() {
    for (ToolWindowWhitelistEP extension : ToolWindowWhitelistEP.EP_NAME.getExtensions()) {
      if (StatisticsUtilKt.isDevelopedByJetBrains(extension.getPluginId())) {
        ourToolwindowWhitelist.add(extension.id);
      }
    }

    // initialize outdated collectors to clean up previously cached values
    ShortcutsCollector.getInstance();
    OutdatedToolWindowCollector.getInstance();
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

    final boolean isWhitelisted = ourToolwindowWhitelist.contains(toolWindowId) || isDevelopedByJetBrains(toolWindowId);
    final String key = escapeDescriptorName(isWhitelisted ? toolWindowId + " by " + source : UNKNOWN + source);
    FeatureUsageLogger.INSTANCE.log("toolwindow.v2", key, FUSUsageContext.OS_CONTEXT.getData());
  }

  public static boolean isDevelopedByJetBrains(@NotNull String toolWindowId) {
    boolean isByJB = isDevelopedByJetBrains(toolWindowId, ToolWindowEP.EP_NAME.getExtensions());
    isByJB = isByJB || isDevelopedByJetBrains(toolWindowId, LibraryDependentToolWindow.EXTENSION_POINT_NAME.getExtensions());
    isByJB = isByJB || isDevelopedByJetBrains(toolWindowId, FacetDependentToolWindow.EXTENSION_POINT_NAME.getExtensions());
    return isByJB;
  }

  public static boolean isDevelopedByJetBrains(@NotNull String toolWindowId, @NotNull ToolWindowEP[] toolWindows) {
    for (ToolWindowEP ep : toolWindows) {
      if (StringUtil.equals(toolWindowId, ep.id)) {
        return StatisticsUtilKt.isDevelopedByJetBrains(ep.getPluginId());
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
  }

  public final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "toolWindow", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }

  @com.intellij.openapi.components.State(
    name = "ToolWindowCollector",
    storages = {
      @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED, deprecated = true),
    }
  )
  public static class OutdatedToolWindowCollector implements PersistentStateComponent<ToolWindowCollector.State> {

    public static OutdatedToolWindowCollector getInstance() {
      return ServiceManager.getService(OutdatedToolWindowCollector.class);
    }

    @Nullable
    @Override
    public ToolWindowCollector.State getState() {
      return new State();
    }

    @Override
    public void loadState(@NotNull ToolWindowCollector.State state) {
    }
  }
}
