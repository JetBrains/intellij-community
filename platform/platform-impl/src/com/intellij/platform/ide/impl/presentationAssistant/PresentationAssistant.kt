// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.xmlb.XmlSerializerUtil
import org.jetbrains.annotations.Nls
import java.awt.Color

internal enum class PresentationAssistantPopupSize(val value: Int, @Nls val displayName: String) {
  SMALL(0, IdeBundle.message("presentation.assistant.configurable.size.small")),
  MEDIUM(1, IdeBundle.message("presentation.assistant.configurable.size.medium")),
  LARGE(2, IdeBundle.message("presentation.assistant.configurable.size.large"));

  companion object {
    fun from(value: Int): PresentationAssistantPopupSize = when (value) {
      0 -> SMALL
      2 -> LARGE
      else -> MEDIUM
    }
  }
}

internal enum class PresentationAssistantPopupAlignment(val x: Int, val y: Int, @Nls val displayName: String) {
  TOP_LEFT(0, 0, IdeBundle.message("presentation.assistant.configurable.alignment.top.left")),
  TOP_CENTER(1, 0, IdeBundle.message("presentation.assistant.configurable.alignment.top.center")),
  TOP_RIGHT(2, 0, IdeBundle.message("presentation.assistant.configurable.alignment.top.right")),
  BOTTOM_LEFT(0, 2, IdeBundle.message("presentation.assistant.configurable.alignment.bottom.left")),
  BOTTOM_CENTER(1, 2, IdeBundle.message("presentation.assistant.configurable.alignment.bottom.center")),
  BOTTOM_RIGHT(2, 2, IdeBundle.message("presentation.assistant.configurable.alignment.bottom.right"));

  companion object {
    fun from(x: Int, y: Int): PresentationAssistantPopupAlignment = when (y) {
      0 -> when(x) {
        0 -> TOP_LEFT
        2 -> TOP_RIGHT
        else -> TOP_CENTER
      }
      else -> when(x) {
        0 -> BOTTOM_LEFT
        2 -> BOTTOM_RIGHT
        else -> BOTTOM_CENTER
      }
    }

    val defaultAlignment = BOTTOM_CENTER
  }
}

internal enum class PresentationAssistantTheme(val value: Int, @Nls val displayName: String, val foreground: Color,
                                      val background: Color, val border: Color, val keymapLabel: Color) {
  BRIGHT(0,
         IdeBundle.message("presentation.assistant.configurable.theme.bright"),
         JBColor.namedColor("PresentationAssistant.Bright.Popup.foreground", JBColor.foreground()),
         JBColor.namedColor("PresentationAssistant.Bright.PopupBackground", JBUI.CurrentTheme.Notification.BACKGROUND),
         JBColor.namedColor("PresentationAssistant.Bright.Popup.border", JBColor.border()),
         JBColor.namedColor("PresentationAssistant.Bright.keymapLabel", JBColor.foreground())),

  PALE(1,
       IdeBundle.message("presentation.assistant.configurable.theme.pale"),
       JBColor.namedColor("PresentationAssistant.Pale.Popup.foreground", JBColor.foreground()),
       JBColor.namedColor("PresentationAssistant.Pale.PopupBackground", JBUI.CurrentTheme.Notification.BACKGROUND),
       JBColor.namedColor("PresentationAssistant.Pale.Popup.border", JBColor.border()),
       JBColor.namedColor("PresentationAssistant.Pale.keymapLabel", JBColor.foreground()));

  companion object {
    fun fromValueOrDefault(value: Int?) = value.takeIf {
      PresentationAssistant.isThemeEnabled
    }.let {
      when(it) {
        BRIGHT.value-> BRIGHT
        PALE.value -> PALE
        else -> DEFAULT
      }
    }

    val DEFAULT: PresentationAssistantTheme get() =
      if (LafManager.getInstance().currentUIThemeLookAndFeel.name == "Light with Light Header") PALE else BRIGHT
  }
}

class PresentationAssistantState {
  var showActionDescriptions = false
  var popupSize: Int = 1
  var popupDuration = 4 * 1000

  /**
   * Holds the value for the horizontal alignment.
   *
   * Valid values:
   *  - 0: Aligns the element to the left.
   *  - 1: Aligns the element to the center.
   *  - 2: Aligns the element to the right.
   */
  var horizontalAlignment = 1

  /**
   * Holds the value for the vertical alignment.
   *
   * Valid values:
   *  - 0: Aligns the element to the top.
   *  - 2: Aligns the element to the bottom.
   */
  var verticalAlignment = 2

  var mainKeymapName = KeymapKind.defaultForOS().value
  var mainKeymapLabel: String = KeymapKind.defaultForOS().defaultLabel

  var showAlternativeKeymap = false
  var alternativeKeymapName: String = KeymapKind.defaultForOS().getAlternativeKind().value
  var alternativeKeymapLabel: String = KeymapKind.defaultForOS().getAlternativeKind().defaultLabel

  var deltaX: Int? = null
  var deltaY: Int? = null

  var theme: Int? = null
}

internal fun PresentationAssistantState.resetDelta() {
  deltaX = null
  deltaY = null
}

internal val PresentationAssistantState.alignmentIfNoDelta: PresentationAssistantPopupAlignment?
  get() {
    if (deltaX == null || deltaY == null) {
      return PresentationAssistantPopupAlignment.from(horizontalAlignment, verticalAlignment)
    }
    else {
      return null
    }
  }

internal fun PresentationAssistantState.mainKeymapKind(): KeymapKind = KeymapKind.from(mainKeymapName)

internal fun PresentationAssistantState.alternativeKeymapKind(): KeymapKind? {
  return alternativeKeymapName.takeIf { showAlternativeKeymap }?.let { KeymapKind.from(it) }
}

@State(name = "PresentationAssistantIJ", storages = [Storage("presentation-assistant-ij.xml")])
class PresentationAssistant : PersistentStateComponent<PresentationAssistantState>, Disposable {
  internal val configuration = PresentationAssistantState()
  private var warningAboutMacKeymapWasShown = false
  private var presenter: ShortcutPresenter? = null

  override fun getState() = configuration

  override fun loadState(p: PresentationAssistantState) {
    XmlSerializerUtil.copyBean(p, configuration)
  }

  fun initialize() {
    if (configuration.showActionDescriptions && presenter == null) {
      presenter = ShortcutPresenter()
    }
  }

  override fun dispose() {
    presenter?.disable()
  }

  fun updatePresenter(project: Project? = null, showInitialAction: Boolean = false) {
    val isEnabled = configuration.showActionDescriptions
    if (isEnabled && presenter == null) {
      presenter = ShortcutPresenter().apply {
        if (showInitialAction) {
          showActionInfo(ShortcutPresenter.ActionData(TogglePresentationAssistantAction.ID,
                                                      project,
                                                      TogglePresentationAssistantAction.name.get()))
        }
      }
    }
    else if (presenter != null) {
      if (!isEnabled) {
        presenter?.disable()
        presenter = null
      }
      else {
        presenter?.refreshPresentedPopupIfNeeded()
      }
    }
  }

  internal fun checkIfMacKeymapIsAvailable() {
    val alternativeKeymap = configuration.alternativeKeymapKind()
    if (warningAboutMacKeymapWasShown
        || SystemInfo.isMac
        || alternativeKeymap == null
        || !alternativeKeymap.isMac
        || alternativeKeymap.keymap != null) {
      return
    }

    val pluginId = PluginId.getId("com.intellij.plugins.macoskeymap")
    val plugin = PluginManagerCore.getPlugin(pluginId)
    if (plugin != null && plugin.isEnabled) return

    warningAboutMacKeymapWasShown = true
    showInstallMacKeymapPluginNotification(pluginId)
  }

  companion object {
    val isThemeEnabled: Boolean
      get() = ExperimentalUI.isNewUI() && Registry.`is`("ide.presentation.assistant.theme.enabled", false)
  }
}

private class PresentationAssistantListenerRegistrar : AppLifecycleListener, DynamicPluginListener {
  override fun appFrameCreated(commandLineArgs: MutableList<String>) {
    service<PresentationAssistant>().initialize()
  }
}
