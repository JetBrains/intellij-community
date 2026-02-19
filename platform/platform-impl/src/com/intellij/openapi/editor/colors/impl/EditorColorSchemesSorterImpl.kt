// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.TargetUIType
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.ide.ui.laf.defaultSchemeName
import com.intellij.ide.ui.laf.isThemeFromPlugin
import com.intellij.openapi.editor.colors.EditorColorSchemesSorter
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.Groups
import com.intellij.openapi.options.Scheme
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EditorColorSchemesSorterImpl: EditorColorSchemesSorter {
  override fun getOrderedSchemes(schemesMap: Map<String, EditorColorsScheme>): Groups<EditorColorsScheme> {
    val unhandledSchemes = schemesMap.toMutableMap().also { filterOutRedundantSchemes(it) }
    val groupInfos = mutableListOf<Groups.GroupInfo<EditorColorsScheme>>()

    // Sorting of the first section should always be aligned with themes
    val schemesFirstGroup = ThemeListProvider.getInstance().getShownThemes().infos.firstOrNull()?.items?.let {
      findSchemesFromThemes(it, unhandledSchemes)
    } ?: emptyList()

    var bundledSchemesFromThemes = listOf<EditorColorsScheme>()
    if (ExperimentalUI.isNewUI()) {
      if (schemesFirstGroup.isNotEmpty()) {
        groupInfos.add(Groups.GroupInfo(schemesFirstGroup))
      }
    }
    else {
      bundledSchemesFromThemes = schemesFirstGroup
    }

    val bundledSchemes = mutableListOf<EditorColorsScheme>()
    val customSchemes = mutableListOf<EditorColorsScheme>()
    val oldBundledSchemeNames = setOf("Darcula", "Darcula Contrast", "IntelliJ Light", "Default")

    unhandledSchemes.forEach { (_, scheme) ->
      val baseName = Scheme.getBaseName(scheme.name)
      val originalScheme = EditorColorsManager.getInstance().getScheme(baseName)

      if ((originalScheme as? EditorColorsSchemeImpl)?.isFromIntellij == true || oldBundledSchemeNames.contains(baseName)) {
        bundledSchemes.add(scheme)
      }
      else {
        customSchemes.add(scheme)
      }
    }

    bundledSchemes.sortBy { it.displayName }
    bundledSchemes.addAll(0, bundledSchemesFromThemes)

    if (bundledSchemes.isNotEmpty()) {
      groupInfos.add(Groups.GroupInfo(bundledSchemes))
    }

    if (customSchemes.isNotEmpty()) {
      customSchemes.sortWith(EditorColorSchemesComparator.INSTANCE)
      groupInfos.add(Groups.GroupInfo(customSchemes, IdeBundle.message("combobox.list.custom.section.title")))
    }

    return Groups(groupInfos)
  }

  private fun filterOutRedundantSchemes(schemes: MutableMap<String, EditorColorsScheme>) {
    val schemesToFilterOut = mutableListOf<String>()

    if (!ExperimentalUI.isNewUI()) {
      // Remove schemes from newUI themes
      val newUiSchemeIds = UiThemeProviderListManager.getInstance().getThemeListForTargetUI(TargetUIType.NEW).mapNotNull { theme ->
        theme.defaultSchemeName.takeIf { !theme.isThemeFromPlugin }
      }
      schemesToFilterOut.addAll(newUiSchemeIds)
    }

    schemesToFilterOut.forEach {
      schemes.remove(it)
      schemes.remove("${Scheme.EDITABLE_COPY_PREFIX}$it")
    }
  }

  private fun findSchemesFromThemes(themes: List<UIThemeLookAndFeelInfo>,
                                    schemes: MutableMap<String, EditorColorsScheme>): MutableList<EditorColorsScheme> {
    val result = mutableListOf<EditorColorsScheme>()
    val darculaFamilyNames = setOf("Darcula", "Darcula Contrast")

    themes.forEach { theme ->
      val schemeId = theme.defaultSchemeName

      if (darculaFamilyNames.contains(schemeId)) {
        darculaFamilyNames.forEach { darculaSchemeName ->
          findAndAddToSchemesGroup(darculaSchemeName, schemes, result)
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
}