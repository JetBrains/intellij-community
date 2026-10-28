// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.testFramework

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.composedWeight
import com.intellij.platform.searchEverywhere.equalityProviders.SeEqualityChecker
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.isExactMatch
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeLocalItemDataProvider
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.intellij.platform.searchEverywhere.toProviderId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the Search Everywhere providers of a project in a test.
 *
 * The fixture creates the providers with [SeProvidersHolder], as the backend of a monolith does.
 * A test gets a provider by its ID and does not create a legacy contributor.
 * So one test can check a provider that wraps a legacy contributor and a provider that does not.
 * See [forEachGotoImplementation].
 *
 * Call the suspending functions from a test body that runs off the EDT.
 * A Java test calls the blocking functions. They also work on the EDT.
 */
@ApiStatus.Internal
class SeProvidersTestFixture private constructor(
  private val holder: SeProvidersHolder,
  private val session: SeSession,
) : Disposable {
  /**
   * Returns the provider with [providerId], or fails the test when the session has no such provider.
   *
   * With [isAllTab], the function returns the provider of the All tab.
   * Otherwise, it returns the provider of the separate tab when the provider has one.
   */
  fun getProvider(providerId: String, isAllTab: Boolean = false): SeItemsProvider = getDataProvider(providerId, isAllTab).provider

  private fun getDataProvider(providerId: String, isAllTab: Boolean): SeLocalItemDataProvider =
    holder.get(providerId.toProviderId(), isAllTab)
    ?: throw AssertionError("The Search Everywhere session has no provider '$providerId' (isAllTab=$isAllTab)")

  /**
   * Runs the search of [params] in the provider with [providerId] and returns the found items.
   *
   * The search goes through [SeLocalItemDataProvider], as in the backend.
   * So a write action that cancels an attempt of the provider starts the attempt again.
   * The search stops after the provider sends [limit] items.
   * [SeEqualityChecker] keeps one copy of each item, as the backend does.
   * A better copy takes the place of the copy that it replaces, as in the result list.
   * The exact matches go first, because the result list puts an exact match at the top.
   * Then the items go from the highest composed weight to the lowest.
   * Items with an equal weight keep the order in which the provider sent them, and a provider can send them in any order.
   */
  suspend fun search(providerId: String, params: SeParams, isAllTab: Boolean = false, limit: Int = DEFAULT_LIMIT): List<SeItem> {
    require(limit > 0) { "The limit must be positive: $limit" }

    val equalityChecker = SeEqualityChecker()
    val foundItems = ArrayList<SeItemData>()
    getDataProvider(providerId, isAllTab).getItems(params).take(limit).collect { itemData ->
      val checkedItemData = equalityChecker.checkAndUpdateIfNeeded(itemData) ?: return@collect
      val replacedIndex = foundItems.indexOfFirst { it.uuid in checkedItemData.uuidsToReplace }
      foundItems.removeAll { it.uuid in checkedItemData.uuidsToReplace }
      foundItems.add(if (replacedIndex >= 0) replacedIndex else foundItems.size, checkedItemData)
    }
    val order = compareByDescending<SeItemData> { it.isExactMatch }.thenByDescending { it.composedWeight }
    return foundItems.sortedWith(order).map {
      it.fetchItemIfExists() ?: throw AssertionError("The Search Everywhere session has no item ${it.uuid}")
    }
  }

  /**
   * Searches for [query] in the provider with [providerId] and returns the found items.
   *
   * [isEverywhere] also searches outside the project, as the everywhere toggle does.
   * The other parameters and the order of the items are the same as in the [search] with [SeParams].
   */
  suspend fun search(
    providerId: String,
    query: String,
    isEverywhere: Boolean = false,
    isAllTab: Boolean = false,
    limit: Int = DEFAULT_LIMIT,
  ): List<SeItem> {
    val filter = SeEverywhereFilterImpl(isAllTab = isAllTab, isEverywhere = isEverywhere, disabledProviderIds = emptyList())
    return search(providerId, SeParams(query, filter.toState()), isAllTab, limit)
  }

  /** Runs the [search] for [query] and returns the element of each found item. See [elementOf]. */
  suspend fun findElements(
    providerId: String,
    query: String,
    isEverywhere: Boolean = false,
    isAllTab: Boolean = false,
    limit: Int = DEFAULT_LIMIT,
  ): List<Any> = search(providerId, query, isEverywhere, isAllTab, limit).map(::elementOf)

  /** The blocking form of the [search] for [query] for a Java test. */
  @JvmOverloads
  fun searchBlocking(
    providerId: String,
    query: String,
    isEverywhere: Boolean = false,
    isAllTab: Boolean = false,
    limit: Int = DEFAULT_LIMIT,
  ): List<SeItem> = runBlockingInTest { search(providerId, query, isEverywhere, isAllTab, limit) }

  /** The blocking form of [findElements] for a Java test. */
  @JvmOverloads
  fun findElementsBlocking(
    providerId: String,
    query: String,
    isEverywhere: Boolean = false,
    isAllTab: Boolean = false,
    limit: Int = DEFAULT_LIMIT,
  ): List<Any> = runBlockingInTest { findElements(providerId, query, isEverywhere, isAllTab, limit) }

  override fun dispose() {
    Disposer.dispose(holder)
    SeItemRegistry.getInstance().closeSession(session)
  }

  companion object {
    /** The registry key that selects the implementation of the goto providers. See [forEachGotoImplementation]. */
    const val COROUTINE_BASED_GOTO_KEY: String = "search.everywhere.coroutine.based.goto"

    /** The number of items that the frontend asks a provider for at once. */
    const val DEFAULT_LIMIT: Int = 50

    /**
     * Opens a Search Everywhere session in [project] and creates its providers.
     *
     * [contextFile] is the file of the selected editor. Some providers sort the results by it.
     * The fixture goes away with [parentDisposable].
     */
    suspend fun create(project: Project, parentDisposable: Disposable, contextFile: PsiFile? = null): SeProvidersTestFixture {
      val dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(CommonDataKeys.PSI_FILE, contextFile)
        .build()
      val initEvent = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
      val session = SeSessionImpl(UUID.randomUUID().toString())
      val holder = SeProvidersHolder.initialize(initEvent, project, session, "Test", withAdaptedLegacyContributors = true)

      val fixture = SeProvidersTestFixture(holder, session)
      Disposer.register(parentDisposable, fixture)
      return fixture
    }

    /** The blocking form of [create] for a Java test. */
    @JvmStatic
    @JvmOverloads
    fun createBlocking(project: Project, parentDisposable: Disposable, contextFile: PsiFile? = null): SeProvidersTestFixture =
      runBlockingInTest { create(project, parentDisposable, contextFile) }

    /**
     * Runs [block] once with the coroutine based goto providers and once with the legacy based ones.
     *
     * A legacy based provider wraps a legacy contributor, and a coroutine based provider does not.
     * So a test that passes in both runs shows that the two implementations agree.
     * Each run gets a new fixture. After the run, [COROUTINE_BASED_GOTO_KEY] gets its previous value back.
     */
    suspend fun forEachGotoImplementation(
      project: Project,
      contextFile: PsiFile? = null,
      block: suspend (SeProvidersTestFixture) -> Unit,
    ) {
      for (isCoroutineBased in listOf(true, false)) {
        val disposable = Disposer.newDisposable("SeProvidersTestFixture: $COROUTINE_BASED_GOTO_KEY=$isCoroutineBased")
        try {
          RegistryManager.getInstance().get(COROUTINE_BASED_GOTO_KEY).setValue(isCoroutineBased, disposable)
          try {
            block(create(project, disposable, contextFile))
          }
          catch (e: AssertionError) {
            throw AssertionError("The run with $COROUTINE_BASED_GOTO_KEY=$isCoroutineBased failed: ${e.message}", e)
          }
        }
        finally {
          Disposer.dispose(disposable)
        }
      }
    }

    /**
     * Returns the element of [item].
     * A goto item holds its element in a [PSIPresentationBgRendererWrapper.ItemWithPresentation].
     * The function takes the element out of it.
     */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun elementOf(item: SeItem): Any = PSIPresentationBgRendererWrapper.getItem(item.rawObject)
  }
}

private val BLOCKING_TIMEOUT = 1.minutes

/**
 * Runs [block] on a background thread and waits for its result.
 * On the EDT, the wait dispatches the EDT events, so a provider can switch to the EDT.
 * The work stops when the wait ends.
 */
@OptIn(DelicateCoroutinesApi::class)
private fun <T> runBlockingInTest(block: suspend CoroutineScope.() -> T): T {
  val scope = GlobalScope.childScope("SeProvidersTestFixture blocking call", Dispatchers.Default)
  try {
    return PlatformTestUtil.waitForFuture(scope.async(block = block).asCompletableFuture(), BLOCKING_TIMEOUT.inWholeMilliseconds)
  }
  finally {
    scope.cancel()
  }
}
