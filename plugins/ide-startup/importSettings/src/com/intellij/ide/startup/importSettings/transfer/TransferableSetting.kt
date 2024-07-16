// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.data.BaseSetting
import com.intellij.ide.startup.importSettings.data.ChildSetting
import com.intellij.ide.startup.importSettings.data.Multiple
import com.intellij.ide.startup.importSettings.models.*
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.util.text.nullize
import javax.swing.Icon
import javax.swing.KeyStroke
import kotlin.io.path.Path

open class TransferableSetting(
  override val id: String,
  override val name: String,
  override val icon: Icon,
  override val comment: String?
) : BaseSetting {

  companion object {

    const val UI_ID = "ui"
    const val KEYMAP_ID = "keymap"
    const val PLUGINS_ID = "plugins"
    const val RECENT_PROJECTS_ID = "recentProjects"

    fun uiTheme(laf: ILookAndFeel): TransferableSetting {
      val themeName = laf.getPreview().name
      return TransferableSetting(
        UI_ID,
        ImportSettingsBundle.message("transfer.settings.ui-theme"),
        StartupImportIcons.Icons.ColorPicker,
        themeName
      )
    }

    fun keymap(keymap: Keymap): Multiple {
      val customShortcuts = (keymap as? PatchedKeymap)?.overrides
      val customShortcutCount = customShortcuts?.sumOf { it.shortcuts.size } ?: 0
      val title = if (customShortcutCount == 0)
        keymap.displayName
      else ImportSettingsBundle.message("transfer.settings.keymap-with-custom-shortcuts", keymap.displayName, customShortcutCount)
      val examples = keymap.demoShortcuts.map { DemoShortcut(it.humanName, it.defaultShortcut) }
      val custom = customShortcuts?.flatMap { shortcut ->
        val action = ActionManager.getInstance().getAction(shortcut.actionId) ?: run {
          thisLogger().error("Cannot find action ${shortcut.actionId}.")
          return@flatMap emptyList()
        }
        val name = action.templateText.nullize() ?: run {
          thisLogger().error("Cannot determine text of action ${shortcut.actionId}.")
          return@flatMap emptyList()
        }
        shortcut.shortcuts.map { DemoShortcut(name, it) }
      }
      val items = buildList {
        add(examples)
        custom?.let(::add)
      }
      return TransferableSettingGroup(
        KEYMAP_ID,
        ImportSettingsBundle.message("transfer.settings.keymap"),
        StartupImportIcons.Icons.Keyboard,
        title,
        items
      )
    }

    fun plugins(features: Collection<FeatureInfo>): Multiple {
      val limitForPreview = 3
      val items = features.asSequence().filter { !it.isHidden }.map(::FeatureSetting).toList()
      val comment = NlsMessages.formatNarrowAndList(items.take(limitForPreview).map { it.nameForPreview })
      val itemGroups = if (items.size > limitForPreview) listOf(items) else emptyList()
      return TransferableSettingGroup(
        PLUGINS_ID,
        ImportSettingsBundle.message("transfer.settings.plugins"),
        StartupImportIcons.Icons.Plugin,
        comment,
        itemGroups
      )
    }

    fun recentProjects(projects: List<RecentPathInfo>): Multiple {
      val limitForPreview = 6
      val items = projects.map(::RecentProjectSetting)
      val comment = NlsMessages.formatNarrowAndList(items.take(limitForPreview).map { it.name })
      val itemGroups = if (items.size > limitForPreview) listOf(items) else emptyList()
      return TransferableSettingGroup(
        RECENT_PROJECTS_ID,
        ImportSettingsBundle.message("transfer.settings.recent-projects"),
        StartupImportIcons.Icons.Recent,
        comment,
        itemGroups
      )
    }
  }
}

private class TransferableSettingGroup(
  override val id: String,
  override val name: String,
  override val icon: Icon,
  override val comment: String?,
  override val list: List<List<ChildSetting>>
) : Multiple

private class DemoShortcut(override val name: String, shortcut: Any) : ChildSetting {
  override val id = ""
  override val leftComment = null
  override val rightComment = run {
    fun getKeyStrokeText(keyStroke: KeyStroke) =
      if (ClientSystemInfo.isMac()) MacKeymapUtil.getKeyStrokeText(keyStroke)
      else KeymapUtil.getKeystrokeText(keyStroke)

    when (shortcut) {
      is KeyboardShortcut -> buildString {
        append(getKeyStrokeText(shortcut.firstKeyStroke))
        shortcut.secondKeyStroke?.let {
          append(", ")
          append(getKeyStrokeText(it))
        }
      }
      is DummyKeyboardShortcut -> buildString {
        append(shortcut.firstKeyStroke)
        shortcut.secondKeyStroke?.let {
          if (!ClientSystemInfo.isMac()) append("+")
          append(shortcut.secondKeyStroke)
        }
      }
      else -> {
        thisLogger().error("Cannot extract keyboard shortcut from object $shortcut")
        null
      }
    }
  }
}

private class FeatureSetting(feature: FeatureInfo) : ChildSetting {
  override val id = ""
  override val name = feature.name
  override val leftComment = when(feature) {
    is BuiltInFeature -> ImportSettingsBundle.message("transfer.setting.feature.built-in")
    is PluginFeature ->
      if (PluginManager.isPluginInstalled(PluginId.getId(feature.pluginId)))
        ImportSettingsBundle.message("transfer.setting.feature.built-in")
      else null
    else -> null
  }
  override val rightComment = null
  val nameForPreview: String
    get() = buildString {
      append(name)
      leftComment?.let {
        append(' ')
        append(it)
      }
    }
}

private class RecentProjectSetting(item: RecentPathInfo) : ChildSetting {
  override val id = ""
  override val name = Path(item.path).fileName?.toString() ?: item.path
  override val leftComment = null
  override val rightComment = null
}