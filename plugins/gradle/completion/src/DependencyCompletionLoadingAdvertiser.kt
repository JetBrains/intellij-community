// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DEFINITION
import com.intellij.codeInsight.completion.LookupActionKeys.SUPPRESS_QUICK_DOCUMENTATION
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerCore.isDisabled
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.SERVER
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
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
 * If the server request times out or fails, a dedicated message is shown in place of Phase 3:
 * - "Server query timed out. Press {ACTION_CODE_COMPLETION} to retry"
 * - "Server unavailable. Press {ACTION_CODE_COMPLETION} to retry"
 *
 * If the Ultimate plugin is disabled (no server access)
 * - Phase 1 (initial): "Searching for dependency libraries..."
 * - Phase 2 (all local results are shown): advertisement is cleared.
 */
@ApiStatus.Internal
class DependencyCompletionLoadingAdvertiser(
  private val freeMode: Boolean = isDisabled(PluginManagerCore.ULTIMATE_PLUGIN_ID),
) {
  private var serverResultsReceived = freeMode
  private var terminalServerStatus: TerminalServerStatus? = null

  /**
   * Call before starting flow collection to show the initial loading message.
   */
  fun showSearchingStatus() {
    replaceAdvertisement(GradleBundle.message("gradle.dependency.completion.searching.status"))
  }

  /**
   * Call for each event received from the completion flow.
   * Automatically updates or clears the loading message based on the result source.
   */
  fun onEvent(event: DependencyCompletionEvent<*>) {
    when (event) {
      is DependencyCompletionEvent.Item -> onItem(event)
      DependencyCompletionEvent.ServerTimedOut -> {
        serverResultsReceived = true
        if (terminalServerStatus == null) terminalServerStatus = TerminalServerStatus.TIMED_OUT
        onComplete()
      }
      is DependencyCompletionEvent.ServerFailed -> {
        serverResultsReceived = true
        terminalServerStatus = TerminalServerStatus.UNAVAILABLE
        onComplete()
      }
    }
  }

  private fun onItem(event: DependencyCompletionEvent.Item<*>) {
    if (serverResultsReceived) return

    if (event.result.source == SERVER) {
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
    if (freeMode) {
      clearAdvertisement()
      return
    }

    val shortcut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_CODE_COMPLETION)
    if (shortcut.isBlank()) {
      clearAdvertisement()
      return
    }

    val key = when (terminalServerStatus) {
      TerminalServerStatus.TIMED_OUT -> "gradle.dependency.completion.server.timeout"
      TerminalServerStatus.UNAVAILABLE -> "gradle.dependency.completion.server.unavailable"
      null -> "gradle.dependency.completion.server.search.tip"
    }
    replaceAdvertisement(GradleBundle.message(key, shortcut))
  }

  private fun replaceAdvertisement(@NlsContexts.PopupAdvertisement text: String) {
    CompletionServiceImpl.currentCompletionProgressIndicator
      ?.replaceAllAdvertisements(text, null)
  }

  private fun clearAdvertisement() {
    CompletionServiceImpl.currentCompletionProgressIndicator?.clearAllAdvertisements()
  }

  /**
   * If the server request ended in a terminal error (timeout / unavailable) and no items were
   * produced, add a placeholder item so the popup stays open with the advertiser
   * message — instead of being replaced with the "No suggestions" hint.
   *
   * No-op for auto-popup completion (where an empty popup would be intrusive),
   * for free mode (no server expected), and when at least one item was added.
   */
  fun addServerErrorPlaceholderIfNeeded(
    resultSet: CompletionResultSet,
    isAutoPopup: Boolean,
    hadResults: Boolean,
  ) {
    if (hadResults) return
    if (isAutoPopup) return
    if (freeMode) return
    val status = terminalServerStatus ?: return
    val key = when (status) {
      TerminalServerStatus.TIMED_OUT -> "gradle.dependency.completion.server.timeout.short"
      TerminalServerStatus.UNAVAILABLE -> "gradle.dependency.completion.server.unavailable.short"
    }
    val placeholder = LookupElementBuilder.create("")
      .withPresentableText(GradleBundle.message(key))
    placeholder.putUserData(SUPPRESS_QUICK_DEFINITION, true)
    placeholder.putUserData(SUPPRESS_QUICK_DOCUMENTATION, true)
    resultSet.withPrefixMatcher(PlainPrefixMatcher.ALWAYS_TRUE).addElement(placeholder)
  }

  /**
   * Enum representing the terminal status of a call to the dependency completion service.
   *
   * If the dependency completion service throws an TimeoutCancellationException from any server contributor, TIME_OUT status is set.
   * If the service throws any other exception (except for cancellation) from any server contributor,
   * the UNAVAILABLE status is set. This overrides a TIME_OUT status if set.
   */
  private enum class TerminalServerStatus { TIMED_OUT, UNAVAILABLE }
}