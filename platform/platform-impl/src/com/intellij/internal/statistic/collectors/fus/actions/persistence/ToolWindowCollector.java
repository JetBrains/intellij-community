// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowWhitelistEP;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowActivationSource.ACTIVATION;
import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector.ToolWindowActivationSource.CLICK;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPlatformPlugin;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getUnknownPlugin;
import static com.intellij.openapi.wm.ToolWindowId.*;

/**
 * @author Konstantin Bulenkov
 */
public class ToolWindowCollector {

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
    ourToolwindowWhitelist.put(SERVICES, getPlatformPlugin());
  }

  public ToolWindowCollector() {
    for (ToolWindowWhitelistEP extension : ToolWindowWhitelistEP.EP_NAME.getExtensions()) {
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfoById(extension.getPluginId());
      if (info.isDevelopedByJetBrains()) {
        ourToolwindowWhitelist.put(extension.id, info);
      }
    }
  }

  public void recordActivation(String toolWindowId) {
    record(toolWindowId, ACTIVATION);
  }

  //todo[kb] provide a proper way to track activations by clicks
  public void recordClick(String toolWindowId) {
    record(toolWindowId, CLICK);
  }

  private void record(@Nullable String toolWindowId, @NotNull ToolWindowActivationSource source) {
    if (StringUtil.isNotEmpty(toolWindowId)) {
      final FeatureUsageData data = new FeatureUsageData().addOS();
      if (source != ACTIVATION) {
        data.addData("source", StringUtil.toLowerCase(source.name()));
      }
      FUCounterUsageLogger.getInstance().logEvent("toolwindow", toolWindowId, data);
    }
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

  enum ToolWindowActivationSource {
    ACTIVATION, CLICK
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

      final PluginInfo info = getPluginInfo(data);
      if (StringUtil.equals(data, context.eventId)) {
        context.setPluginInfo(info);
      }
      return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }
  }
}
