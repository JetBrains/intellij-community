// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.ex

import com.intellij.ide.ui.search.getConfigurablesSafely
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.newEditor.SettingsDialogPerformanceTracker
import com.intellij.openapi.options.newEditor.SettingsFrameProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus

/**
 * The root of the Settings tree of a window that configures several projects, see [SettingsFrameProjects].
 *
 * The tree holds each application-level page once, and one [FrameProjectConfigurable] for the project-level pages with one id
 * of all the projects. So a change of an application-level page has one state, and each project keeps its own changes.
 * A project-level page that only some of the projects have is in the tree too.
 *
 * A change of an extension point builds the tree again, as for the tree of one project. The new tree holds new pages.
 * The tree lists the projects of [projects] at the time of the build: a frame guest that opens later gets no page.
 */
@ApiStatus.Internal
class FrameProjectsConfigurableGroup private constructor(
  val projects: SettingsFrameProjects,
  private val group: MutableConfigurableGroup,
) : MutableConfigurableGroup {
  override fun getDisplayName(): @NlsContexts.ConfigurableName String = group.displayName

  override fun getConfigurables(): Array<Configurable> = group.configurables

  override fun addListener(listener: MutableConfigurableGroup.Listener) {
    group.addListener(listener)
  }

  override fun dispose() {
    Disposer.dispose(group)
  }

  /** Drops the pages of [project] from the tree, because the project closes, see [FrameProjectConfigurable.forget]. */
  fun forget(project: Project) {
    ConfigurableVisitor.collect({ (it as? FrameProjectConfigurable)?.forget(project) }, configurables)
  }

  companion object {
    /** Builds the tree for [projects]. This is as heavy as the build of the tree of each project, so it must not run on the EDT. */
    @JvmStatic
    fun create(projects: SettingsFrameProjects): FrameProjectsConfigurableGroup {
      val group = SettingsDialogPerformanceTracker.measureConfigurableTreeBuild {
        EpBasedConfigurableGroup(projects.frameOwner) { buildMergedTree(projects) }
      }
      return FrameProjectsConfigurableGroup(projects, group)
    }
  }
}

private fun buildMergedTree(projects: SettingsFrameProjects): ConfigurableGroup {
  // no tree gets a project: the name of a group must not hold the name of one of the projects
  val roots = projects.projects.filter { !it.isDisposed }.mapNotNull { project ->
    val configurables = ConfigurableExtensionPointUtil.getConfigurables(project, /* withIdeSettings = */ true)
    ConfigurableExtensionPointUtil.buildGroupTree(configurables, null)?.let { project to it }
  }
  if (roots.isEmpty()) {
    // every project of the selector closed: the tree of the frame owner is the one tree that is left
    val frameOwner = projects.frameOwner
    return checkNotNull(ConfigurableExtensionPointUtil.getConfigurableGroup(ConfigurableExtensionPointUtil.getConfigurables(frameOwner, true), frameOwner))
  }
  val root = FrameProjectTreeMerger(projects).merge(roots, isParentProjectLevel = false) as SortedConfigurableGroup
  ConfigurableExtensionPointUtil.compactGroups(root)
  return root
}

/**
 * Merges the Settings trees of several projects into the tree of the first project.
 *
 * The pages of the trees with one id under one parent become one page. An application-level page is the page of the first tree
 * that has it, with the children of all the trees. The project-level pages become one [FrameProjectConfigurable].
 */
private class FrameProjectTreeMerger(private val projects: SettingsFrameProjects) {
  /** [nodes] holds the pages with one id under one parent: one page for each project that has it, in the order of the projects. */
  fun merge(nodes: List<Pair<Project, Configurable>>, isParentProjectLevel: Boolean): Configurable {
    val first = nodes.first().second
    if (isProjectLevel(first, isParentProjectLevel)) {
      return mergeProjectLevel(nodes)
    }
    when (first) {
      is SortedConfigurableGroup -> {
        val children = nodes.map { (project, node) -> project to ((node as? SortedConfigurableGroup)?.myList ?: emptyList()) }
        if (first.myList != null) {
          first.myList = mergeChildren(children, isParentProjectLevel = false)
        }
      }
      is ConfigurableWrapper.CompositeWrapper -> {
        // the first tree gives the children that the extension declares, and they are application-level in every tree;
        // only the children that the grouping added can be project-level, so each other tree gives only those
        val children = nodes.mapIndexed { index, (project, node) ->
          project to if (index == 0) first.configurables.asList() else (node as? ConfigurableWrapper.CompositeWrapper)?.addedConfigurables?.asList() ?: emptyList()
        }
        first.setConfigurables(mergeChildren(children, isParentProjectLevel = false).toTypedArray())
      }
    }
    return first
  }

  private fun mergeProjectLevel(nodes: List<Pair<Project, Configurable>>): Configurable {
    val delegates = LinkedHashMap<Project, Configurable>()
    for ((project, node) in nodes) {
      delegates.putIfAbsent(project, node)
    }
    val id = ConfigurableVisitor.getId(nodes.first().second)
    if (nodes.none { it.second is Configurable.Composite }) {
      return FrameProjectConfigurable(projects, id, delegates)
    }
    val children = nodes.map { (project, node) ->
      project to ((node as? Configurable.Composite)?.getConfigurablesSafely()?.asList() ?: emptyList())
    }
    return FrameProjectCompositeConfigurable(projects, id, delegates, mergeChildren(children, isParentProjectLevel = true).toTypedArray())
  }

  /** [children] holds the children of one page in each project. The answer keeps the order of the first project. */
  private fun mergeChildren(children: List<Pair<Project, List<Configurable>>>, isParentProjectLevel: Boolean): MutableList<Configurable> {
    val nodesByKey = LinkedHashMap<String, MutableList<Pair<Project, Configurable>>>()
    for ((project, configurables) in children) {
      val counts = HashMap<String, Int>()
      for (configurable in configurables) {
        val id = ConfigurableVisitor.getId(configurable)
        val count = counts.merge(id, 1, Int::plus)!!
        // a parent can hold two pages with one id: the second page stays a page of its own
        val key = if (count == 1) id else "$id#$count"
        nodesByKey.getOrPut(key) { ArrayList() }.add(project to configurable)
      }
    }
    return nodesByKey.values.mapTo(ArrayList()) { merge(it, isParentProjectLevel) }
  }

  /** A page without a declaration, which a parent page builds itself, is of the level of its parent, as in `SettingsTreeView`. */
  private fun isProjectLevel(configurable: Configurable, isParentProjectLevel: Boolean): Boolean {
    return when (configurable) {
      is ConfigurableWrapper -> configurable.project != null
      is SortedConfigurableGroup -> false
      else -> isParentProjectLevel
    }
  }
}
