// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** The [provider] that searches [project], one of the [SeSearchProjects] of a popup. [key] stands for [project] in a filter state. */
@ApiStatus.Internal
class SeSearchProjectItemsProvider(val project: Project, val key: String, val provider: SeItemsProvider)

/**
 * The providers of a popup that search the other [SeSearchProjects]: every search project but [popupProject].
 *
 * [SeProvidersHolder] creates the providers of [popupProject] when the popup opens. A provider of another project
 * comes with the first search that asks that project and that id, so the popup creates no provider that it does not
 * search. Each project gets its own legacy contributors, because a wrapped provider takes its scopes and its model
 * from the contributor of its project. The event of such a project holds only the project: the PSI and the editor of
 * [initEvent] belong to [popupProject].
 *
 * The holder owns this object and disposes it. A project that closes disposes its providers earlier.
 */
@ApiStatus.Internal
class SeSearchProjectProviders internal constructor(
  private val popupProject: Project,
  private val searchProjects: SeSearchProjects,
  private val initEvent: AnActionEvent,
  private val logLabel: String,
) : Disposable {
  private val lock = ReentrantLock()

  /** The other search projects that a search asked, guarded by [lock]. A project leaves when it closes. */
  private val members = HashMap<Project, Member>()

  /** Guarded by [lock]. */
  private var isDisposed = false

  /** Whether the popup searches several projects. Then each search of a project marks itself, see [SeSearchProjectContext]. */
  private val searchesSeveralProjects: Boolean = searchProjects.projects.distinct().size > 1

  /**
   * The source of the providers of [providerId] for the All tab or for the separate tab.
   * `null` for an id that searches [popupProject] only, see [SeSearchProjects.providerIds].
   */
  fun sourceOf(providerId: SeProviderId, isAllTab: Boolean): Source? =
    if (providerId in searchProjects.providerIds) Source(providerId, isAllTab) else null

  /** The providers of one id in the search projects. [SeLocalItemDataProvider] reads them. */
  inner class Source internal constructor(private val providerId: SeProviderId, private val isAllTab: Boolean) {
    /** Whether a search of one project marks itself with [SeSearchProjectContext]. */
    val marksSearchOfProject: Boolean get() = searchesSeveralProjects

    /**
     * The providers that a search with [filter] asks, in the order of [SeSearchProjects.projects]: [popupProvider] for
     * [popupProject], and the provider of each other open project that [filter] picks. A pick that names no open search
     * project picks each of them: the picked project closed after the filter took it. The first search of a project
     * creates its provider of this id only.
     */
    suspend fun providers(filter: SeFilterState, popupProvider: SeItemsProvider): List<SeSearchProjectItemsProvider> {
      val open = searchProjects.projects.distinct().filter { !it.isDisposed }
      val selectedKeys = SeSearchProjectsFilter.selectedKeys(filter)
      val picked = if (selectedKeys == null) open else open.filter { SeSearchProjectsFilter.keyOf(it) in selectedKeys }.ifEmpty { open }
      return picked.mapNotNull { project ->
        if (project === popupProject) SeSearchProjectItemsProvider(project, SeSearchProjectsFilter.keyOf(project), popupProvider)
        else memberOf(project)?.providerOf(providerId, isAllTab)
      }
    }

    /** The providers of the other projects that [providers] already created. A caller that handles a found item asks this one. */
    fun createdProviders(): List<SeSearchProjectItemsProvider> =
      lock.withLock { members.values.toList() }.mapNotNull { it.createdProviderOf(providerId, isAllTab) }
  }

  /** The member of [project], or `null` when this object or [project] went away. */
  private fun memberOf(project: Project): Member? {
    val member = lock.withLock {
      if (isDisposed) return null
      members[project]?.let { return it }
      Member(project).also { members[project] = it }
    }
    // A project that closed in the meantime takes no child, and its member goes away at once.
    if (!Disposer.tryRegister(project, member)) {
      Disposer.dispose(member)
      return null
    }
    return member
  }

  override fun dispose() {
    val toDispose = lock.withLock {
      isDisposed = true
      members.values.toList().also { members.clear() }
    }
    toDispose.forEach { Disposer.dispose(it) }
  }

  /** The providers and the legacy contributors of one other search project. */
  private inner class Member(val project: Project) : Disposable {
    private val event = initEvent.withDataContext(DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null })
    private val key = SeSearchProjectsFilter.keyOf(project)

    /** Serializes the creations of this member. */
    private val creation = Mutex()

    /** The providers of each id that a search asked, also the ids whose factory gave none. */
    private val providers = ConcurrentHashMap<SeProviderId, CreatedProviders>()

    /** Created with the first provider of a wrapped legacy contributor, under [creation]. */
    private var legacyContributors: SeLegacyContributors? = null

    /** What this member disposes, in the order of the creation, guarded by [lock]. */
    private val created = ArrayList<Disposable>()

    /** Guarded by [lock]. */
    private var isMemberDisposed = false

    suspend fun providerOf(providerId: SeProviderId, isAllTab: Boolean): SeSearchProjectItemsProvider? {
      val providers = providers[providerId] ?: creation.withLock {
        providers[providerId] ?: createProviders(providerId).also { providers[providerId] = it }
      }
      return providers.of(isAllTab)
    }

    fun createdProviderOf(providerId: SeProviderId, isAllTab: Boolean): SeSearchProjectItemsProvider? =
      if (project.isDisposed) null else providers[providerId]?.of(isAllTab)

    private suspend fun createProviders(providerId: SeProviderId): CreatedProviders {
      var allTab: SeSearchProjectItemsProvider? = null
      var separateTab: SeSearchProjectItemsProvider? = null
      val made = ArrayList<SeItemsProvider>()
      try {
        for (factory in SeItemsProviderFactory.EP_NAME.extensionList.filter { SeProviderId(it.id) == providerId }) {
          val contributors = if (factory is SeWrappedLegacyContributorItemsProviderFactory) legacyContributors() else NO_CONTRIBUTORS
          val (provider, separateTabProvider) =
            SeProvidersHolder.createProviders(factory, project, event.dataContext, contributors.allTab, contributors.separateTab, logLabel) { it() }
          provider?.let { made += it; allTab = SeSearchProjectItemsProvider(project, key, it) }
          separateTabProvider?.let { made += it; separateTab = SeSearchProjectItemsProvider(project, key, it) }
        }
      }
      catch (e: Throwable) {
        // A canceled search leaves no owner for the providers that it created. The next search creates them again.
        made.forEach { Disposer.dispose(it) }
        throw e
      }
      made.forEach(::own)
      return CreatedProviders(allTab, separateTab)
    }

    private suspend fun legacyContributors(): SeLegacyContributors {
      legacyContributors?.let { return it }
      val allTab = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()
      val separateTab = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()
      try {
        SeProvidersHolder.initializeLegacyContributors(event, project, allTab, separateTab)
      }
      catch (e: Throwable) {
        // A canceled search leaves no owner for the contributors that it created.
        (allTab.values + separateTab.values).distinct().forEach { Disposer.dispose(it) }
        throw e
      }
      // A legacy contributor of another id serves no provider of this project.
      val unused = (allTab.filterKeys { it !in searchProjects.providerIds }.values +
                    separateTab.filterKeys { it !in searchProjects.providerIds }.values).distinct()
      unused.forEach { Disposer.dispose(it) }
      allTab.keys.retainAll(searchProjects.providerIds)
      separateTab.keys.retainAll(searchProjects.providerIds)
      (allTab.values + separateTab.values).distinct().forEach(::own)
      return SeLegacyContributors(allTab, separateTab).also { legacyContributors = it }
    }

    /** Takes [disposable] into this member, or disposes it at once when the member went away during the creation. */
    private fun own(disposable: Disposable) {
      val isOwned = lock.withLock { if (!isMemberDisposed) created.add(disposable); !isMemberDisposed }
      if (!isOwned) Disposer.dispose(disposable)
    }

    override fun dispose() {
      val toDispose = lock.withLock {
        isMemberDisposed = true
        if (members[project] === this) members.remove(project)
        created.toList().also { created.clear() }
      }
      // A provider goes before the legacy contributor that it wraps.
      toDispose.asReversed().forEach { Disposer.dispose(it) }
    }

    override fun toString(): String = "Search Everywhere providers of ${project.name}"
  }

  private class CreatedProviders(val allTab: SeSearchProjectItemsProvider?, val separateTab: SeSearchProjectItemsProvider?) {
    /** The same rule as [SeProvidersHolder.get]. */
    fun of(isAllTab: Boolean): SeSearchProjectItemsProvider? = if (isAllTab) allTab else separateTab ?: allTab
  }

  private companion object {
    val NO_CONTRIBUTORS = SeLegacyContributors(emptyMap(), emptyMap())
  }
}
