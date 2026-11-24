// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataKeys
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeItemsPreviewProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemEmptyPresentation
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.Processor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.DefaultListCellRenderer
import javax.swing.ListCellRenderer

/**
 * A popup asks the providers of each project that [SeSearchProjectsProvider] names, see [SeSearchProjects].
 *
 * The popup belongs to the first project. The stub providers give one item each, and the item names the project of its
 * provider.
 */
@TestApplication
class SeSearchProjectsTest {
  private val popupProjectFixture = projectFixture()
  private val otherProjectFixture = projectFixture()

  @TestDisposable
  private lateinit var disposable: Disposable

  private val popupProject: Project get() = popupProjectFixture.get()
  private val otherProject: Project get() = otherProjectFixture.get()

  private val createdProviders = CopyOnWriteArrayList<TestProvider>()

  @Test
  fun aPopupWithoutSearchProjectsAsksItsProjectOnly() = timeoutRunBlocking {
    val holder = holder(searchProjects = null)

    val items = holder.items(SEARCHED_ID)

    assertEquals(listOf(popupProject), items.map { it.project() })
    assertNull(items.single().additionalInfo[SeItemDataKeys.SEARCH_PROJECT], "an item of a popup with one project names no search project")
  }

  @Test
  fun aNamedProviderAsksEachSearchProject() = timeoutRunBlocking {
    val holder = holder(SeSearchProjects(listOf(popupProject, otherProject), setOf(SeProviderId(SEARCHED_ID))))

    assertEquals(setOf(popupProject, otherProject), holder.items(SEARCHED_ID).map { it.project() }.toSet())
    assertEquals(listOf(popupProject), holder.items(SINGLE_ID).map { it.project() }, "a provider that the product does not name asks the popup project only")
  }

  @Test
  fun aSeparateTabAsksEachSearchProjectToo() = timeoutRunBlocking {
    val holder = holder(SeSearchProjects(listOf(popupProject, otherProject), setOf(SeProviderId(SEARCHED_ID))))

    assertEquals(setOf(popupProject, otherProject), holder.items(SEARCHED_ID, isAllTab = false).map { it.project() }.toSet())
  }

  @Test
  fun theFilterPicksOneSearchProject() = timeoutRunBlocking {
    val holder = holder(SeSearchProjects(listOf(popupProject, otherProject), setOf(SeProviderId(SEARCHED_ID))))

    val ofOther = holder.items(SEARCHED_ID, SeSearchProjectsFilter.withSelected(SeFilterState.Empty, listOf(otherProject)))
    val ofPopup = holder.items(SEARCHED_ID, SeSearchProjectsFilter.withSelected(SeFilterState.Empty, listOf(popupProject)))

    assertEquals(listOf(otherProject), ofOther.map { it.project() })
    assertEquals(listOf(popupProject), ofPopup.map { it.project() })
  }

  @Test
  fun aPickOfNoSearchProjectAsksEachSearchProject() = timeoutRunBlocking {
    val holder = holder(SeSearchProjects(listOf(popupProject, otherProject), setOf(SeProviderId(SEARCHED_ID))))
    val staleFilter = SeFilterState.Data(mapOf(SeSearchProjectsFilter.KEY to listOf("a project that closed")))

    assertEquals(setOf(popupProject, otherProject), holder.items(SEARCHED_ID, staleFilter).map { it.project() }.toSet())
  }

  @Test
  fun aPopupProjectOutsideTheSearchProjectsIsNotSearched() = timeoutRunBlocking {
    val holder = holder(SeSearchProjects(listOf(otherProject), setOf(SeProviderId(SEARCHED_ID))))

    assertEquals(listOf(otherProject), holder.items(SEARCHED_ID).map { it.project() })
  }

  @Test
  fun theProviderOfTheOwnerHandlesAFoundItem() = timeoutRunBlocking {
    val holder = holder(SeSearchProjects(listOf(popupProject, otherProject), setOf(SeProviderId(SEARCHED_ID))))
    val dataProvider = holder.get(SeProviderId(SEARCHED_ID), true)!!
    val itemOfOther = holder.items(SEARCHED_ID).single { it.project() === otherProject }
    val providerOfOther = createdProviders.single { it.id == SEARCHED_ID && it.project === otherProject }

    dataProvider.itemSelected(itemOfOther, 0, "query")
    dataProvider.getPreviewInfo(itemOfOther, popupProject)

    assertSame(providerOfOther, dataProvider.providerOf(itemOfOther))
    assertEquals(listOf(itemOfOther.fetchItemIfExists()), providerOfOther.selectedItems)
    assertEquals(listOf(otherProject), providerOfOther.previewProjects, "the preview of an item comes from the project that found it")
    assertSame(dataProvider.provider, createdProviders.single { it.id == SEARCHED_ID && it.project === popupProject },
               "the provider of the popup project stays the provider of the popup")
  }

  @Test
  fun aWrappedProviderGetsTheLegacyContributorOfItsProject() = timeoutRunBlocking {
    ExtensionTestUtil.maskExtensions(SearchEverywhereContributor.EP_NAME, listOf(TestContributorFactory()), disposable)
    val holder = holder(SeSearchProjects(listOf(popupProject, otherProject), setOf(SeProviderId(WRAPPED_ID))))

    val items = holder.items(WRAPPED_ID)

    assertEquals(setOf(popupProject, otherProject), items.map { it.project() }.toSet())
    assertEquals(setOf(popupProject, otherProject), createdProviders.filter { it.id == WRAPPED_ID }.map { it.contributorProject }.toSet(),
                 "each provider wraps the legacy contributor of its project")
  }

  @Test
  fun theHolderDisposesTheProvidersOfEachSearchProject() = timeoutRunBlocking {
    val holder = holder(SeSearchProjects(listOf(popupProject, otherProject), setOf(SeProviderId(SEARCHED_ID))))
    holder.items(SEARCHED_ID)
    val providers = createdProviders.filter { it.id == SEARCHED_ID }
    assertEquals(setOf(popupProject, otherProject), providers.map { it.project }.toSet())
    assertFalse(providers.any { it.isDisposed })

    Disposer.dispose(holder)

    assertTrue(providers.all { it.isDisposed }, "the providers of each search project go away with the holder")
  }

  @Test
  fun aCanceledFirstSearchDisposesTheProvidersThatItCreated() = timeoutRunBlocking {
    val blockedFactory = BlockedFactory(popupProject, createdProviders)
    val holder = holder(SeSearchProjects(listOf(popupProject, otherProject), setOf(SeProviderId(SEARCHED_ID), SeProviderId(BLOCKED_ID))),
                        blockedFactory)
    val search = launch(start = CoroutineStart.UNDISPATCHED) { holder.items(SEARCHED_ID) }
    blockedFactory.isBlocked.await()
    val providerOfOther = createdProviders.single { it.id == SEARCHED_ID && it.project === otherProject }
    assertFalse(providerOfOther.isDisposed)

    search.cancelAndJoin()

    assertTrue(providerOfOther.isDisposed, "a canceled search leaves no provider of another project behind")
    assertEquals(setOf(popupProject, otherProject), holder.items(SEARCHED_ID).map { it.project() }.toSet(),
                 "the next search creates the providers again")
  }

  private suspend fun holder(searchProjects: SeSearchProjects?, vararg extraFactories: SeItemsProviderFactory): SeProvidersHolder {
    val factories = listOf(TestFactory(SEARCHED_ID, createdProviders), TestFactory(SINGLE_ID, createdProviders), TestWrappedFactory(createdProviders)) +
                    extraFactories
    ExtensionTestUtil.maskExtensions(SeItemsProviderFactory.EP_NAME, factories, disposable)
    ExtensionTestUtil.maskExtensions(SeSearchProjectsProvider.EP_NAME, listOf(object : SeSearchProjectsProvider {
      override fun getSearchProjects(project: Project): SeSearchProjects? = searchProjects.takeIf { project === popupProject }
    }), disposable)

    val popupProject = popupProject
    val dataContext = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) popupProject else null }
    val initEvent = AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
    val session = SeSessionImpl(UUID.randomUUID().toString())
    val holder = SeProvidersHolder.initialize(initEvent, popupProject, session, "Test", withAdaptedLegacyContributors = false)
    Disposer.register(disposable) {
      Disposer.dispose(holder)
      SeItemRegistry.getInstance().closeSession(session)
    }
    return holder
  }

  private suspend fun SeProvidersHolder.items(
    providerId: String,
    filter: SeFilterState = SeFilterState.Empty,
    isAllTab: Boolean = true,
  ): List<SeItemData> = get(SeProviderId(providerId), isAllTab)!!.getItems(SeParams("query", filter)).toList()

  private fun SeItemData.project(): Project = (fetchItemIfExists() as TestItem).project

  private class TestItem(val project: Project) : SeItem {
    override fun weight(): Int = 0
    override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(false)
  }

  private class TestProvider(override val id: String, val project: Project, val contributorProject: Project? = null) : SeItemsProvider, SeItemsPreviewProvider {
    val selectedItems = CopyOnWriteArrayList<SeItem>()
    val previewProjects = CopyOnWriteArrayList<Project>()

    @Volatile
    var isDisposed = false

    override val displayName: String get() = id

    override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
      collector.put(TestItem(project))
    }

    override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
      selectedItems.add(item)
      return true
    }

    override suspend fun canBeShownInFindResults(): Boolean = false

    override suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? {
      previewProjects.add(project)
      return null
    }

    override fun dispose() {
      isDisposed = true
    }
  }

  private class TestFactory(override val id: String, private val createdProviders: MutableList<TestProvider>) : SeItemsProviderFactory {
    override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider =
      TestProvider(id, project!!).also { createdProviders.add(it) }
  }

  /** Gives a provider to [popupProject]. For another project, the first creation suspends until its search is canceled. */
  private class BlockedFactory(private val popupProject: Project, private val createdProviders: MutableList<TestProvider>) : SeItemsProviderFactory {
    override val id: String get() = BLOCKED_ID

    val isBlocked = CompletableDeferred<Unit>()

    override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider {
      if (project !== popupProject && isBlocked.complete(Unit)) awaitCancellation()
      return TestProvider(id, project!!).also { createdProviders.add(it) }
    }
  }

  private class TestWrappedFactory(private val createdProviders: MutableList<TestProvider>) : SeWrappedLegacyContributorItemsProviderFactory {
    override val id: String get() = WRAPPED_ID

    override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider =
      TestProvider(id, project!!, (legacyContributor as TestContributor).project).also { createdProviders.add(it) }
  }

  private class TestContributorFactory : SearchEverywhereContributorFactory<Any> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any> = TestContributor(initEvent.project!!)
  }

  private class TestContributor(val project: Project) : SearchEverywhereContributor<Any> {
    override fun getSearchProviderId(): String = WRAPPED_ID
    override fun getGroupName(): String = WRAPPED_ID
    override fun getSortWeight(): Int = 0
    override fun showInFindResults(): Boolean = false
    override fun isShownInSeparateTab(): Boolean = true
    override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in Any>) {}
    override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean = true
    override fun getElementsRenderer(): ListCellRenderer<in Any> = DefaultListCellRenderer()
  }

  private companion object {
    const val SEARCHED_ID = "SeSearchProjectsTest.searched"
    const val SINGLE_ID = "SeSearchProjectsTest.single"
    const val WRAPPED_ID = "SeSearchProjectsTest.wrapped"
    const val BLOCKED_ID = "SeSearchProjectsTest.blocked"
  }
}
