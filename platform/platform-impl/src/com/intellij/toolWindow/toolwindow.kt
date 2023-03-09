// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.BundleBase
import com.intellij.DynamicBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.IdeUICustomization
import com.intellij.util.ui.EmptyIcon
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.function.Supplier
import javax.swing.Icon

enum class ToolWindowProperty {
  TITLE, ICON, AVAILABLE, STRIPE_TITLE
}

@ApiStatus.Internal
interface RegisterToolWindowTaskProvider {
  fun getTasks(project: Project): Collection<ToolWindowEP>
}

// Adding or removing items? Don't forget to increment the version in ToolWindowEventLogGroup.GROUP
enum class ToolWindowEventSource {
  StripeButton, SquareStripeButton, ToolWindowHeader, ToolWindowHeaderAltClick, Content, Switcher, SwitcherSearch,
  ToolWindowsWidget, RemoveStripeButtonAction,
  HideOnShowOther, HideSide, CloseFromSwitcher,
  ActivateActionMenu, ActivateActionKeyboardShortcut, ActivateActionGotoAction, ActivateActionOther,
  CloseAction, HideButton, HideToolWindowAction, HideSideWindowsAction, HideAllWindowsAction, JumpToLastWindowAction, ToolWindowSwitcher,
  InspectionsWidget
}

@ApiStatus.ScheduledForRemoval
@Deprecated("This API was replaced", ReplaceWith("getStripeTitleSupplier(id, project, pluginDescriptor)"))
fun getStripeTitleSupplier(id: String, pluginDescriptor: PluginDescriptor): Supplier<String>? {
  val openProjects = ProjectUtilCore.getOpenProjects()
  val project = if (openProjects.size == 1) openProjects.first() else DefaultProjectFactory.getInstance().defaultProject
  return getStripeTitleSupplier(id, project, pluginDescriptor)
}

fun getStripeTitleSupplier(id: String, project: Project, pluginDescriptor: PluginDescriptor): Supplier<@NlsContexts.TabTitle String>? {
  if (id == "Project") {
    return Supplier { IdeUICustomization.getInstance().getProjectViewTitle(project) }
  }

  val classLoader = pluginDescriptor.classLoader
  val bundleName = when (pluginDescriptor.pluginId) {
    PluginManagerCore.CORE_ID -> IdeBundle.BUNDLE
    else -> pluginDescriptor.resourceBundleBaseName ?: return null
  }

  try {
    val bundle = DynamicBundle.getResourceBundle(classLoader, bundleName)
    val key = "toolwindow.stripe.${id}".replace(" ", "_")

    @Suppress("HardCodedStringLiteral", "UnnecessaryVariable")
    val fallback = id
    val label = BundleBase.messageOrDefault(bundle, key, fallback)
    return Supplier { label }
  }
  catch (e: MissingResourceException) {
    logger<ToolWindowManagerImpl>().warn("Missing bundle $bundleName at $classLoader", e)
  }
  return null
}

fun findIconFromBean(bean: ToolWindowEP, factory: ToolWindowFactory, pluginDescriptor: PluginDescriptor): Icon? {
  factory.icon?.let {
    return it
  }

  try {
    return IconLoader.findIcon(bean.icon ?: return null, pluginDescriptor.classLoader)
  }
  catch (e: Exception) {
    logger<ToolWindowManagerImpl>().error(e)
    return EmptyIcon.ICON_13
  }
}

@Serializable
data class ToolWindowDescriptor(
  val id: String,
  var order: Int = -1,

  val paneId: String = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID,
  var anchor: ToolWindowAnchor = ToolWindowAnchor.LEFT,
  val isAutoHide: Boolean = false,
  val floatingBounds: List<Int>? = null,
  val isMaximized: Boolean = false,

  val type: ToolWindowType = ToolWindowType.DOCKED,
  val internalType: ToolWindowType = ToolWindowType.DOCKED,
  var contentUiType: ToolWindowContentUiType = ToolWindowContentUiType.TABBED,

  val isActiveOnStart: Boolean = false,
  var isVisible: Boolean = false,
  val isShowStripeButton: Boolean = true,

  var weight: Float = 0.33f,
  val sideWeight: Float = 0.5f,

  val isSplit: Boolean = false,
  ) {
  enum class ToolWindowAnchor {
    TOP, LEFT, BOTTOM, RIGHT
  }

  enum class ToolWindowContentUiType {
    TABBED, COMBO
  }
}