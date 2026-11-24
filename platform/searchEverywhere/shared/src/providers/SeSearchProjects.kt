// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeProviderId
import org.jetbrains.annotations.ApiStatus

/**
 * Names the projects that one Search Everywhere popup searches.
 *
 * A popup belongs to one project, and each of its providers searches that project. A product whose window shows several
 * projects gives the other projects here. The popup then asks the providers of each project, see [SeSearchProjects].
 * Without an extension that answers, the popup searches its project only.
 */
@ApiStatus.Internal
interface SeSearchProjectsProvider {
  /** The projects that a popup of [project] searches, or `null` when the popup searches [project] only. */
  fun getSearchProjects(project: Project): SeSearchProjects?

  companion object {
    val EP_NAME: ExtensionPointName<SeSearchProjectsProvider> =
      ExtensionPointName("com.intellij.searchEverywhere.searchProjectsProvider")

    /** The answer of the first extension that has one for [project]. */
    fun find(project: Project): SeSearchProjects? = EP_NAME.computeSafeIfAny { it.getSearchProjects(project) }
  }
}

/**
 * The [projects] that a popup searches, in the order of the product, and the [providerIds] that search each of them.
 *
 * The popup creates one provider of each id in [providerIds] for each project. A provider of another id searches the
 * project of the popup only: an application provider, such as the actions, gives the same items for each project.
 * The project of the popup is searched only when [projects] holds it.
 */
@ApiStatus.Internal
class SeSearchProjects(val projects: List<Project>, val providerIds: Set<SeProviderId>)

/**
 * The part of a filter state that picks some of the [SeSearchProjects] of a popup.
 *
 * A state without the key picks every search project. A filter editor of the product adds the key.
 */
@ApiStatus.Internal
object SeSearchProjectsFilter {
  const val KEY: String = "SEARCH_PROJECTS"

  /** The value that stands for [project] in the filter state and in the data of an item. */
  fun keyOf(project: Project): String = project.projectId().serializeToString()

  /** The keys of the picked projects, or `null` when [state] picks every search project. */
  fun selectedKeys(state: SeFilterState): Set<String>? = state.get(KEY)?.toSet()

  /** [state] with a pick of [projects], or without a pick when [projects] is `null`. */
  fun withSelected(state: SeFilterState, projects: List<Project>?): SeFilterState {
    val map = state.keys.associateWithTo(LinkedHashMap()) { state.get(it).orEmpty() }
    if (projects == null) map.remove(KEY) else map[KEY] = projects.map(::keyOf)
    return SeFilterState.Data(map)
  }
}
