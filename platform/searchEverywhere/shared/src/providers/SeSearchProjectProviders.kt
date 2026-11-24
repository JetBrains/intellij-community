// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereMlContributorReplacement
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

/** The [provider] that searches [project], one of the [SeSearchProjects] of a popup. [key] stands for [project] in a filter state. */
@ApiStatus.Internal
class SeSearchProjectItemsProvider(val project: Project, val key: String, val provider: SeItemsProvider)

/**
 * The providers of a popup that search the other [SeSearchProjects]: every search project but [popupProject].
 *
 * [SeProvidersHolder] creates the providers of [popupProject] when the popup opens. The providers of the other projects
 * come with the first search that needs them, so they do not delay the popup. Each project gets its own legacy
 * contributors, because a wrapped provider takes its scopes and its model from the contributor of its project.
 * The event of such a project holds only the project: the PSI and the editor of [initEvent] belong to [popupProject].
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
  private val mutex = Mutex()

  @Volatile
  private var members: List<Member>? = null

  @Volatile
  private var isDisposed = false

  /** The key of [popupProject], or `null` when the popup does not search its own project. */
  val popupProjectKey: String? = SeSearchProjectsFilter.keyOf(popupProject).takeIf { popupProject in searchProjects.projects }

  /**
   * The source of the providers of [providerId] for the All tab or for the separate tab.
   * `null` for an id that searches [popupProject] only, see [SeSearchProjects.providerIds].
   */
  fun sourceOf(providerId: SeProviderId, isAllTab: Boolean): Source? =
    if (providerId in searchProjects.providerIds) Source(providerId, isAllTab) else null

  /** The providers of one id in the other search projects. [SeLocalItemDataProvider] reads them. */
  inner class Source internal constructor(private val providerId: SeProviderId, private val isAllTab: Boolean) {
    val popupProjectKey: String? get() = this@SeSearchProjectProviders.popupProjectKey

    /** The providers of the other search projects that are open. The first call creates them. */
    suspend fun providers(): List<SeSearchProjectItemsProvider> = providersOf(members())

    /** The providers that [providers] already created. A caller that handles a found item asks this one. */
    fun createdProviders(): List<SeSearchProjectItemsProvider> = providersOf(members.orEmpty())

    private fun providersOf(members: List<Member>): List<SeSearchProjectItemsProvider> =
      members.mapNotNull { member -> member.takeUnless { it.project.isDisposed }?.providerOf(providerId, isAllTab) }
  }

  private suspend fun members(): List<Member> {
    members?.let { return it }
    return mutex.withLock {
      members ?: createMembers().also { created ->
        members = created
        // The holder went away while the providers were on their way.
        if (isDisposed) created.forEach { Disposer.dispose(it.disposable) }
      }
    }
  }

  private suspend fun createMembers(): List<Member> {
    val factories = SeItemsProviderFactory.EP_NAME.extensionList.filter { SeProviderId(it.id) in searchProjects.providerIds }
    try {
      return searchProjects.projects.filter { it !== popupProject && !it.isDisposed }.distinct().map { createMember(it, factories) }
    }
    finally {
      // The creation of the legacy contributors of another project saved the event of that project.
      SearchEverywhereMlContributorReplacement.saveInitEvent(initEvent)
    }
  }

  private suspend fun createMember(project: Project, factories: List<SeItemsProviderFactory>): Member {
    val event = initEvent.withDataContext(DataContext { dataId -> if (CommonDataKeys.PROJECT.`is`(dataId)) project else null })
    val legacyContributors = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()
    val separateTabLegacyContributors = mutableMapOf<SeProviderId, SearchEverywhereContributor<Any>>()
    if (factories.any { it is SeWrappedLegacyContributorItemsProviderFactory }) {
      SeProvidersHolder.initializeLegacyContributors(event, project, legacyContributors, separateTabLegacyContributors)
      // A legacy contributor of another id serves no provider of this project.
      val unused = (legacyContributors.filterKeys { it !in searchProjects.providerIds }.values +
                    separateTabLegacyContributors.filterKeys { it !in searchProjects.providerIds }.values).distinct()
      unused.forEach { Disposer.dispose(it) }
      legacyContributors.keys.retainAll(searchProjects.providerIds)
      separateTabLegacyContributors.keys.retainAll(searchProjects.providerIds)
    }

    val key = SeSearchProjectsFilter.keyOf(project)
    val allTabProviders = mutableMapOf<SeProviderId, SeSearchProjectItemsProvider>()
    val separateTabProviders = mutableMapOf<SeProviderId, SeSearchProjectItemsProvider>()
    for (factory in factories) {
      val (provider, separateTabProvider) =
        SeProvidersHolder.createProviders(factory, project, event.dataContext, legacyContributors, separateTabLegacyContributors, logLabel) { it() }
      provider?.let { allTabProviders[SeProviderId(it.id)] = SeSearchProjectItemsProvider(project, key, it) }
      separateTabProvider?.let { separateTabProviders[SeProviderId(it.id)] = SeSearchProjectItemsProvider(project, key, it) }
    }

    val disposable = Disposer.newDisposable("Search Everywhere providers of ${project.name}")
    Disposer.register(disposable) {
      (allTabProviders.values + separateTabProviders.values).forEach { Disposer.dispose(it.provider) }
      (legacyContributors.values + separateTabLegacyContributors.values).distinct().forEach { Disposer.dispose(it) }
    }
    // A project that closed in the meantime takes no child, and its providers go away at once.
    if (!Disposer.tryRegister(project, disposable)) Disposer.dispose(disposable)
    return Member(project, allTabProviders, separateTabProviders, disposable)
  }

  override fun dispose() {
    isDisposed = true
    members?.forEach { Disposer.dispose(it.disposable) }
  }

  private class Member(
    val project: Project,
    private val allTabProviders: Map<SeProviderId, SeSearchProjectItemsProvider>,
    private val separateTabProviders: Map<SeProviderId, SeSearchProjectItemsProvider>,
    val disposable: Disposable,
  ) {
    /** The same rule as [SeProvidersHolder.get]. */
    fun providerOf(providerId: SeProviderId, isAllTab: Boolean): SeSearchProjectItemsProvider? =
      if (isAllTab) allTabProviders[providerId]
      else separateTabProviders[providerId] ?: allTabProviders[providerId]
  }
}
