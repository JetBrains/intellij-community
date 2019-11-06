// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowWhitelistEP;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowActivationSource.ACTIVATED;
import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowActivationSource.CLICKED;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPlatformPlugin;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getUnknownPlugin;
import static com.intellij.openapi.wm.ToolWindowId.*;

/**
 * @author Konstantin Bulenkov
 */
public final class ToolWindowCollector {
  private static final ToolWindowInfo UNKNOWN = new ToolWindowInfo("unknown", getUnknownPlugin());

  public static ToolWindowCollector getInstance() {
    return ServiceManager.getService(ToolWindowCollector.class);
  }

  public static final Map<String, ToolWindowInfo> ourToolwindowWhitelist = new HashMap<>();
  static {
    // Constants from ToolWindowId can be changed by localization plugins, therefore
    // we need to remember a link to a bundled message (which can be changed by localization)
    // and a constant id for recording.
    ourToolwindowWhitelist.put(COMMANDER, new ToolWindowInfo("Commander"));
    ourToolwindowWhitelist.put(MESSAGES_WINDOW, new ToolWindowInfo("Messages"));
    ourToolwindowWhitelist.put(PROJECT_VIEW, new ToolWindowInfo("Project"));
    ourToolwindowWhitelist.put(STRUCTURE_VIEW, new ToolWindowInfo("Structure"));
    ourToolwindowWhitelist.put(FAVORITES_VIEW, new ToolWindowInfo("Favorites"));
    ourToolwindowWhitelist.put(ANT_BUILD, new ToolWindowInfo("Ant"));
    ourToolwindowWhitelist.put(DEBUG, new ToolWindowInfo("Debug"));
    ourToolwindowWhitelist.put(RUN, new ToolWindowInfo("Run"));
    ourToolwindowWhitelist.put(BUILD, new ToolWindowInfo("Build"));
    ourToolwindowWhitelist.put(FIND, new ToolWindowInfo("Find"));
    ourToolwindowWhitelist.put(CVS, new ToolWindowInfo("CVS"));
    ourToolwindowWhitelist.put(HIERARCHY, new ToolWindowInfo("Hierarchy"));
    ourToolwindowWhitelist.put(INSPECTION, new ToolWindowInfo("Inspection_Results"));
    ourToolwindowWhitelist.put(TODO_VIEW, new ToolWindowInfo("TODO"));
    ourToolwindowWhitelist.put(DEPENDENCIES, new ToolWindowInfo("Dependency_Viewer"));
    ourToolwindowWhitelist.put(VCS, new ToolWindowInfo("Version_Control"));
    ourToolwindowWhitelist.put(MODULES_DEPENDENCIES, new ToolWindowInfo("Module_Dependencies"));
    ourToolwindowWhitelist.put(DUPLICATES, new ToolWindowInfo("Duplicates"));
    ourToolwindowWhitelist.put(EXTRACT_METHOD, new ToolWindowInfo("Extract_Method"));
    ourToolwindowWhitelist.put(DOCUMENTATION, new ToolWindowInfo("Documentation"));
    ourToolwindowWhitelist.put(TASKS, new ToolWindowInfo("Time_Tracking"));
    ourToolwindowWhitelist.put(DATABASE_VIEW, new ToolWindowInfo("Database"));
    ourToolwindowWhitelist.put(PREVIEW, new ToolWindowInfo("Preview"));
    ourToolwindowWhitelist.put(RUN_DASHBOARD, new ToolWindowInfo("Run_Dashboard"));
    ourToolwindowWhitelist.put(SERVICES, new ToolWindowInfo("Services"));
    ourToolwindowWhitelist.put("Statistics Event Log", new ToolWindowInfo("Statistics_Event_Log"));
  }

  private ToolWindowCollector() {
    for (ToolWindowWhitelistEP extension : ToolWindowWhitelistEP.EP_NAME.getExtensionList()) {
      PluginDescriptor pluginDescriptor = extension == null ? null : extension.getPluginDescriptor();
      PluginInfo info = pluginDescriptor != null ? PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor) : null;
      if (info != null && info.isDevelopedByJetBrains()) {
        ourToolwindowWhitelist.put(extension.id, new ToolWindowInfo(extension.id, info));
      }
    }
  }

  public static void recordActivation(String toolWindowId) {
    record(toolWindowId, ACTIVATED);
  }

  //todo[kb] provide a proper way to track activations by clicks
  public static void recordClick(String toolWindowId) {
    record(toolWindowId, CLICKED);
  }

  private static void record(@Nullable String toolWindowId, @NotNull ToolWindowActivationSource source) {
    if (StringUtil.isNotEmpty(toolWindowId)) {
      final ToolWindowInfo info = getToolWindowInfo(toolWindowId);
      final FeatureUsageData data = new FeatureUsageData().
        addData("id", info.myRecordedId).
        addPluginInfo(info.myPluginInfo);
      FUCounterUsageLogger.getInstance().logEvent("toolwindow", StringUtil.toLowerCase(source.name()), data);
    }
  }

  @NotNull
  private static ToolWindowInfo getToolWindowInfo(@NotNull String toolWindowId) {
    if (ourToolwindowWhitelist.containsKey(toolWindowId)) {
      return ourToolwindowWhitelist.get(toolWindowId);
    }

    ToolWindowInfo info = getToolWindowInfo(toolWindowId, ToolWindowEP.EP_NAME.getExtensions());
    if (info == null) {
      info = getToolWindowInfo(toolWindowId, LibraryDependentToolWindow.EXTENSION_POINT_NAME.getExtensions());
    }
    if (info == null) {
      info = getToolWindowInfo(toolWindowId, FacetDependentToolWindow.EXTENSION_POINT_NAME.getExtensions());
    }
    return info != null ? info : UNKNOWN;
  }

  @Nullable
  public static ToolWindowInfo getToolWindowInfo(@NotNull String toolWindowId, @NotNull ToolWindowEP[] toolWindows) {
    for (ToolWindowEP ep : toolWindows) {
      if (StringUtil.equals(toolWindowId, ep.id)) {
        PluginDescriptor pluginDescriptor = ep.getPluginDescriptor();
        PluginInfo info = pluginDescriptor != null ? PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor) : getUnknownPlugin();
        return new ToolWindowInfo(ep.id, info);
      }
    }
    return null;
  }

  enum ToolWindowActivationSource {
    ACTIVATED, CLICKED
  }

  public static class ToolWindowUtilValidator extends CustomWhiteListRule {

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "toolwindow".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if ("unknown".equals(data)) return ValidationResultType.ACCEPTED;
      return acceptWhenReportedByJetbrainsPlugin(context);
    }
  }

  private static class ToolWindowInfo {
    private final String myRecordedId;
    private final PluginInfo myPluginInfo;

    private ToolWindowInfo(@NotNull String recordedId) {
      this(recordedId, getPlatformPlugin());
    }

    private ToolWindowInfo(@NotNull String recordedId, @NotNull PluginInfo info) {
      myRecordedId = recordedId;
      myPluginInfo = info;
    }
  }
}
