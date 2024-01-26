// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.ide.ui.laf.isThemeFromJetBrains
import com.intellij.openapi.editor.colors.EditorColorSchemesSorter
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.Groups
import com.intellij.openapi.options.Scheme
import com.intellij.ui.ExperimentalUI

class EditorColorSchemesSorterImpl: EditorColorSchemesSorter {
  override fun getOrderedSchemes(schemesMap: Map<String, EditorColorsScheme>): Groups<EditorColorsScheme> {
    val unhandledSchemes = schemesMap.toMutableMap().also {
      filterOutRedundantSchemes(it)
    }
    val schemesGroups = mutableListOf<MutableList<EditorColorsScheme>>()
    val themeGroups = ThemeListProvider.getInstance().getShownThemes()
    var hasCustomSchemesGroup = false
    val orphanedSchemeNames = listOf("Darcula", "Darcula Contrast", "IntelliJ Light", "Default")

    // Sort schemes from available themes
    themeGroups.infos.forEach { themes ->
      findSchemesFromThemes(themes, unhandledSchemes, orphanedSchemeNames).takeIf { it.isNotEmpty() }?.let {
        if (!hasCustomSchemesGroup && themes.items.firstOrNull()?.isThemeFromJetBrains == false) hasCustomSchemesGroup = true
        schemesGroups.add(it)
      }
    }

    val unhandledOrphanedSchemes = mutableListOf<EditorColorsScheme>()
    orphanedSchemeNames.forEach { findAndAddToSchemesGroup(it, unhandledSchemes, unhandledOrphanedSchemes) }

    if (unhandledOrphanedSchemes.isNotEmpty()) {
      if (hasCustomSchemesGroup) schemesGroups.add(schemesGroups.size - 1, unhandledOrphanedSchemes)
      else schemesGroups.add(unhandledOrphanedSchemes)
    }

    // Join unhandled schemes to the group with custom schemes
    if (unhandledSchemes.isNotEmpty()) {
      if (!hasCustomSchemesGroup) schemesGroups.add(mutableListOf())
      schemesGroups.lastOrNull()?.addAll(unhandledSchemes.values)

      hasCustomSchemesGroup = true
    }

    val groupInfos = schemesGroups.mapIndexed { index, editorColorsSchemes ->
      editorColorsSchemes.sortWith(EditorColorSchemesComparator.INSTANCE)
      val isCustomGroup = hasCustomSchemesGroup && schemesGroups.size - 1 == index
      Groups.GroupInfo(editorColorsSchemes, if (isCustomGroup) IdeBundle.message("combobox.list.custom.section.title") else "")
    }

    return Groups(groupInfos)
  }

  private fun findSchemesFromThemes(themes: Groups.GroupInfo<UIThemeLookAndFeelInfo>,
                                    schemes: MutableMap<String, EditorColorsScheme>,
                                    orphanedSchemeNames: List<String>): MutableList<EditorColorsScheme> {
    val result = mutableListOf<EditorColorsScheme>()

    themes.items.forEach { theme ->
      val schemeId = theme.editorSchemeId

      if (schemeId == null && theme.isThemeFromJetBrains && theme.name == "Darcula") {
        // Darcula theme doesn't have specified editorSchemeId. We do things manually here
        orphanedSchemeNames.forEach { orphanSchemeId ->
          findAndAddToSchemesGroup(orphanSchemeId, schemes, result)
        }
      }
      else {
        findAndAddToSchemesGroup(schemeId, schemes, result)
      }
    }

    return result
  }

  private fun findAndAddToSchemesGroup(schemeId: String?,
                                       source: MutableMap<String, EditorColorsScheme>,
                                       destination: MutableList<EditorColorsScheme>) {
    schemeId?.let {
      source[schemeId] ?: source["${Scheme.EDITABLE_COPY_PREFIX}$schemeId"]
    }?.let { scheme ->
      source.remove(scheme.name)
      destination.add(scheme)
    }
  }

  private fun filterOutRedundantSchemes(schemes: MutableMap<String, EditorColorsScheme>) {
    val schemesToFilterOut = mutableListOf<String>()

    if (!ExperimentalUI.isNewUI()) {
      // Remove schemes from newUI themes
      val newUiSchemeIds = UiThemeProviderListManager.getInstance().getThemeListForTargetUI(TargetUIType.NEW).mapNotNull { theme ->
        theme.editorSchemeId.takeIf { theme.isThemeFromJetBrains }
      }
      schemesToFilterOut.addAll(newUiSchemeIds)
    }

    schemesToFilterOut.forEach {
      schemes.remove(it)
      schemes.remove("${Scheme.EDITABLE_COPY_PREFIX}$it")
    }
  }
}