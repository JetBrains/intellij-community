// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoActionAction
import com.intellij.ide.actions.SearchEverywhereBaseAction
import com.intellij.ide.actions.SetShortcutAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellCheckResult
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereSpellingCorrector
import com.intellij.ide.actions.searcheverywhere.footer.ActionHistoryManager
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.ide.ui.search.BooleanOptionDescription.RequiresRebuild
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.ActionAsyncProvider
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.ide.util.gotoByName.GotoActionModel.MatchedValue
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.Utils.runUpdateSessionForActionSearch
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ActionShortcutRestrictions
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.searchEverywhere.SeClosePopupRequester
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoBuilder
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemWithDiagnostics
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.lang.ref.WeakReference
import java.util.function.Supplier


private const val MAX_TYPO_TOLERANT_CANDIDATES: Int = 2

private val LOG = logger<SeActionsProvider>()


@Internal
interface SeSpellCorrectionAwareItem {
  val correction: SearchEverywhereSpellCheckResult
  val effectiveSearchText: String
}

@Internal
class SeActionItem(
  val matchedValue: MatchedValue,
  val weight: Int,
  val extendedInfo: SeExtendedInfo?,
  override val effectiveSearchText: String,
  override val correction: SearchEverywhereSpellCheckResult,
  override val diagnostics: @NonNls String? = null,
) : SeItem, SeSpellCorrectionAwareItem, SeItemWithDiagnostics {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation {
    return SeActionPresentationProvider.get(matchedValue, extendedInfo, isMultiSelectionSupported = false)
  }

  override val rawObject: Any get() = matchedValue
}

@Internal
data class SeActionQueryVariant(
  val searchText: String,
  val correction: SearchEverywhereSpellCheckResult,
)

@Internal
open class SeActionsProvider(
  private val project: Project?,
  contextComponent: Component?,
  editor: Editor?,
  dataContext: DataContext?,
) : SeItemsProvider, SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.ACTIONS_ID
  override val displayName: @Nls String
    get() = IdeBundle.message("search.everywhere.group.name.actions")

  /**
   * The model that gives the actions and their presentations in the context of the popup.
   */
  internal val actionModel: GotoActionModel = GotoActionModel(project, contextComponent, editor, dataContext)
  private val actionProvider = ActionAsyncProvider(actionModel)

  private val contextComponentRef = WeakReference(contextComponent)
  private val dataContextRef = WeakReference(dataContext)
  private val dataContextProvider: () -> DataContext? = {
    dataContextRef.get() ?: contextComponentRef.get()?.let { DataManager.getInstance().getDataContext(it) }
  }

  private val isRecentEnabled: Boolean
    get() = Registry.`is`("search.everywhere.recents") || ApplicationManager.getApplication().isInternal

  private val spellingCorrector: SearchEverywhereSpellingCorrector? = SearchEverywhereSpellingCorrector.getInstance()
    ?.takeIf { it.isAvailableInTab(SeProviderIdUtils.ACTIONS_ID) }

  private fun getQueryVariants(inputQuery: String, isAllTab: Boolean): List<SeActionQueryVariant> {
    if (spellingCorrector == null || isAllTab) {
      return listOf(SeActionQueryVariant(inputQuery, SearchEverywhereSpellCheckResult.NoCorrection))
    }

    return buildList {
      add(SeActionQueryVariant(inputQuery, SearchEverywhereSpellCheckResult.NoCorrection))

      addAll(
        spellingCorrector.getAllCorrections(inputQuery, MAX_TYPO_TOLERANT_CANDIDATES)
          .map { correction -> SeActionQueryVariant(correction.correction, correction) }
      )
    }.distinctBy { it.searchText }
  }

  /**
   * Creates the item of [matchedValue] that [searchText] finds.
   */
  internal fun createItem(
    matchedValue: MatchedValue,
    weight: Int,
    searchText: String,
    correction: SearchEverywhereSpellCheckResult,
    diagnostics: @NonNls String? = null,
  ): SeActionItem {
    return SeActionItem(matchedValue,
                        weight,
                        createExtendedInfo(matchedValue),
                        searchText,
                        correction,
                        diagnostics)
  }

  /**
   * Creates the extended info of [matchedValue]: the description on the left and the action that assigns a shortcut on the right.
   */
  private fun createExtendedInfo(matchedValue: MatchedValue): SeExtendedInfo {
    val description = when (val value = matchedValue.value) {
      is GotoActionModel.ActionWrapper -> value.action.templatePresentation.description
      is AnAction -> value.templatePresentation.description
      is OptionDescription -> value.option
      else -> null
    }
    val rightAction = SeAssignShortcutAction(project, matchedValue)
    val keyStroke = rightAction.shortcutSet.shortcuts.filterIsInstance<KeyboardShortcut>().firstOrNull()?.firstKeyStroke

    return SeExtendedInfoBuilder()
      .withText(description)
      .withActionText(rightAction.templatePresentation.text)
      .withActionDescription(rightAction.templatePresentation.description)
      .withKeyCode(keyStroke?.keyCode)
      .withModifiers(keyStroke?.modifiers)
      .build()
  }

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val includeDisabled = SeActionsFilter.from(params.filter).includeDisabled
    val isAllTab = SeEverywhereFilterImpl.isAllTab(params.filter) == true

    coroutineScope {
      getQueryVariants(params.inputQuery, isAllTab).forEach { queryVariant ->
        launch {
          findItems(queryVariant.searchText, includeDisabled, includeRecent = !isAllTab) { matchedValue, weight ->
            val keepGoing = collector.put(createItem(matchedValue, weight, queryVariant.searchText, queryVariant.correction))
            if (!keepGoing) {
              this@coroutineScope.cancel()
            }
            keepGoing
          }
        }
      }
    }
  }

  /**
   * Finds the items for [pattern], and gives each item and its weight to [consumer].
   * When [consumer] returns `false`, the search can stop.
   *
   * For a blank [pattern], it finds the recent actions, but only when [includeRecent] is `true`.
   * When [includeDisabled] is `false`, it skips the actions that are not available in the context.
   */
  protected open suspend fun findItems(
    pattern: String,
    includeDisabled: Boolean,
    includeRecent: Boolean,
    consumer: suspend (MatchedValue, Int) -> Boolean,
  ) {
    val isRecentSearch = pattern.isBlank()
    if (isRecentSearch && !(includeRecent && isRecentEnabled)) return

    coroutineScope {
      actionModel.buildGroupMappings()
      runUpdateSessionForActionSearch(actionModel.updateSession) { presentationProvider ->
        if (isRecentSearch) {
          val actionIds: Set<String> = ActionHistoryManager.getInstance().state.ids
          actionProvider.processActions(this, presentationProvider, pattern, actionIds) { element: MatchedValue ->
            if (!includeDisabled && !(element.value as GotoActionModel.ActionWrapper).isAvailable) return@processActions true
            val action = element.unwrapAction() ?: return@processActions true

            val id = serviceAsync<ActionManager>().getId(action)
            consumer(element, actionIds.indexOf(id))
          }
          return@runUpdateSessionForActionSearch
        }

        LOG.debug("Start actions search")
        actionProvider.filterElements(this, presentationProvider, pattern) { element: MatchedValue ->
          val actionWrapper = element.value as? GotoActionModel.ActionWrapper
          if (!includeDisabled && actionWrapper != null && !actionWrapper.isAvailable) return@filterElements true

          consumer(element, element.matchingDegree)
        }
      }
    }
  }

  /**
   * Runs the selected [item] on the EDT.
   *
   * With the Alt modifier, it opens the dialog that assigns a shortcut to the action.
   * A boolean option changes its state in place.
   *
   * @return `true` when the popup must close.
   */
  protected open fun processSelectedItem(item: MatchedValue, modifiers: Int, searchText: String): Boolean {
    if (modifiers == InputEvent.ALT_DOWN_MASK) {
      showAssignShortcutDialog(project, item)
      return true
    }

    val selected = item.value

    if (selected is BooleanOptionDescription) {
      if (selected is RequiresRebuild) {
        actionModel.clearCaches() // release references to plugin actions so that the plugin can be unloaded successfully
        actionProvider.clearIntentions()
      }
      selected.setOptionState(!selected.isOptionEnabled)
      return false
    }

    if (isRecentEnabled) {
      saveRecentAction(item)
    }

    GotoActionAction.openOptionOrPerformAction(selected, searchText, project, contextComponentRef.get(), modifiers, dataContextProvider)
    val inplaceChange = (selected is GotoActionModel.ActionWrapper && (selected.action is ToggleAction ||
                                                                       selected.action is SearchEverywhereBaseAction))
    return !inplaceChange
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val actionItem = item as? SeActionItem ?: return false
    val selectionText = when (actionItem.correction) {
      is SearchEverywhereSpellCheckResult.Correction -> actionItem.effectiveSearchText
      SearchEverywhereSpellCheckResult.NoCorrection -> searchText
    }
    val closeRequester = currentCoroutineContext()[SeClosePopupRequester]
    return withContext(Dispatchers.EDT) {
      val shouldClose = processSelectedItem(actionItem.matchedValue, modifiers, selectionText)
      if (shouldClose) {
        // Close the popup in the same EDT event as processSelectedItem — before the
        // invokeLater queued by GotoActionAction.openOptionOrPerformAction can fire.
        // Mirrors the synchronous closePopup() call in old-SE SearchEverywhereUI.elementsSelected;
        // required on Wayland where setCancelOnWindowDeactivation is disabled (IJPL-243358).
        closeRequester?.close?.invoke()
      }
      shouldClose
    }
  }

  override suspend fun canBeShownInFindResults(): Boolean = false

  override suspend fun performExtendedAction(item: SeItem): Boolean {
    val matchedValue = (item as? SeActionItem)?.matchedValue ?: return false
    val rightAction = SeAssignShortcutAction(project, matchedValue)

    return withContext(Dispatchers.EDT) {
      val result = CompletableDeferred<Boolean>()
      DataManager.getInstance().getDataContextFromFocusAsync().onSuccess { context ->
        rightAction.actionPerformed(AnActionEvent.createEvent(
          context,
          Presentation(),
          ActionPlaces.ACTION_SEARCH,
          ActionUiKind.SEARCH_POPUP,
          null
        ))
        result.complete(true)
      }.onError {
        result.complete(false)
      }
      return@withContext result.await()
    }
  }

  override fun addDataForItem(item: SeItem, sink: DataSink) {
    val matchedValue = (item as? SeActionItem)?.matchedValue ?: return
    sink.lazy(SetShortcutAction.SELECTED_ACTION) { matchedValue.unwrapAction() }
  }

  override fun dispose() {}
}

/**
 * Opens the dialog that assigns a shortcut to the action of [value]. The extended info of an item shows this action.
 */
private class SeAssignShortcutAction(private val project: Project?, private val value: MatchedValue) :
  AnAction(Supplier { LangBundle.message("label.assign.shortcut") },
           Supplier {
             LangBundle.message("actions.tab.assign.a.shortcut", KeymapUtil.getFirstKeyboardShortcutText(
               KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))
             )
           }) {
  init {
    shortcutSet = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SHOW_INTENTION_ACTIONS)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    showAssignShortcutDialog(project, value)
  }
}

/**
 * Opens the dialog that assigns a shortcut to the action of [value].
 */
private fun showAssignShortcutDialog(project: Project?, value: MatchedValue) {
  val action = value.unwrapAction() ?: return
  val id = ActionManager.getInstance().getId(action) ?: return
  val activeKeymap = KeymapManager.getInstance()?.activeKeymap ?: return

  ApplicationManager.getApplication().invokeLater {
    val window = if (project != null) WindowManager.getInstance().suggestParentWindow(project)
    else KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    if (window == null) return@invokeLater
    KeymapPanel.addKeyboardShortcut(id, ActionShortcutRestrictions.getInstance().getForActionId(id), activeKeymap, window)
  }
}

private fun saveRecentAction(selected: MatchedValue) {
  val action = selected.unwrapAction() ?: return
  val id = ActionManager.getInstance().getId(action) ?: return

  val ids: MutableSet<String> = ActionHistoryManager.getInstance().state.ids
  if (ids.size < Registry.intValue("search.everywhere.recents.limit")) {
    ids.add(id)
  }
}

/**
 * Returns the action of this item, or `null` when the item is an option.
 */
private fun MatchedValue.unwrapAction(): AnAction? {
  return when (val value = value) {
    is GotoActionModel.ActionWrapper -> value.action
    else -> value as? AnAction
  }
}
