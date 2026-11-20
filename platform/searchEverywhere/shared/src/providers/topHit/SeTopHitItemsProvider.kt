// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.topHit

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.actions.GotoActionAction
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.OptionsTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.actionSystem.AbbreviationManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeClosePopupRequester
import com.intellij.platform.searchEverywhere.SeCommandInfo
import com.intellij.platform.searchEverywhere.SeCommandInfoFactory
import com.intellij.platform.searchEverywhere.SeCommandsProviderInterface
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.providers.commands.SeCommandItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component

/**
 * A top hit: an action, an option, or another object of a [SearchTopHitProvider].
 */
@ApiStatus.Internal
class SeTopHitItem(
  override val rawObject: Any,
  private val weight: Int,
  private val project: Project,
) : SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTopHitItemPresentationProvider.getPresentation(rawObject, project)
}

/**
 * Finds the top hits: the actions of the abbreviations and the objects of the [SearchTopHitProvider]s.
 *
 * A split session has two providers. The provider on the host has [isHost] set, and the provider on the client does not.
 * An item of the client provider has a higher weight.
 * The commands of the provider are the ids of the [OptionsSearchTopHitProvider]s.
 * A top hit that is an [OptionsTopHitProvider] becomes a command item.
 */
@ApiStatus.Internal
class SeTopHitItemsProvider(
  private val isHost: Boolean,
  private val project: Project,
  private val contextComponent: Component?,
  override val displayName: @Nls String,
) : SeItemsProvider, SeCommandsProviderInterface {
  override val id: String get() = id(isHost)

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val query = params.inputQuery
    if (query.isEmpty()) return

    val weight = TOP_HIT_WEIGHT + if (isHost) 0 else 1
    coroutineToIndicator {
      findTopHits(query) { topHit ->
        val item = if (topHit is OptionsTopHitProvider) createCommandItem(topHit) else SeTopHitItem(topHit, weight, project)
        runBlockingCancellable { collector.put(item) }
      }
    }
  }

  /**
   * Makes a command item for [provider]. The selection of the item sets the search text to the command of [provider].
   */
  private fun createCommandItem(provider: OptionsTopHitProvider): SeCommandItem =
    SeCommandItem(SeCommandInfoFactory().create(provider.id, "", id))

  /**
   * Gives each top hit of [pattern] to [consumer]. When [consumer] returns `false`, the search stops.
   *
   * A command without an argument, such as `/editor`, has no top hits.
   */
  private fun findTopHits(pattern: String, consumer: (Any) -> Boolean) {
    if (pattern.startsWith(SearchTopHitProvider.getTopHitAccelerator()) && !pattern.contains(' ')) return
    if (!findAbbreviationActions(pattern, consumer)) return

    for (provider in SearchTopHitProvider.EP_NAME.extensionList) {
      var isStopped = false
      provider.consumeTopHits(pattern, { topHit -> if (!isStopped && !consumer(topHit)) isStopped = true }, project)
      if (isStopped) return
    }
  }

  /**
   * Gives each available action of the abbreviation [pattern] to [consumer].
   *
   * @return `false` when [consumer] stops the search.
   */
  private fun findAbbreviationActions(pattern: String, consumer: (Any) -> Boolean): Boolean {
    val actionManager = ActionManager.getInstance()
    for (actionId in AbbreviationManager.getInstance().findActions(pattern)) {
      val action = actionManager.getAction(actionId) ?: continue
      if (isAvailable(action) && !consumer(action)) return false
    }
    return true
  }

  private fun isAvailable(action: AnAction): Boolean {
    if (ActionUtil.isDumbMode(project) && !action.isDumbAware) return false

    val presentation = action.templatePresentation
    return presentation.isEnabled && presentation.isVisible && !presentation.text.isNullOrEmpty()
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val topHit = (item as? SeTopHitItem)?.rawObject ?: return false
    val closeRequester = currentCoroutineContext()[SeClosePopupRequester]
    return withContext(Dispatchers.EDT) {
      val shouldClose = processSelectedItem(topHit, modifiers)
      if (shouldClose) {
        // Close the popup in the same EDT event as the selection.
        // GotoActionAction.openOptionOrPerformAction runs the item later with invokeLater.
        // See SeActionsProvider for details (IJPL-243358).
        closeRequester?.close?.invoke()
      }
      shouldClose
    }
  }

  /**
   * Runs the selected [topHit] on the EDT. A boolean option changes its state in place.
   *
   * @return `true` when the popup must close.
   */
  private fun processSelectedItem(topHit: Any, modifiers: Int): Boolean {
    return when (topHit) {
      is BooleanOptionDescription -> {
        topHit.setOptionState(!topHit.isOptionEnabled)
        false
      }
      is AnAction, is OptionDescription -> {
        GotoActionAction.openOptionOrPerformAction(topHit, "", project, contextComponent, modifiers, null)
        true
      }
      else -> false
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean = false

  override fun getSupportedCommands(): List<SeCommandInfo> {
    val commandInfoFactory = SeCommandInfoFactory()
    return SearchTopHitProvider.EP_NAME.extensionList
      .filterIsInstance<OptionsSearchTopHitProvider>()
      .map { it.id }
      .distinct()
      .map { commandInfoFactory.create(it, "", id) }
  }

  override fun dispose() {}

  companion object {
    /** The weight of each item of the host provider. */
    const val TOP_HIT_WEIGHT: Int = 15000

    fun id(isHost: Boolean): String = if (isHost) SeProviderIdUtils.TOP_HIT_HOST_ID else SeProviderIdUtils.TOP_HIT_ID
  }
}
