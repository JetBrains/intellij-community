// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.internal.statistic.collectors.fus.ui.persistence.ShortcutsCollector;
import com.intellij.internal.statistic.eventLog.FeatureUsageDataBuilder;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
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
import java.util.Map;

import static com.intellij.internal.statistic.beans.ConvertUsagesUtil.escapeDescriptorName;
import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowActivationSource.ACTIVATION;
import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowActivationSource.CLICK;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPlatformPlugin;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getUnknownPlugin;
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
  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("toolwindow", 1);
  private static final String UNKNOWN = "unknown";

  public static ToolWindowCollector getInstance() {
    return ServiceManager.getService(ToolWindowCollector.class);
  }

  public static final Map<String, PluginInfo> ourToolwindowWhitelist = new HashMap<>();
  static {
    ourToolwindowWhitelist.put(COMMANDER, getPlatformPlugin());
    ourToolwindowWhitelist.put(MESSAGES_WINDOW, getPlatformPlugin());
    ourToolwindowWhitelist.put(PROJECT_VIEW, getPlatformPlugin());
    ourToolwindowWhitelist.put(STRUCTURE_VIEW, getPlatformPlugin());
    ourToolwindowWhitelist.put(FAVORITES_VIEW, getPlatformPlugin());
    ourToolwindowWhitelist.put(ANT_BUILD, getPlatformPlugin());
    ourToolwindowWhitelist.put(DEBUG, getPlatformPlugin());
    ourToolwindowWhitelist.put(RUN, getPlatformPlugin());
    ourToolwindowWhitelist.put(BUILD, getPlatformPlugin());
    ourToolwindowWhitelist.put(FIND, getPlatformPlugin());
    ourToolwindowWhitelist.put(CVS, getPlatformPlugin());
    ourToolwindowWhitelist.put(HIERARCHY, getPlatformPlugin());
    ourToolwindowWhitelist.put(INSPECTION, getPlatformPlugin());
    ourToolwindowWhitelist.put(TODO_VIEW, getPlatformPlugin());
    ourToolwindowWhitelist.put(DEPENDENCIES, getPlatformPlugin());
    ourToolwindowWhitelist.put(VCS, getPlatformPlugin());
    ourToolwindowWhitelist.put(MODULES_DEPENDENCIES, getPlatformPlugin());
    ourToolwindowWhitelist.put(DUPLICATES, getPlatformPlugin());
    ourToolwindowWhitelist.put(EXTRACT_METHOD, getPlatformPlugin());
    ourToolwindowWhitelist.put(DOCUMENTATION, getPlatformPlugin());
    ourToolwindowWhitelist.put(TASKS, getPlatformPlugin());
    ourToolwindowWhitelist.put(DATABASE_VIEW, getPlatformPlugin());
    ourToolwindowWhitelist.put(PREVIEW, getPlatformPlugin());
    ourToolwindowWhitelist.put(RUN_DASHBOARD, getPlatformPlugin());
  }

  public ToolWindowCollector() {
    for (ToolWindowWhitelistEP extension : ToolWindowWhitelistEP.EP_NAME.getExtensions()) {
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfoById(extension.getPluginId());
      if (info.isDevelopedByJetBrains()) {
        ourToolwindowWhitelist.put(extension.id, info);
      }
    }

    // initialize outdated collectors to clean up previously cached values
    ShortcutsCollector.getInstance();
    OutdatedToolWindowCollector.getInstance();
  }

  public void recordActivation(String toolWindowId) {
    record(toolWindowId, ACTIVATION);
  }

  //todo[kb] provide a proper way to track activations by clicks
  public void recordClick(String toolWindowId) {
    record(toolWindowId, CLICK);
  }

  private void record(@Nullable String toolWindowId, @NotNull ToolWindowActivationSource source) {
    if (toolWindowId == null) return;

    final PluginInfo info = getPluginInfo(toolWindowId);
    final String key = escapeDescriptorName(info.isDevelopedByJetBrains() ? toolWindowId: UNKNOWN);

    final FeatureUsageDataBuilder builder = new FeatureUsageDataBuilder().
      addPluginInfo(info).
      addFeatureContext(FUSUsageContext.OS_CONTEXT);

    if (source != ACTIVATION) {
      builder.addData("source", StringUtil.toLowerCase(source.name()));
    }
    FeatureUsageLogger.INSTANCE.log(GROUP, key, builder.createData());
  }

  @NotNull
  private static PluginInfo getPluginInfo(@NotNull String toolWindowId) {
    if (ourToolwindowWhitelist.containsKey(toolWindowId)) {
      return ourToolwindowWhitelist.get(toolWindowId);
    }

    PluginInfo info = getPluginInfo(toolWindowId, ToolWindowEP.EP_NAME.getExtensions());
    if (info == null) {
      info = getPluginInfo(toolWindowId, LibraryDependentToolWindow.EXTENSION_POINT_NAME.getExtensions());
    }
    if (info == null) {
      info = getPluginInfo(toolWindowId, FacetDependentToolWindow.EXTENSION_POINT_NAME.getExtensions());
    }
    return info != null ? info : getUnknownPlugin();
  }

  @Nullable
  public static PluginInfo getPluginInfo(@NotNull String toolWindowId, @NotNull ToolWindowEP[] toolWindows) {
    for (ToolWindowEP ep : toolWindows) {
      if (StringUtil.equals(toolWindowId, ep.id)) {
        return PluginInfoDetectorKt.getPluginInfoById(ep.getPluginId());
      }
    }
    return null;
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

  enum ToolWindowActivationSource {
    ACTIVATION, CLICK
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
