// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.ex

import com.intellij.ide.ui.search.getConfigurablesSafely
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.newEditor.SettingsDialogPerformanceTracker
import com.intellij.openapi.options.newEditor.SettingsFrameProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.impl.IdeFrameGuests
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The root of the Settings tree of a window that configures several projects, see [SettingsFrameProjects].
 *
 * The tree holds each application-level page once, and one [FrameProjectConfigurable] for the project-level pages with one id
 * of all the projects. So a change of an application-level page has one state, and each project keeps its own changes.
 * A project-level page that only some of the projects have is in the tree too. A project-level page that no project has
 * is in the tree only while it has a child page, for example an application-level page that a plugin puts below it.
 *
 * When no project of the selector is open, the tree is the tree of the frame owner. A frame owner that suppresses its own
 * settings gets its tree without its project-level pages then, see [SettingsFrameProjects.suppressesOwnSettings].
 *
 * A change of an extension point of the application or of a project of the selector builds the tree again, as for the tree
 * of one project. The new tree holds new pages.
 * A frame guest that opens while the window is open joins the tree, see [join]. A window that opened with the tree of one
 * project has no such root, and a frame guest that opens later gets no page in it.
 */
@ApiStatus.Internal
class FrameProjectsConfigurableGroup private constructor(
  val projects: SettingsFrameProjects,
  private val tree: MergedTree,
  private val group: EpBasedConfigurableGroup,
) : MutableConfigurableGroup {
  /**
   * The parent of the extension point listeners of each project of the selector, see [EpBasedConfigurableGroup.listenToProject].
   * It goes when its project closes. The map is empty until the first [addListener], and it changes on the EDT.
   */
  private val projectListeners = HashMap<Project, Disposable>()
  private var isListening = false

  override fun getDisplayName(): @NlsContexts.ConfigurableName String = group.displayName

  override fun getConfigurables(): Array<Configurable> = group.configurables

  @RequiresEdt
  override fun addListener(listener: MutableConfigurableGroup.Listener) {
    group.addListener(listener)
    if (!isListening) {
      isListening = true
      for (project in projects.projects) {
        listenToProject(project)
      }
    }
  }

  private fun listenToProject(project: Project) {
    if (project.isDisposed || project in projectListeners) {
      return
    }
    val disposable = Disposer.newDisposable(this, "Settings pages of a frame project")
    projectListeners.put(project, disposable)
    group.listenToProject(project, disposable)
  }

  override fun dispose() {
    Disposer.dispose(group)
  }

  /**
   * Drops the pages of [project] from the tree, because the project closes, see [FrameProjectConfigurable.forget].
   * A page that no project has after the call leaves the tree, unless it has a child page. A group that has no page after
   * the call leaves the tree too. Returns the nodes that left the tree. When it is not empty, the tree view must show the
   * tree again.
   */
  @RequiresEdt
  fun forget(project: Project): Set<Configurable> {
    projectListeners.remove(project)?.let { Disposer.dispose(it) }
    return tree.forget(project)
  }

  /** `true` when a page of [project] has a change that waits for Apply. */
  fun isModified(project: Project): Boolean = tree.pages.any { it.isModified(project) }

  /** Applies the changes of the pages of [project]. A failed page makes [project] the selected project. */
  @Throws(ConfigurationException::class)
  fun apply(project: Project) {
    for (page in tree.pages) {
      page.apply(project)
    }
  }

  /** Drops the changes of the pages of [project], and shows the saved values again. */
  fun cancel(project: Project) {
    for (page in tree.pages) {
      page.cancel(project)
    }
  }

  /** The pages of the tree that have a page of [project] with a change that waits for Apply. */
  fun modifiedPagesOf(project: Project): List<FrameProjectConfigurable> = tree.pages.filter { it.isModified(project) }

  /**
   * Adds the pages of [project] to the tree, because [project] joins the frame. [memberTree] is the tree of [project], see
   * [buildMemberTree]. A page with an id that the tree has already gets the page of [project], so the pending changes of the
   * other projects stay. Returns `true` when the tree got a new node, so that the tree view must show the tree again.
   * Returns `false` and adds nothing when the tree is the tree of the frame owner alone, which the build makes when no project
   * of the selector is open. The tree of a frame owner that suppresses its own settings takes a join.
   */
  @RequiresEdt
  fun join(project: Project, memberTree: SortedConfigurableGroup): Boolean {
    if (!tree.canJoin()) {
      return false
    }
    val hasNewNodes = tree.join(project, memberTree)
    if (isListening) {
      listenToProject(project)
    }
    return hasNewNodes
  }

  /** `true` when [join] can add the pages of a project to the tree. */
  fun canJoin(): Boolean = tree.canJoin()

  companion object {
    /** Builds the tree for [projects]. This is as heavy as the build of the tree of each project, so it must not run on the EDT. */
    @JvmStatic
    fun create(projects: SettingsFrameProjects): FrameProjectsConfigurableGroup {
      val tree = MergedTree(projects)
      val group = SettingsDialogPerformanceTracker.measureConfigurableTreeBuild {
        EpBasedConfigurableGroup(projects.frameOwner) { tree.build() }
      }
      return FrameProjectsConfigurableGroup(projects, tree, group)
    }

    /** Builds the tree of [project] for [join]. This is heavy, so it must not run on the EDT. */
    @JvmStatic
    fun buildMemberTree(project: Project): SortedConfigurableGroup? {
      // no tree gets a project: the name of a group must not hold the name of one of the projects
      return ConfigurableExtensionPointUtil.buildGroupTree(ConfigurableExtensionPointUtil.getConfigurables(project, true), null)
    }

    /**
     * The project that owns the Settings window of [project]: the owner of the frame that shows [project].
     * So a frame has one Settings window, whatever project of the frame opens it.
     */
    @JvmStatic
    fun settingsWindowProject(project: Project): Project {
      return if (project.isDefault || project.isDisposed) project else IdeFrameGuests.frameOwnerOf(project)
    }

    /**
     * Builds the tree of the Settings window of [project]. The window belongs to [settingsWindowProject].
     * A frame that shows several projects gets the tree of all of them. This is heavy, so it must not run on the EDT.
     */
    @JvmStatic
    fun buildSettingsWindowGroups(project: Project?): List<ConfigurableGroup> {
      val windowProject = project?.let(::settingsWindowProject)
      val frameProjects = windowProject?.let { SettingsFrameProjects.create(it) }
      if (frameProjects != null) {
        return listOf(create(frameProjects))
      }
      return listOf(ConfigurableExtensionPointUtil.getConfigurableGroup(windowProject, /* withIdeSettings = */ true))
    }
  }
}

/**
 * The merged tree of [projects] and its structure before the group compaction, see [ConfigurableExtensionPointUtil.compactGroups].
 * The compaction replaces a group of one page with that page, so a join and a removal change the stored structure and
 * compact it again.
 */
private class MergedTree(private val projects: SettingsFrameProjects) {
  /** The root of the merged tree, or `null` for the tree of the frame owner alone, which takes no join. */
  @Volatile
  private var root: SortedConfigurableGroup? = null

  /** The pages of several projects of the tree. The tree holds no other project-level page. */
  @Volatile
  var pages: List<FrameProjectConfigurable> = emptyList()
    private set

  /** The children of each group of the tree before the compaction. */
  private val children = IdentityHashMap<SortedConfigurableGroup, List<Configurable>>()

  /** The weight of each child of a group before the compaction. The compaction gives a page the weight of the group it replaces. */
  private val weights = IdentityHashMap<Configurable, Int>()

  fun build(): ConfigurableGroup {
    root = null
    pages = emptyList()
    val frameOwner = projects.frameOwner
    val openProjects = projects.projects.filter { !it.isDisposed }
    // a frame owner that suppresses its own settings gives its tree without its project-level pages. So the tree is the
    // same as after the close of the last project, and a project that joins finds the parent of each of its pages
    val isOwnerTreeOnly = openProjects.isEmpty() && SettingsFrameProjects.suppressesOwnSettings(frameOwner)
    val roots = (if (isOwnerTreeOnly) listOf(frameOwner) else openProjects).mapNotNull { project ->
      FrameProjectsConfigurableGroup.buildMemberTree(project)?.let { project to it }
    }
    if (roots.isEmpty()) {
      // no project of the selector is open: the tree of the frame owner is the one tree that is left
      return checkNotNull(ConfigurableExtensionPointUtil.getConfigurableGroup(ConfigurableExtensionPointUtil.getConfigurables(frameOwner, true), frameOwner))
    }
    val merger = FrameProjectTreeMerger(projects)
    val root = merger.merge(roots, isParentProjectLevel = false) as SortedConfigurableGroup
    var pages = merger.newPages.toList()
    if (isOwnerTreeOnly) {
      for (page in pages) {
        page.forget(frameOwner)
      }
      val removed = removeEmptyNodes(root)
      pages = pages.filter { it !in removed }
    }
    compact(root)
    this.pages = pages
    this.root = root
    return root
  }

  fun canJoin(): Boolean = root != null

  fun join(project: Project, memberTree: SortedConfigurableGroup): Boolean {
    val root = root ?: return false
    restore()
    val merger = FrameProjectTreeMerger(projects)
    merger.merge(listOf(null to root, project to memberTree), isParentProjectLevel = false)
    compact(root)
    pages += merger.newPages
    return merger.hasNewNodes
  }

  fun forget(project: Project): Set<Configurable> {
    val root = root ?: return emptySet()
    val pagesOfProject = pages.filter { it.pageOf(project) != null }
    for (page in pagesOfProject) {
      page.forget(project)
    }
    if (pagesOfProject.none { it.declaration == null }) {
      return emptySet()
    }
    restore()
    val removed = removeEmptyNodes(root)
    compact(root)
    pages = pages.filter { it !in removed }
    return removed
  }

  /**
   * Removes each page of several projects that no project has and that has no child page, and each group that has no page
   * then. [root] has the structure before the compaction. Returns the nodes that left the tree.
   */
  private fun removeEmptyNodes(root: SortedConfigurableGroup): Set<Configurable> {
    val removed = Collections.newSetFromMap(IdentityHashMap<Configurable, Boolean>())
    removeEmptyChildren(root, removed)
    return removed
  }

  /** Returns `false` when [group] has no child after the call. */
  private fun removeEmptyChildren(group: SortedConfigurableGroup, removed: MutableSet<Configurable>): Boolean {
    val list = group.myList ?: return true
    val kept = list.filter { keeps(it, removed) }
    if (kept.size != list.size) {
      group.setChildren(kept)
    }
    return kept.isNotEmpty()
  }

  /** `false` when [node] leaves the tree. The call removes the empty nodes below [node] first. */
  private fun keeps(node: Configurable, removed: MutableSet<Configurable>): Boolean {
    val keeps = when (node) {
      is SortedConfigurableGroup -> removeEmptyChildren(node, removed)
      is FrameProjectCompositeConfigurable -> {
        node.children = node.children.filter { keeps(it, removed) }.toTypedArray()
        node.declaration != null || node.children.isNotEmpty()
      }
      is FrameProjectConfigurable -> node.declaration != null
      is ConfigurableWrapper.CompositeWrapper -> {
        // the merge built the children of each application-level page, so this call creates no page
        val children = node.configurables
        val kept = children.filter { keeps(it, removed) }
        if (kept.size != children.size) {
          node.setConfigurables(kept.toTypedArray())
        }
        true
      }
      else -> true
    }
    if (!keeps) {
      removed.add(node)
    }
    return keeps
  }

  private fun compact(root: SortedConfigurableGroup) {
    children.clear()
    weights.clear()
    record(root)
    ConfigurableExtensionPointUtil.compactGroups(root)
  }

  private fun record(group: SortedConfigurableGroup) {
    val list = group.myList ?: return
    children.put(group, ArrayList(list))
    for (child in list) {
      when (child) {
        is SortedConfigurableGroup -> {
          weights.put(child, child.myWeight)
          record(child)
        }
        is ConfigurableWrapper -> weights.put(child, child.myWeight)
      }
    }
  }

  /** Gives each group its children and each child its weight from before the compaction. */
  private fun restore() {
    for ((group, list) in children) {
      group.setChildren(list)
    }
    for ((child, weight) in weights) {
      when (child) {
        is SortedConfigurableGroup -> child.myWeight = weight
        is ConfigurableWrapper -> child.myWeight = weight
      }
    }
  }
}

/**
 * Merges the Settings trees of several projects into the tree of the first project.
 *
 * The pages of the trees with one id under one parent become one page. An application-level page is the page of the first tree
 * that has it, with the children of all the trees. The project-level pages become one [FrameProjectConfigurable].
 *
 * A node of a pair with no project is a node of a merged tree. It goes first, and the nodes of the other projects merge into it.
 * A non-composite [FrameProjectConfigurable] takes no child pages from a project that joins.
 */
private class FrameProjectTreeMerger(private val projects: SettingsFrameProjects) {
  /** `true` when a node of a project came under a node of the merged tree that had no node with its id. */
  var hasNewNodes: Boolean = false
    private set

  /** The pages of several projects that this merger created. */
  val newPages: MutableList<FrameProjectConfigurable> = ArrayList()

  /** [nodes] holds the pages with one id under one parent: one page for each project that has it, in the order of the projects. */
  fun merge(nodes: List<Pair<Project?, Configurable>>, isParentProjectLevel: Boolean): Configurable {
    val first = nodes.first().second
    if (isProjectLevel(first, isParentProjectLevel)) {
      return mergeProjectLevel(nodes)
    }
    when (first) {
      is SortedConfigurableGroup -> {
        val children = nodes.map { (project, node) -> project to ((node as? SortedConfigurableGroup)?.myList ?: emptyList()) }
        if (first.myList != null) {
          first.setChildren(mergeChildren(children, isParentProjectLevel = false))
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

  private fun mergeProjectLevel(nodes: List<Pair<Project?, Configurable>>): Configurable {
    val (firstProject, first) = nodes.first()
    if (firstProject == null) {
      return joinProjectLevel(first, nodes.drop(1))
    }
    val delegates = LinkedHashMap<Project, Configurable>()
    for ((project, node) in nodes) {
      delegates.putIfAbsent(project!!, node)
    }
    val id = ConfigurableVisitor.getId(first)
    val page = if (nodes.none { it.second is Configurable.Composite }) {
      FrameProjectConfigurable(projects, id, delegates)
    }
    else {
      FrameProjectCompositeConfigurable(projects, id, delegates, mergeChildren(childrenOf(nodes), isParentProjectLevel = true).toTypedArray())
    }
    newPages.add(page)
    return page
  }

  /** Adds the pages of the projects of [joining] to the page [merged] of a merged tree. */
  private fun joinProjectLevel(merged: Configurable, joining: List<Pair<Project?, Configurable>>): Configurable {
    if (merged !is FrameProjectConfigurable) {
      return merged
    }
    for ((project, node) in joining) {
      merged.addDelegate(project!!, node)
    }
    if (merged is FrameProjectCompositeConfigurable) {
      val children = listOf(null to merged.children.asList()) + childrenOf(joining)
      merged.children = mergeChildren(children, isParentProjectLevel = true).toTypedArray()
    }
    return merged
  }

  private fun childrenOf(nodes: List<Pair<Project?, Configurable>>): List<Pair<Project?, List<Configurable>>> {
    return nodes.map { (project, node) -> project to ((node as? Configurable.Composite)?.getConfigurablesSafely()?.asList() ?: emptyList()) }
  }

  /** [children] holds the children of one page in each project. The answer keeps the order of the first project. */
  private fun mergeChildren(children: List<Pair<Project?, List<Configurable>>>, isParentProjectLevel: Boolean): MutableList<Configurable> {
    val nodesByKey = LinkedHashMap<String, MutableList<Pair<Project?, Configurable>>>()
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
    if (children.any { it.first == null } && nodesByKey.values.any { nodes -> nodes.none { it.first == null } }) {
      hasNewNodes = true
    }
    return nodesByKey.values.mapTo(ArrayList()) { merge(it, isParentProjectLevel) }
  }

  /** A page without a declaration, which a parent page builds itself, is of the level of its parent, as in `SettingsTreeView`. */
  private fun isProjectLevel(configurable: Configurable, isParentProjectLevel: Boolean): Boolean {
    return when (configurable) {
      is FrameProjectConfigurable -> true
      is ConfigurableWrapper -> configurable.project != null
      is SortedConfigurableGroup -> false
      else -> isParentProjectLevel
    }
  }
}
