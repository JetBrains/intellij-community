// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.util.NlsContexts.TabTitle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ContentUtilEx
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.util.GraphOptionsUtil.presentationForTabTitle
import com.intellij.vcs.log.visible.filters.getPresentation
import org.jetbrains.annotations.NonNls
import java.util.*

internal object VcsLogTabsUtil {
  fun getFullName(shortName: @TabTitle String): @TabTitle String {
    return ContentUtilEx.getFullName(VcsLogBundle.message("vcs.log.tab.name"), shortName)
  }

  @JvmStatic
  fun generateDisplayName(ui: VcsLogUiEx): @TabTitle String {
    return getFullName(generateShortDisplayName(ui))
  }

  fun generateShortDisplayName(ui: VcsLogUiEx): @TabTitle String {
    val options = ui.properties.getOrNull(MainVcsLogUiProperties.GRAPH_OPTIONS)
    val optionsPresentation = options?.presentationForTabTitle ?: ""
    val filters = ui.filterUi.filters
    val filtersPresentation = if (filters.isEmpty) "" else filters.getPresentation(withPrefix = optionsPresentation.isNotEmpty())
    @NlsSafe
    val presentation = listOf(optionsPresentation, filtersPresentation).filter { it.isNotEmpty() }.joinToString(separator = " ")
    return StringUtil.shortenTextWithEllipsis(presentation, 150, 20)
  }

  fun generateTabId(manager: VcsLogManager): @NonNls String {
    val existingIds = manager.logUis.map { it.id }.toSet()
    var newId: String
    do {
      newId = UUID.randomUUID().toString()
    }
    while (existingIds.contains(newId))
    return newId
  }
}