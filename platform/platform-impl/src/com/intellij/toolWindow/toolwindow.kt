// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.BundleBase
import com.intellij.DynamicBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.ui.IdeUICustomization
import com.intellij.ui.icons.findIconByPath
import com.intellij.util.ui.EmptyIcon
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.*
import java.util.function.Supplier
import javax.swing.Icon

@ApiStatus.Internal
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

fun getStripeTitleSupplier(id: String, project: Project, pluginDescriptor: PluginDescriptor): Supplier<@NlsContexts.TabTitle String>? {
  if (id == "Project") {
    val weakProjectRef = WeakReference(project)
    return Supplier {
      val unwrappedProject = weakProjectRef.get()?.takeUnless { it.isDisposed } ?: return@Supplier ""
      IdeUICustomization.getInstance().getProjectViewTitle(unwrappedProject)
    }
  }

  val classLoader = pluginDescriptor.classLoader
  val bundleName = when (pluginDescriptor.pluginId) {
    PluginManagerCore.CORE_ID -> IdeBundle.BUNDLE
    else -> pluginDescriptor.resourceBundleBaseName ?: return null
  }

  return Supplier {
    try {
      val bundle = DynamicBundle.getResourceBundle(classLoader, bundleName)
      val key = "toolwindow.stripe.${id}".replace(" ", "_")

      @Suppress("HardCodedStringLiteral")
      BundleBase.messageOrDefault(bundle = bundle, key = key, defaultValue = id)
    }
    catch (e: MissingResourceException) {
      logger<ToolWindowManagerImpl>().warn("Missing bundle $bundleName at $classLoader", e)
      ""
    }
  }
}

fun findIconFromBean(bean: ToolWindowEP, factory: ToolWindowFactory, pluginDescriptor: PluginDescriptor): Icon? {
  factory.icon?.let {
    return it
  }

  try {
    // cache is not used - cached as a part of ToolWindow instance
    return findIconByPath(path = bean.icon ?: return null, classLoader = pluginDescriptor.classLoader, cache = null)
  }
  catch (e: Exception) {
    logger<ToolWindowManagerImpl>().error(e)
    return EmptyIcon.ICON_13
  }
}

internal fun ToolWindowAnchor.isUltrawideLayout(): Boolean = !isHorizontal && !isSplitVertically

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