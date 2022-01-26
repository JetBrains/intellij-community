// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.build.BuildContentManager;
import com.intellij.facet.ui.FacetDependentToolWindow;
import com.intellij.ide.actions.ToolWindowMoveAction.Anchor;
import com.intellij.ide.actions.ToolWindowViewModeAction.ViewMode;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.internal.statistic.eventLog.events.VarargEventId;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowAllowlistEP;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow;
import com.intellij.openapi.wm.impl.ToolWindowEventSource;
import com.intellij.openapi.wm.impl.WindowInfoImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowEventLogGroup.*;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPlatformPlugin;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getUnknownPlugin;
import static com.intellij.openapi.wm.ToolWindowId.*;

/**
 * <p>
 *   Toolwindows registered in plugin.xml are whitelisted by default.<br/>
 *   See: {@link ToolWindowEP#EP_NAME}, {@link LibraryDependentToolWindow#EP_NAME}, {@link FacetDependentToolWindow#EP_NAME}
 * </p>
 *
 * <p>
 *   If toolwindow is registered dynamically is <b>should</b> be explicitly whitelisted
 *   in plugin.xml {@link #EP_NAME} or here in {@link ToolWindowCollector#ourToolwindowWhitelist}
 * </p>
 */
public final class ToolWindowCollector {
  private static final ExtensionPointName<ToolWindowAllowlistEP> EP_NAME = new ExtensionPointName<>("com.intellij.toolWindowAllowlist");
  private static final ToolWindowInfo UNKNOWN = new ToolWindowInfo("unknown", getUnknownPlugin());

  public static ToolWindowCollector getInstance() {
    return Holder.INSTANCE;
  }

  private static class Holder {
    private static final ToolWindowCollector INSTANCE = new ToolWindowCollector();
  }

  /**
   * Use this set to whitelist dynamically registered platform toolwindows.<br/><br/>
   *
   * If toolwindow is registered in plugin.xml, it's whitelisted automatically. <br/>
   * To whitelist dynamically registered plugin toolwindow use {@link #EP_NAME}
   */
  private static final Map<String, ToolWindowInfo> ourToolwindowWhitelist = new HashMap<>();
  static {
    ourToolwindowWhitelist.put(MESSAGES_WINDOW, new ToolWindowInfo("Messages"));
    ourToolwindowWhitelist.put(DEBUG, new ToolWindowInfo("Debug"));
    ourToolwindowWhitelist.put(RUN, new ToolWindowInfo("Run"));
    ourToolwindowWhitelist.put(BuildContentManager.TOOL_WINDOW_ID, new ToolWindowInfo("Build"));
    ourToolwindowWhitelist.put(FIND, new ToolWindowInfo("Find"));
    ourToolwindowWhitelist.put("CVS", new ToolWindowInfo("CVS"));
    ourToolwindowWhitelist.put(HIERARCHY, new ToolWindowInfo("Hierarchy"));
    ourToolwindowWhitelist.put(DEPENDENCIES, new ToolWindowInfo("Dependency_Viewer"));
    ourToolwindowWhitelist.put(MODULES_DEPENDENCIES, new ToolWindowInfo("Module_Dependencies"));
    ourToolwindowWhitelist.put(DUPLICATES, new ToolWindowInfo("Duplicates"));
    ourToolwindowWhitelist.put(EXTRACT_METHOD, new ToolWindowInfo("Extract_Method"));
    ourToolwindowWhitelist.put(DOCUMENTATION, new ToolWindowInfo("Documentation"));
    ourToolwindowWhitelist.put(PREVIEW, new ToolWindowInfo("Preview"));
    ourToolwindowWhitelist.put(RUN_DASHBOARD, new ToolWindowInfo("Run_Dashboard"));
    ourToolwindowWhitelist.put(SERVICES, new ToolWindowInfo("Services"));
    ourToolwindowWhitelist.put(ENDPOINTS, new ToolWindowInfo("Endpoints"));
  }

  private ToolWindowCollector() {
    EP_NAME.processWithPluginDescriptor(ToolWindowCollector::addToolwindowToWhitelist);
    EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull ToolWindowAllowlistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        addToolwindowToWhitelist(extension, pluginDescriptor);
      }
    }, null);
  }

  private static void addToolwindowToWhitelist(@NotNull ToolWindowAllowlistEP extension, @NotNull PluginDescriptor pluginDescriptor) {
    PluginInfo info = PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor);
    if (info.isDevelopedByJetBrains()) {
      ourToolwindowWhitelist.put(extension.id, new ToolWindowInfo(extension.id, info));
    }
  }

  public void recordActivation(@NotNull Project project,
                               @Nullable String toolWindowId,
                               @Nullable WindowInfoImpl info,
                               @Nullable ToolWindowEventSource source) {
    record(project, toolWindowId, ACTIVATED, info, source);
  }

  public void recordHidden(@NotNull Project project,
                           @NotNull WindowInfoImpl info,
                           @Nullable ToolWindowEventSource source) {
    record(project, info.getId(), HIDDEN, info, source);
  }

  public void recordShown(@NotNull Project project, @Nullable ToolWindowEventSource source, @NotNull WindowInfoImpl info) {
    record(project, info.getId(), SHOWN, info, source);
  }

  private static void record(@NotNull Project project,
                             @Nullable String toolWindowId,
                             @NotNull VarargEventId event,
                             @Nullable WindowInfoImpl windowInfo,
                             @Nullable ToolWindowEventSource source) {
    if (StringUtil.isEmpty(toolWindowId)) {
      return;
    }

    ToolWindowInfo info = getToolWindowInfo(toolWindowId);
    List<EventPair<?>> data = new ArrayList<>();
    data.add(TOOLWINDOW_ID.with(info.myRecordedId));
    data.add(EventFields.PluginInfo.with(info.myPluginInfo));
    if (windowInfo != null) {
      data.add(VIEW_MODE.with(ViewMode.fromWindowInfo(windowInfo)));
      data.add(LOCATION.with(Anchor.fromWindowInfo(windowInfo)));
    }
    if (source != null) {
      data.add(SOURCE.with(source));
    }
    event.log(project, data.toArray(new EventPair[0]));
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
  public static ToolWindowInfo getToolWindowInfo(@NotNull String toolWindowId, ToolWindowEP @NotNull [] toolWindows) {
    for (ToolWindowEP ep : toolWindows) {
      if (StringUtil.equals(toolWindowId, ep.id)) {
        PluginDescriptor pluginDescriptor = ep.getPluginDescriptor();
        return new ToolWindowInfo(ep.id, PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor));
      }
    }
    return null;
  }

  public static class ToolWindowUtilValidator extends CustomValidationRule {

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "toolwindow".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if ("unknown".equals(data)) return ValidationResultType.ACCEPTED;

      if (hasPluginField(context)) {
        return acceptWhenReportedByJetBrainsPlugin(context);
      }
      ToolWindowInfo info = getToolWindowInfo(data);
      return info.myPluginInfo.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }
  }

  public static final class ToolWindowInfo {
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
