// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.eventLog.events.EventFields.Float
import com.intellij.internal.statistic.eventLog.events.EventFields.Int
import com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByEnum
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.actionSystem.ex.QuickListsManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.platform.jbr.JdkEx
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.NewUiValue
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment

/**
 * @author Konstantin Bulenkov
 */
private class UiInfoUsageCollector : ApplicationUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override suspend fun getMetricsAsync(): Set<MetricEvent> = getDescriptors()
}

@Suppress("EnumEntryName")
private enum class UiType {
  classic, new
}

@Suppress("EnumEntryName")
private enum class NavBarType {
  visible, floating
}

@Suppress("EnumEntryName")
private enum class VisibilityType {
  visible, hidden
}

@Suppress("EnumEntryName")
private enum class HidpiMode {
  per_monitor_dpi, system_dpi
}

private val GROUP = EventLogGroup("ui.info.features", 15)
private val orientationField = Enum("value", VisibilityType::class.java)
private val UI_TYPE = GROUP.registerEvent("UI.type", Enum("value", UiType::class.java))
private val NAV_BAR = GROUP.registerEvent("Nav.Bar", Enum("value", NavBarType::class.java))
private val NAV_BAR_MEMBERS = GROUP.registerEvent("Nav.Bar.members", orientationField)
private val TOOLBAR = GROUP.registerEvent("Toolbar", orientationField)
private val TOOLBAR_AND_NAV_BAR = GROUP.registerEvent("Toolbar.and.NavBar",
                                                      Enum("toolbar", VisibilityType::class.java),
                                                      Enum("navbar", VisibilityType::class.java)
)
private val RETINA = GROUP.registerEvent("Retina", EventFields.Enabled)
private val SHOW_TOOLWINDOW = GROUP.registerEvent("QuickDoc.Show.Toolwindow", EventFields.Enabled)
private val QUICK_DOC_AUTO_UPDATE = GROUP.registerEvent("QuickDoc.AutoUpdate", EventFields.Enabled)
private val LOOK_AND_FEEL = GROUP.registerEvent("Look.and.Feel", StringValidatedByEnum("value", "look_and_feel"))
private val LAF_AUTODETECT = GROUP.registerEvent("laf.autodetect", EventFields.Enabled)
private val TOOL_WINDOW_LAYOUTS_COUNT = GROUP.registerEvent("tool.window.layouts", EventFields.Count)
private val HIDPI_MODE = GROUP.registerEvent("Hidpi.Mode", Enum("value", HidpiMode::class.java))
private val SCREEN_READER = GROUP.registerEvent("Screen.Reader", EventFields.Enabled)
private val QUICK_LISTS_COUNT = GROUP.registerEvent("QuickListsCount", Int("value"))
private val SCALE_MODE_FIELD = Boolean("scale_mode")
private val SCALE_FIELD = Float("scale")
private val USER_SCALE_FIELD = Float("user_scale")
private val SCREEN_SCALE = GROUP.registerVarargEvent("Screen.Scale", SCALE_MODE_FIELD, SCALE_FIELD, USER_SCALE_FIELD)
private val NUMBER_OF_MONITORS = GROUP.registerEvent("Number.Of.Monitors", Int("value"))
private val SCREEN_RESOLUTION_FIELD: StringEventField = object : StringEventField("value") {
  override val validationRule = java.util.List.of("{regexp#integer}x{regexp#integer}_({regexp#integer}%)",
                                                  "{regexp#integer}x{regexp#integer}")
}
private val SCREEN_RESOLUTION = GROUP.registerEvent("Screen.Resolution", Int("display_id"), SCREEN_RESOLUTION_FIELD)

private suspend fun getDescriptors(): Set<MetricEvent> {
  val set = HashSet<MetricEvent>()
  set.add(UI_TYPE.metric(if (NewUiValue.isEnabled()) UiType.new else UiType.classic))
  set.add(NAV_BAR.metric(if (navbar()) NavBarType.visible else NavBarType.floating))
  set.add(
    NAV_BAR_MEMBERS.metric(if (UISettings.getInstance().showMembersInNavigationBar) VisibilityType.visible else VisibilityType.hidden)
  )
  set.add(TOOLBAR.metric(if (toolbar()) VisibilityType.visible else VisibilityType.hidden))
  set.add(TOOLBAR_AND_NAV_BAR.metric(
    if (toolbar()) VisibilityType.visible else VisibilityType.hidden,
    if (navbar()) VisibilityType.visible else VisibilityType.hidden
  ))
  set.add(RETINA.metric(UIUtil.isRetina()))
  val properties = PropertiesComponent.getInstance()
  set.add(SHOW_TOOLWINDOW.metric(properties.isTrueValue("ShowDocumentationInToolWindow")))
  set.add(QUICK_DOC_AUTO_UPDATE.metric(properties.getBoolean("DocumentationAutoUpdateEnabled", true)))
  val lafManager = LafManager.getInstance()
  set.add(LOOK_AND_FEEL.metric(lafManager.currentUIThemeLookAndFeel?.id?.takeIf(String::isNotEmpty) ?: "unknown"))
  set.add(LAF_AUTODETECT.metric(lafManager.autodetect))
  set.add(TOOL_WINDOW_LAYOUTS_COUNT.metric(ToolWindowDefaultLayoutManager.getInstance().getLayoutNames().size))
  val value = if (JreHiDpiUtil.isJreHiDPIEnabled()) HidpiMode.per_monitor_dpi else HidpiMode.system_dpi
  set.add(HIDPI_MODE.metric(value))
  set.add(SCREEN_READER.metric(ScreenReader.isActive()))
  set.add(QUICK_LISTS_COUNT.metric(QuickListsManager.getInstance().allQuickLists.size))
  addScreenScale(set)
  addNumberOfMonitors(set)
  addScreenResolutions(set)
  return set
}

private fun getDeviceScreenInfo(device: GraphicsDevice): String {
  val conf = device.defaultConfiguration
  val rect = conf.bounds
  var info = rect.width.toString() + "x" + rect.height
  val scale = JBUIScale.sysScale(conf)
  if (scale != 1f) {
    info += " (" + (scale * 100).toInt() + "%)"
  }
  return info
}

private fun addScreenResolutions(set: MutableSet<MetricEvent>) {
  if (GraphicsEnvironment.isHeadless()) return
  val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
  for (i in devices.indices) {
    val info = getDeviceScreenInfo(devices[i])
    set.add(SCREEN_RESOLUTION.metric(i, info))
  }
}

private fun addNumberOfMonitors(set: MutableSet<MetricEvent>) {
  if (GraphicsEnvironment.isHeadless()) return
  val numberOfMonitors = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.size
  set.add(NUMBER_OF_MONITORS.metric(numberOfMonitors))
}

private fun toolbar(): Boolean = UISettings.getInstance().showMainToolbar

private fun navbar(): Boolean = UISettings.getInstance().showNavigationBar

private suspend fun addScreenScale(set: MutableSet<MetricEvent>) {
  val scale = roundScaleValue(JBUIScale.sysScale())
  val userScale = roundScaleValue(JBUIScale.scale(1.0f))
  var isScaleMode: Boolean? = null
  if (!GraphicsEnvironment.isHeadless()) {
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val dm = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode
      isScaleMode = dm != null && !JdkEx.getDisplayModeEx().isDefault(dm)
    }
  }
  val data = ArrayList<EventPair<*>>()
  data.add(SCALE_FIELD.with(scale))
  data.add(USER_SCALE_FIELD.with(userScale))
  if (isScaleMode != null) {
    data.add(SCALE_MODE_FIELD.with(isScaleMode == true))
  }
  set.add(SCREEN_SCALE.metric(data))
}

private fun roundScaleValue(scale: Float): Float {
  val scaleBase = Math.floor(scale.toDouble()).toInt()
  var scaleFraction = scale - scaleBase
  // count integer scale on a precise match only
  scaleFraction = when {
    scaleFraction == 0.0f -> 0.0f
    scaleFraction < 0.375f -> 0.25f
    scaleFraction < 0.625f -> 0.5f
    else -> 0.75f
  }
  return scaleBase + scaleFraction
}
