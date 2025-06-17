// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfoChunk
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextTabEmptyResultInfoProvider(
  private val filterEditor: SeTextFilterEditor? = null,
  private val project: Project?
) {

  fun getEmptyResultInfo(): SeEmptyResultInfo {
    val emptyInfoChunks = mutableListOf<SeEmptyResultInfoChunk>()
    emptyInfoChunks.add(SeEmptyResultInfoChunk(IdeBundle.message("searcheverywhere.nothing.found.for.all.anywhere") + "."))
    emptyInfoChunks.add(SeEmptyResultInfoChunk(text = "", onNewLine = true))

    if (showResetType()) {
      emptyInfoChunks.add(SeEmptyResultInfoChunk(FindBundle.message("message.nothingFound.used.options")))
      emptyInfoChunks.add(SeEmptyResultInfoChunk(text = FindBundle.message("find.popup.filemask.label"), onNewLine = true))
      emptyInfoChunks.add(SeEmptyResultInfoChunk(text = FindBundle.message("find.popup.clear.all.options"),
                                                 onNewLine = true,
                                                 attrs = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                 listener = { resetType() }))
    }

    if (showResetScope()) {
      emptyInfoChunks.add(SeEmptyResultInfoChunk(text = IdeBundle.message("searcheverywhere.try.to.reset.scope") +
                                                        " " + StringUtil.toLowerCase(EverythingGlobalScope.getNameText()),
                                                 onNewLine = true,
                                                 attrs = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                 listener = { resetScope() }))
    }

    return SeEmptyResultInfo(emptyInfoChunks)
  }

  private val toggleScopeAction: SearchEverywhereToggleAction?
    get() = filterEditor?.getActions()?.firstOrNull {
      it is SearchEverywhereToggleAction
    } as? SearchEverywhereToggleAction

  private fun showResetScope(): Boolean = toggleScopeAction?.isEverywhere == false

  private fun resetScope() {
    val action = toggleScopeAction ?: return
    action.isEverywhere = !action.isEverywhere
  }

  private fun showResetType(): Boolean = FindManager.getInstance(project).findInProjectModel.fileFilter != null

  private fun resetType() {
    filterEditor?.changeType(null)
  }
}
