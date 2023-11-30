// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.startup.importSettings.models.*
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.laf.darcula.DarculaInstaller
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.util.ui.StartupUiUtil

/**
 * Similar to ImportPerformer
 */

private val logger = logger<PartialImportPerformer>()

interface PartialImportPerformer {
  fun willPerform(settings: Settings): Boolean
  fun collectAllRequiredPlugins(settings: Settings): Set<PluginId>
  fun patchSettingsAfterPluginInstallation(settings: Settings, pluginIds: Set<String>): Settings
  fun perform(project: Project?, settings: Settings, pi: ProgressIndicator)
  fun performEdt(project: Project?, settings: Settings)
}

class LookAndFeelImportPerformer : PartialImportPerformer {
  override fun willPerform(settings: Settings): Boolean = settings.preferences[SettingsPreferencesKind.Laf] && settings.laf != null

  override fun collectAllRequiredPlugins(settings: Settings): Set<PluginId> {
    (settings.laf as? PluginLookAndFeel)?.let {
      return setOf(PluginId.getId(it.pluginId))
    }

    return emptySet()
  }

  override fun patchSettingsAfterPluginInstallation(settings: Settings, pluginIds: Set<String>): Settings {
    (settings.laf as? PluginLookAndFeel)?.let {
      if (pluginIds.contains(it.pluginId)) {
        settings.laf = BundledLookAndFeel.fromManager(it.transferableId, it.installedName)
      }
      else {
        settings.laf = it.fallback
      }
    }

    return settings
  }

  override fun perform(project: Project?, settings: Settings, pi: ProgressIndicator) {}

  override fun performEdt(project: Project?, settings: Settings) {
    (settings.laf as? BundledLookAndFeel)?.let {
      val laf = it.lafInfo
      val wasDark = StartupUiUtil.isUnderDarcula

      LafManager.getInstance().apply {
        setCurrentLookAndFeel(laf, false)
        updateUI()
        repaintUI()
      }

      val isDark = StartupUiUtil.isUnderDarcula

      if (isDark) {
        DarculaInstaller.install()
      }
      else if (wasDark) {
        DarculaInstaller.uninstall()
      }

      JBColor.setDark(isDark)
      IconLoader.setUseDarkIcons(isDark)

      LafManager.getInstance().updateUI()

      UISettings.getInstance().fireUISettingsChanged()
    }
  }
}

class SyntaxSchemeImportPerformer : PartialImportPerformer {
  override fun willPerform(settings: Settings): Boolean = settings.preferences[SettingsPreferencesKind.SyntaxScheme] && settings.syntaxScheme != null

  override fun collectAllRequiredPlugins(settings: Settings): Set<PluginId> {
    (settings.syntaxScheme as? PluginEditorColorScheme)?.let {
      return setOf(PluginId.getId(it.pluginId))
    }

    return emptySet()
  }

  override fun patchSettingsAfterPluginInstallation(settings: Settings, pluginIds: Set<String>): Settings {
    (settings.syntaxScheme as? PluginEditorColorScheme)?.let {
      if (pluginIds.contains(it.pluginId)) {
        settings.syntaxScheme = BundledEditorColorScheme.fromManager(it.installedName)
      }
      else {
        settings.syntaxScheme = it.fallback
      }
    }

    return settings
  }

  override fun perform(project: Project?, settings: Settings, pi: ProgressIndicator) {}

  override fun performEdt(project: Project?, settings: Settings) {
    val scheme = settings.syntaxScheme ?: return
    if (scheme !is BundledEditorColorScheme) {
      logger.warn("scheme is not BundledEditorColorScheme, but instead ${scheme::class.java.simpleName}")
      return
    }

    EditorColorsManager.getInstance().globalScheme
  }

}

class KeymapSchemeImportPerformer : PartialImportPerformer {
  override fun willPerform(settings: Settings): Boolean = settings.preferences[SettingsPreferencesKind.Keymap] && settings.keymap != null
  override fun collectAllRequiredPlugins(settings: Settings): Set<PluginId> {
    (settings.keymap as? PluginKeymap)?.let {
      return setOf(PluginId.getId(it.pluginId))
    }
    ((settings.keymap as? PatchedKeymap)?.parent as? PluginKeymap)?.let {
      return setOf(PluginId.getId(it.pluginId))
    }

    return emptySet()
  }

  override fun patchSettingsAfterPluginInstallation(settings: Settings, pluginIds: Set<String>): Settings {
    val km = settings.keymap
    if (km is PatchedKeymap) {
      val parent = km.parent as? PluginKeymap
      if (parent != null) {
        if (pluginIds.contains(parent.pluginId)) {
          km.parent = BundledKeymap.fromManager(km.transferableId, parent.installedName, emptyList())
        }
        else {
          km.parent = parent.fallback
        }
      }
    }
    if (km is PluginKeymap) {
      if (pluginIds.contains(km.pluginId)) {
        settings.keymap = BundledKeymap.fromManager(km.transferableId, km.installedName, emptyList())
      }
      else {
        settings.keymap = km.fallback
      }
    }

    return settings
  }

  override fun perform(project: Project?, settings: Settings, pi: ProgressIndicator) {}

  override fun performEdt(project: Project?, settings: Settings) {
    val keymap = settings.keymap ?: return

    when (keymap) {
      is BundledKeymap -> doBundledKeymap(keymap)
      is PatchedKeymap -> doPatchedKeymap(keymap)
    }
  }

  private fun doBundledKeymap(keymap: BundledKeymap) {
    val keymapManager = KeymapManagerEx.getInstanceEx()
    keymapManager.activeKeymap = keymap.keymap
  }

  private fun doPatchedKeymap(keymap: PatchedKeymap) {
    val keymapManager = KeymapManagerEx.getInstanceEx()
    val parent = (keymap.parent as? BundledKeymap)?.keymap
    requireNotNull(parent) { "parent must be BundledKeymap at this point" }

    if (keymap.overrides.isEmpty() && keymap.removal.isEmpty()) {
      keymapManager.activeKeymap = parent
      return
    }

    val derived = parent.deriveKeymap("${parent.name} (Migrated)")

    keymap.overrides.forEach {
      it.shortcuts.forEach { ks ->
        derived.addShortcut(it.actionId, ks)
      }
    }

    keymap.removal.forEach {
      val og = derived.getShortcuts(it.actionId).toSet()
      val toRemove = it.shortcuts.filter { og.contains(it) }
      toRemove.forEach { tr ->
        derived.removeShortcut(it.actionId, tr)
      }
    }

    keymapManager.schemeManager.addScheme(derived)
    keymapManager.activeKeymap = derived
    println()
  }
}

class RecentProjectsImportPerformer : PartialImportPerformer {
  override fun willPerform(settings: Settings): Boolean = settings.preferences[SettingsPreferencesKind.RecentProjects] && settings.recentProjects.isNotEmpty()

  override fun collectAllRequiredPlugins(settings: Settings): Set<PluginId> = emptySet()

  override fun patchSettingsAfterPluginInstallation(settings: Settings, pluginIds: Set<String>): Settings = settings

  override fun perform(project: Project?, settings: Settings, pi: ProgressIndicator) {}

  override fun performEdt(project: Project?, settings: Settings) {
    val recentProjectsManagerBase = RecentProjectsManagerBase.getInstanceEx()
    settings.recentProjects.sortedBy { (_, info) -> info.projectOpenTimestamp }.forEach { (path, info) ->
      recentProjectsManagerBase.addRecentPath(path, info)
    }
  }
}