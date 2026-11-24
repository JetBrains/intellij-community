// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.concurrency.JobLauncher
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlContributorReplacement
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataKeys
import com.intellij.platform.searchEverywhere.SeItemsPreviewProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemEmptyPresentation
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.Processor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.DefaultListCellRenderer
import javax.swing.ListCellRenderer

/**
 * A popup asks the providers of each project that the product names, see [SeSearchProjects] and [SeSearchProjectHolders].
 *
 * The popup belongs to the first project. The stub providers give one item each, and the item names the project of its
 * provider. [TestHolders] stands for the product: it gives each other project a holder of its own.
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

  /** The project of each legacy contributor that [TestContributorFactory] created. */
  private val contributorProjects = CopyOnWriteArrayList<Project>()

  @Test
  fun aPopupWithoutSearchProjectsAsksItsProjectOnly() = timeoutRunBlocking {
    val holder = holder(searchProjects = null)

    val items = holder.items(SEARCHED_ID)

    assertEquals(listOf(popupProject), items.map { it.project() })
    assertNull(items.single().additionalInfo[SeItemDataKeys.SEARCH_PROJECT], "an item of a popup with one project names no search project")
  }

  @Test
  fun aNamedProviderAsksEachSearchProject() = timeoutRunBlocking {
    val holder = holder(TestSearchProjects(listOf(popupProject, otherProject), SEARCHED_ID))

    assertEquals(setOf(popupProject, otherProject), holder.items(SEARCHED_ID).map { it.project() }.toSet())
    assertEquals(listOf(popupProject), holder.items(SINGLE_ID).map { it.project() }, "a provider that the product does not name asks the popup project only")
  }

  @Test
  fun aSeparateTabAsksEachSearchProjectToo() = timeoutRunBlocking {
    val holder = holder(TestSearchProjects(listOf(popupProject, otherProject), SEARCHED_ID))

    assertEquals(setOf(popupProject, otherProject), holder.items(SEARCHED_ID, isAllTab = false).map { it.project() }.toSet())
  }

  @Test
  fun theProductPicksTheSearchProjectsOfASearch() = timeoutRunBlocking {
    val holder = holder(TestSearchProjects(listOf(popupProject, otherProject), SEARCHED_ID))

    val ofOther = holder.items(SEARCHED_ID, pickOf(otherProject))
    val ofPopup = holder.items(SEARCHED_ID, pickOf(popupProject))

    assertEquals(listOf(otherProject), ofOther.map { it.project() })
    assertEquals(listOf(popupProject), ofPopup.map { it.project() })
  }

  @Test
  fun aPopupProjectOutsideTheSearchProjectsIsNotSearched() = timeoutRunBlocking {
    val holder = holder(TestSearchProjects(listOf(otherProject), SEARCHED_ID))

    assertEquals(listOf(otherProject), holder.items(SEARCHED_ID).map { it.project() })
  }

  @Test
  fun theProviderOfTheOwnerHandlesAFoundItem() = timeoutRunBlocking {
    val holder = holder(TestSearchProjects(listOf(popupProject, otherProject), SEARCHED_ID))
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
    ExtensionTestUtil.maskExtensions(SearchEverywhereContributor.EP_NAME, listOf(TestContributorFactory(contributorProjects)), disposable)
    val holder = holder(TestSearchProjects(listOf(popupProject, otherProject), WRAPPED_ID))

    val items = holder.items(WRAPPED_ID)

    assertEquals(setOf(popupProject, otherProject), items.map { it.project() }.toSet())
    assertEquals(setOf(popupProject, otherProject), createdProviders.filter { it.id == WRAPPED_ID }.map { it.contributorProject }.toSet(),
                 "each provider wraps the legacy contributor of its project")
  }

  @Test
  fun theHolderDisposesTheHoldersOfTheSearchProjects() = timeoutRunBlocking {
    val searchProjects = TestSearchProjects(listOf(popupProject, otherProject), SEARCHED_ID)
    val holder = holder(searchProjects)
    holder.items(SEARCHED_ID)
    val providers = createdProviders.filter { it.id == SEARCHED_ID }
    assertEquals(setOf(popupProject, otherProject), providers.map { it.project }.toSet())
    assertFalse(providers.any { it.isDisposed })

    Disposer.dispose(holder)

    assertTrue(searchProjects.holders.single().isDisposed, "the holder of the popup disposes the holders of the product")
    assertTrue(providers.all { it.isDisposed }, "the providers of each search project go away with the holder")
  }

  @Test
  fun theFindToolWindowGetsTheItemsOfThePopupProjectOnly() = timeoutRunBlocking {
    val holder = holder(TestSearchProjects(listOf(popupProject, otherProject), SEARCHED_ID))
    val dataProvider = holder.get(SeProviderId(SEARCHED_ID), true)!!

    val exported = dataProvider.getRawItemsWithOperationLifetime(SeParams("query", SeFilterState.Empty), disposable).toList()
    val exportedOfOther = dataProvider.getRawItemsWithOperationLifetime(SeParams("query", pickOf(otherProject)), disposable).toList()

    assertEquals(listOf(popupProject), exported.map { (it as TestItem).project }, "the Find tool window shows the items in the popup project")
    assertEquals(emptyList<SeItem>(), exportedOfOther, "a pick of another project exports no item")
  }

  @Test
  fun eachSearchOfAProjectOfSeveralIsMarkedWithItsProject() = timeoutRunBlocking {
    val holder = holder(TestSearchProjects(listOf(popupProject, otherProject), SEARCHED_ID))

    holder.items(SEARCHED_ID)
    holder.items(SINGLE_ID)

    val searched = createdProviders.filter { it.id == SEARCHED_ID }
    assertEquals(setOf(popupProject, otherProject), searched.map { it.project }.toSet())
    assertTrue(searched.all { it.searchMarks.single() }, "the search of each project is marked, also on a forked thread")
    val single = createdProviders.filter { it.id == SINGLE_ID }
    assertFalse(single.single { it.project === popupProject }.searchMarks.single(), "a provider of the popup project only is not marked")
    assertEquals(emptyList<Boolean>(), single.single { it.project === otherProject }.searchMarks,
                 "the holder of another project has a provider of each id, and no search asks the ids that the product does not name")
  }

  @Test
  fun aSlowSearchProjectDelaysNoItemOfAnother() = timeoutRunBlocking {
    val gate = CompletableDeferred<Unit>()
    val holder = holder(TestSearchProjects(listOf(popupProject, otherProject), SEARCHED_ID, gateOfOther = gate))

    val first = holder.get(SeProviderId(SEARCHED_ID), true)!!.getItems(SeParams("query", SeFilterState.Empty)).first()

    assertSame(popupProject, first.project(), "the popup project finds its item while the holder of the other project waits")
    gate.complete(Unit)
    assertEquals(setOf(popupProject, otherProject), holder.items(SEARCHED_ID).map { it.project() }.toSet())
  }

  @Test
  fun aPopupOfOneProjectDoesNotMarkItsSearch() = timeoutRunBlocking {
    val holder = holder(TestSearchProjects(listOf(popupProject), SEARCHED_ID))

    holder.items(SEARCHED_ID)

    assertFalse(createdProviders.single { it.id == SEARCHED_ID }.searchMarks.single(), "a popup that searches one project does not mark it")
  }

  @Test
  fun concurrentHoldersGiveEachSeparateTabReplacementTheEventOfItsProject() = timeoutRunBlocking {
    ExtensionTestUtil.maskExtensions(SearchEverywhereContributor.EP_NAME, listOf(TestContributorFactory(contributorProjects)), disposable)
    val replacement = RecordingReplacement()
    ExtensionTestUtil.maskExtensions(SearchEverywhereMlContributorReplacement.EP_NAME, listOf(replacement), disposable)
    val projects = listOf(popupProject, otherProject)
    maskSearchProjects { null }
    maskFactories()

    // The holders of the search projects of a popup initialize at the same time.
    coroutineScope {
      repeat(CONCURRENT_HOLDERS) { index ->
        launch(Dispatchers.Default) { openHolder(projects[index % projects.size]) }
      }
    }

    assertEquals(CONCURRENT_HOLDERS, replacement.seen.size, "each holder replaced the contributor of its project")
    assertEquals(emptyList<Pair<Project, Project?>>(), replacement.seen.filter { (contributor, event) -> contributor !== event },
                 "a replacement reads the event of the project of its contributor, not the event of another holder")
  }

  private suspend fun holder(searchProjects: SeSearchProjects?): SeProvidersHolder {
    maskSearchProjects { project -> searchProjects.takeIf { project === popupProject } }
    maskFactories()
    return openHolder(popupProject)
  }

  private fun maskFactories() {
    val factories = listOf(TestFactory(SEARCHED_ID, createdProviders), TestFactory(SINGLE_ID, createdProviders), TestWrappedFactory(createdProviders))
    ExtensionTestUtil.maskExtensions(SeItemsProviderFactory.EP_NAME, factories, disposable)
  }

  private fun maskSearchProjects(searchProjects: (Project) -> SeSearchProjects?) {
    ExtensionTestUtil.maskExtensions(SeSearchProjectsProvider.EP_NAME, listOf(object : SeSearchProjectsProvider {
      override fun getSearchProjects(project: Project): SeSearchProjects? = searchProjects(project)
    }), disposable)
  }

  /** A popup of [popupProject] with the masked factories. The test disposable disposes it. */
  private suspend fun openHolder(popupProject: Project): SeProvidersHolder {
    val session = SeSessionImpl(UUID.randomUUID().toString())
    val holder = SeProvidersHolder.initialize(eventOf(popupProject), popupProject, session, "Test", withAdaptedLegacyContributors = false)
    Disposer.register(disposable) {
      Disposer.dispose(holder)
      SeItemRegistry.getInstance().closeSession(session)
    }
    return holder
  }

  private fun eventOf(project: Project): AnActionEvent {
    val dataContext = DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null }
    return AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
  }

  /** A filter state that makes [TestHolders] search [project] only. */
  private fun pickOf(project: Project): SeFilterState = SeFilterState.Data(mapOf(PICK_KEY to listOf(SeSearchProjectHolder.keyOf(project))))

  /** The product: [projects] are the search projects, and the providers of [providerId] search each of them. */
  private inner class TestSearchProjects(
    private val projects: List<Project>,
    providerId: String,
    private val gateOfOther: Deferred<Unit>? = null,
  ) : SeSearchProjects {
    val holders = CopyOnWriteArrayList<TestHolders>()

    override val providerIds: Set<SeProviderId> = setOf(SeProviderId(providerId))

    override fun createHolders(initEvent: AnActionEvent, session: SeSession): SeSearchProjectHolders =
      TestHolders(projects, session, gateOfOther).also { holders.add(it) }
  }

  /**
   * Gives each of [projects] but the popup project a holder of its own with the first search that asks it.
   * The creation of each holder waits for [gate].
   */
  private inner class TestHolders(
    private val projects: List<Project>,
    private val session: SeSession,
    private val gate: Deferred<Unit>?,
  ) : SeSearchProjectHolders {
    private val holders = ConcurrentHashMap<Project, Deferred<SeProvidersHolder>>()
    private val scope = CoroutineScope(SupervisorJob())

    @Volatile
    var isDisposed = false

    override val searchesSeveralProjects: Boolean get() = projects.size > 1

    override fun searched(filter: SeFilterState): List<SeSearchProjectHolder> {
      val pickedKeys = filter.get(PICK_KEY)
      val picked = if (pickedKeys == null) projects else projects.filter { SeSearchProjectHolder.keyOf(it) in pickedKeys }
      return picked.map { project -> SeSearchProjectHolder(project, if (project === popupProject) null else holderOf(project)) }
    }

    override fun created(key: String): SeSearchProjectHolder? =
      holders.entries.firstOrNull { SeSearchProjectHolder.keyOf(it.key) == key }?.let { SeSearchProjectHolder(it.key, it.value) }

    private fun holderOf(project: Project): Deferred<SeProvidersHolder> = holders.computeIfAbsent(project) {
      scope.async {
        gate?.await()
        SeProvidersHolder.initialize(eventOf(project), project, session, "Test", withAdaptedLegacyContributors = false)
      }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun dispose() {
      isDisposed = true
      scope.cancel()
      holders.values.forEach { holder -> holder.invokeOnCompletion { if (it == null) Disposer.dispose(holder.getCompleted()) } }
    }
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

    /** For each search: whether each forked task of a read action saw the mark of [project]. A goto model forks so. */
    val searchMarks = CopyOnWriteArrayList<Boolean>()

    @Volatile
    var isDisposed = false

    override val displayName: String get() = id

    override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
      searchMarks.add(readAction {
        blockingContextToIndicator {
          val marks = CopyOnWriteArrayList<Boolean>()
          JobLauncher.getInstance().invokeConcurrentlyUnderContextProgress((1..FORKED_TASKS).toList()) {
            marks.add(SeSearchProjectContext.isSearchOf(project))
          }
          marks.toSet().single()
        }
      })
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

  private class TestWrappedFactory(private val createdProviders: MutableList<TestProvider>) : SeWrappedLegacyContributorItemsProviderFactory {
    override val id: String get() = WRAPPED_ID

    override suspend fun getItemsProvider(project: Project?, legacyContributor: SearchEverywhereContributor<Any>): SeItemsProvider =
      TestProvider(id, project!!, (legacyContributor as TestContributor).project).also { createdProviders.add(it) }
  }

  private class TestContributorFactory(private val contributorProjects: MutableList<Project>) : SearchEverywhereContributorFactory<Any> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any> =
      TestContributor(initEvent.project!!).also { contributorProjects.add(it.project) }
  }

  /** Records the project of each replaced contributor and the project of the event that the replacement reads. */
  private class RecordingReplacement : SearchEverywhereMlContributorReplacement {
    val seen = CopyOnWriteArrayList<Pair<Project, Project?>>()

    override fun replaceInSeparateTab(contributor: SearchEverywhereContributor<*>): SearchEverywhereContributor<*> {
      if (contributor is TestContributor) seen.add(contributor.project to SearchEverywhereMlContributorReplacement.initEvent.get()?.project)
      return contributor
    }
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
    const val PICK_KEY = "SeSearchProjectsTest.pick"
    const val CONCURRENT_HOLDERS = 40
    const val FORKED_TASKS = 8
  }
}
