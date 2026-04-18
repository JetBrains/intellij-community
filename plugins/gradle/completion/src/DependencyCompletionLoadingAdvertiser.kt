// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.repository.search.completion.api.BaseDependencyCompletionResult
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.GradleBundle

/**
 * Manages loading advertisement messages in the completion popup during dependency completion.
 *
 * Shows a status message in the completion popup advertiser while server results are being fetched.
 * - Phase 1 (initial): "Searching for dependency libraries..."
 * - Phase 2 (local results arrived, server still pending): "Server is still being queried..."
 * - Phase 3 (server responded or timed out): "Press {ACTION_CODE_COMPLETION} to query the server"
 *
 * If the Ultimate plugin is disabled (no server access)
 * - Phase 1 (initial): "Searching for dependency libraries..."
 * - Phase 2 (all local results are shown): advertisement is cleared.
 */
@ApiStatus.Internal
class DependencyCompletionLoadingAdvertiser {
  private var serverResultsReceived = isFreeMode()

  /**
   * Call before starting flow collection to show the initial loading message.
   */
  fun showSearchingServer() {
    replaceAdvertisement(GradleBundle.message("gradle.dependency.completion.searching.server"))
  }

  /**
   * Call for each item received from the completion flow.
   * Automatically updates or clears the loading message based on the result source.
   */
  fun onResultReceived(result: BaseDependencyCompletionResult) {
    if (serverResultsReceived) return

    if (result.source == DependencyCompletionContributionSource.SERVER) {
      serverResultsReceived = true
      onComplete()
    }
    else {
      replaceAdvertisement(GradleBundle.message("gradle.dependency.completion.server.still.searching"))
    }
  }

  /**
   * Call after flow collection completes to ensure the loading message is removed.
   */
  fun onComplete() {
    if (isFreeMode()) {
      clearAdvertisement()
      return
    }

    val shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION)
    if (shortcut.isNotBlank()) {
      replaceAdvertisement(GradleBundle.message("gradle.dependency.completion.server.search.tip", shortcut))
    }
    else {
      clearAdvertisement()
    }
  }

  private fun replaceAdvertisement(@NlsContexts.PopupAdvertisement text: String) {
    CompletionServiceImpl.currentCompletionProgressIndicator
      ?.replaceAllAdvertisements(text, null)
  }

  private fun clearAdvertisement() {
    CompletionServiceImpl.currentCompletionProgressIndicator?.clearAllAdvertisements()
  }

  private fun isFreeMode(): Boolean {
    return isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID)
  }
}