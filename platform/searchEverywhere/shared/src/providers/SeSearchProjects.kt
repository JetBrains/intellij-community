// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Names the projects that one Search Everywhere popup searches.
 *
 * A popup belongs to one project, and each of its providers searches that project. A product whose window shows several
 * projects gives the other projects here. The popup then asks the providers of each project, see [SeSearchProjects].
 * Without an extension that answers, the popup searches its project only.
 */
@ApiStatus.Internal
interface SeSearchProjectsProvider {
  /** The search projects of a popup of [project], or `null` when the popup searches [project] only. */
  fun getSearchProjects(project: Project): SeSearchProjects?

  companion object {
    val EP_NAME: ExtensionPointName<SeSearchProjectsProvider> =
      ExtensionPointName("com.intellij.searchEverywhere.searchProjectsProvider")

    /** The answer of the first extension that has one for [project]. */
    fun find(project: Project): SeSearchProjects? = EP_NAME.computeSafeIfAny { it.getSearchProjects(project) }
  }
}

/**
 * The projects that a popup searches. The product owns the list of the projects, the pick of a search and the
 * providers of each project. The popup only asks them.
 *
 * A provider of an id in [providerIds] searches each search project. A provider of another id searches the project of
 * the popup only: an application provider, such as the actions, gives the same items for each project.
 */
@ApiStatus.Internal
interface SeSearchProjects {
  val providerIds: Set<SeProviderId>

  /**
   * The scope of a search with [filter] for a provider that reaches every search project in one run of the project of
   * the popup: the text search. Such a provider is not in [providerIds].
   * [everywhereScope] is the "All Places" scope of the project of the popup when [filter] asks for every place, else
   * `null`. The result then adds the places outside the search projects.
   * `null` when the product has no such scope. The provider then searches in its own scope.
   */
  fun scopeOf(filter: SeFilterState, everywhereScope: GlobalSearchScope?): GlobalSearchScope? = null

  /**
   * The holders of the search projects for one popup, which opens with [initEvent] and [session].
   * The providers holder of the popup disposes the result.
   */
  fun createHolders(initEvent: AnActionEvent, session: SeSession): SeSearchProjectHolders
}

/** The providers holders of the search projects of one popup. The product creates them and disposes them. */
@ApiStatus.Internal
interface SeSearchProjectHolders : Disposable {
  /** Whether the popup has several search projects. Then each search of a project marks itself, see [SeSearchProjectContext]. */
  val searchesSeveralProjects: Boolean

  /** The search projects that a search with [filter] asks, in the order of the product. */
  suspend fun searched(filter: SeFilterState): List<SeSearchProjectHolder>

  /** The search project of [key] when [searched] gave it already, see [SeSearchProjectHolder.key]. A found item names its project so. */
  fun created(key: String): SeSearchProjectHolder?
}

/** A search [project] and its [holder]. [holder] is `null` for the project of the popup, whose providers the popup has. */
@ApiStatus.Internal
class SeSearchProjectHolder(val project: Project, val holder: SeProvidersHolder?) {
  /** The value that stands for [project] in the data of an item, see `SeItemDataKeys.SEARCH_PROJECT`. */
  val key: String = keyOf(project)

  companion object {
    fun keyOf(project: Project): String = project.projectId().serializeToString()
  }
}

/**
 * Marks the search of one project that a popup runs in each of its [SeSearchProjects].
 *
 * The popup asks the providers of each search project, so the search of one project must not also search the other
 * projects. A provider that reaches several projects in one run reads [isSearchOf]. A search outside such a popup has
 * no mark: Go to Class of the platform, or a popup that searches one project.
 *
 * The mark holds the key of the project, not the project, so a computation that it reaches retains no project.
 */
@ApiStatus.Internal
class SeSearchProjectContext(private val projectKey: String) : AbstractCoroutineContextElement(Key), IntelliJContextElement {
  /** A child computation searches the same project, also one that runs on another thread. */
  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  companion object Key : CoroutineContext.Key<SeSearchProjectContext> {
    /** Whether the current thread runs the search of [project] that a popup runs in each of its search projects. */
    fun isSearchOf(project: Project): Boolean =
      currentThreadContext()[Key]?.projectKey == SeSearchProjectHolder.keyOf(project)
  }
}
