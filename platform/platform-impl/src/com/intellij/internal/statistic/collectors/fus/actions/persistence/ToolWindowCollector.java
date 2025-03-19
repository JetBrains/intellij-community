// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.wm.impl.WindowInfoImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import kotlin.Unit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 *   in plugin.xml {@link #EP_NAME} or here in {@link ToolWindowCollector#toolwindowAllowList}
 * </p>
 */
@ApiStatus.Internal
public final class ToolWindowCollector {
  private static final ExtensionPointName<ToolWindowAllowlistEP> EP_NAME = new ExtensionPointName<>("com.intellij.toolWindowAllowlist");

  public static ToolWindowCollector getInstance() {
    return Holder.INSTANCE;
  }

  private static final class Holder {
    private static final ToolWindowCollector INSTANCE = new ToolWindowCollector();
  }

  /**
   * Use this set to whitelist dynamically registered platform toolwindows.<br/><br/>
   *
   * If toolwindow is registered in plugin.xml, it's whitelisted automatically. <br/>
   * To whitelist dynamically registered plugin toolwindow use {@link #EP_NAME}
   */
  private static final Map<String, PluginInfo> toolwindowAllowList = new HashMap<>();

  static {
    for (String id : List.of(MESSAGES_WINDOW, DEBUG, RUN, FIND, HIERARCHY, ANALYZE_DEPENDENCIES, MODULES_DEPENDENCIES, DUPLICATES,
                             EXTRACT_METHOD,
                             DOCUMENTATION, PREVIEW, SERVICES, ENDPOINTS, BuildContentManager.TOOL_WINDOW_ID, "CVS",
                             BUILD_DEPENDENCIES)) {
      toolwindowAllowList.put(id, getPlatformPlugin());
    }
  }

  private ToolWindowCollector() {
    EP_NAME.processWithPluginDescriptor((extension, pluginDescriptor) -> {
      addToolwindowToWhitelist(extension, pluginDescriptor);
      return Unit.INSTANCE;
    });
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
      toolwindowAllowList.put(extension.id, info);
    }
  }

  public void recordResized(@NotNull Project project, @NotNull WindowInfoImpl info, @NotNull Boolean isMaximized) {
    if (StringUtil.isEmpty(info.getId())) {
      return;
    }

    RESIZED.log(project, data -> {
      addDefaultFields(info.getId(), info, null, data);
      data.add(WEIGHT.with(info.getWeight()));
      data.add(IS_MAXIMIZED.with(isMaximized));
    });
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

    event.log(project, data -> {
      addDefaultFields(toolWindowId, windowInfo, source, data);
    });
  }

  private static void addDefaultFields(@NotNull String toolWindowId,
                                @Nullable WindowInfoImpl windowInfo,
                                @Nullable ToolWindowEventSource source,
                                List<EventPair<?>> data) {
    PluginInfo info = getToolWindowInfo(toolWindowId);
    data.add(TOOLWINDOW_ID.with(toolWindowId));
    data.add(EventFields.PluginInfo.with(info));
    if (windowInfo != null) {
      data.add(VIEW_MODE.with(ViewMode.fromWindowInfo(windowInfo)));
      data.add(LOCATION.with(Anchor.fromWindowInfo(windowInfo)));
    }
    if (source != null) {
      data.add(SOURCE.with(source));
    }
  }

  private static @NotNull PluginInfo getToolWindowInfo(@NotNull String toolWindowId) {
    if (toolwindowAllowList.containsKey(toolWindowId)) {
      return toolwindowAllowList.get(toolWindowId);
    }

    PluginInfo info = getToolWindowInfo(toolWindowId, ToolWindowEP.EP_NAME.getExtensionList());
    if (info == null) {
      info = getToolWindowInfo(toolWindowId, LibraryDependentToolWindow.EXTENSION_POINT_NAME.getExtensionList());
    }
    if (info == null) {
      info = getToolWindowInfo(toolWindowId, FacetDependentToolWindow.EXTENSION_POINT_NAME.getExtensionList());
    }
    return info != null ? info : getUnknownPlugin();
  }

  private static @Nullable PluginInfo getToolWindowInfo(@NotNull String toolWindowId, @NotNull List<? extends ToolWindowEP> toolWindows) {
    for (ToolWindowEP ep : toolWindows) {
      if (StringUtil.equals(toolWindowId, ep.id)) {
        PluginDescriptor pluginDescriptor = ep.getPluginDescriptor();
        return PluginInfoDetectorKt.getPluginInfoByDescriptor(pluginDescriptor);
      }
    }
    return null;
  }

  public static final class ToolWindowUtilValidator extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "toolwindow";
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if ("unknown".equals(data)) return ValidationResultType.ACCEPTED;

      if (hasPluginField(context)) {
        return acceptWhenReportedByJetBrainsPlugin(context);
      }
      PluginInfo info = getToolWindowInfo(data);
      return info.isDevelopedByJetBrains() ? ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
    }
  }
}
