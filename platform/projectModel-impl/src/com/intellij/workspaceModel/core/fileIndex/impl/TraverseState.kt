// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

/**
 * Describes how the workspace includes a file during a top-down traversal of a file tree.
 * Use [goDown] to get the state of a child file.
 *
 * The rules are the same as in [WorkspaceFileIndexEx.getFileInfo] with `honorExclusion = true`:
 * * the nearest file set of a kind includes a file, if no exclusion between them excludes the file;
 * * a nested file set can include files again, unless an unscoped exclusion excludes them;
 * * an ignored file is included only by the file sets registered at it.
 */
@ApiStatus.Internal
class TraverseState private constructor(
  /** The kinds of file sets which include the current file, as [WorkspaceFileKindMask] bits. */
  @VisibleForTesting
  @MagicConstant(flagsFromClass = WorkspaceFileKindMask::class)
  val mask: Int,
  /**
   * The kinds of recursive file sets which include the current file, as [WorkspaceFileKindMask] bits.
   * The children of the current file inherit these kinds.
   */
  @MagicConstant(flagsFromClass = WorkspaceFileKindMask::class)
  @VisibleForTesting
  val maskRecursive: Int,

  private val activeExcludes: List<ActiveExclude>,
) {
  private data class ActiveExclude(val rule: WorkspaceExcludeFileSet, val kinds: Int) {
    fun withKinds(kinds: Int) = if (kinds == this.kinds) this else ActiveExclude(rule, kinds)
  }

  /** The exclusions which can still exclude the descendants of the current file. */
  @VisibleForTesting val excludes: List<WorkspaceExcludeFileSet> = if (activeExcludes.isEmpty()) emptyList()
  else object : AbstractList<WorkspaceExcludeFileSet>() {
    override val size: Int
      get() = activeExcludes.size

    override fun get(index: Int): WorkspaceExcludeFileSet = activeExcludes[index].rule
  }

  /** `true` if a file set includes the current file. */
  val isInWorkspace: Boolean
    get() = mask != 0

  /** `true` if an indexable file set includes the current file. */
  val isIndexable: Boolean
    get() = mask and INDEXABLE_KINDS != 0

  /** `true` if an indexable recursive file set includes the current file. */
  val isIndexableRecursive: Boolean
    get() = maskRecursive and INDEXABLE_KINDS != 0

  /**
   * Returns the state of [file], a child of the current file.
   * [fileIndex] supplies the file sets for [file] by [url], the URL of [file].
   * Pass `null` as [url] if no [VirtualFileUrl] exists for [file]. Then no file sets are registered at [file].
   * [file] can be a transient file.
   * Returns `null` if an unscoped exclusion excludes [file] and all its descendants.
   */
  fun goDown(file: VirtualFile, url: VirtualFileUrl?, fileIndex: WorkspaceFileIndexEx): TraverseState? {
    return goDown(file, if (url != null) fileIndex.getFileSetsAt(url) else WorkspaceFileSets.EMPTY)
  }

  /** The same as [goDown], but uses [fileSetsAt] instead of the data registered at [file]. */
  internal fun goDown(file: VirtualFile, fileSetsAt: WorkspaceFileSets): TraverseState? {
    val inherited = if (FileTypeRegistry.getInstance().isFileIgnored(file)) 0 else maskRecursive
    val newMask = inherited or fileSetsAt.mask
    val newMaskRecursive = inherited or fileSetsAt.maskRecursive
    if (activeExcludes.isEmpty() && fileSetsAt.excludes.isEmpty()) {
      return if (hasSameMasks(newMask, newMaskRecursive)) this else TraverseState(newMask, newMaskRecursive, emptyList())
    }

    if (isExcludedByUnscoped(file, fileSetsAt)) {
      return null
    }

    val (finalMask, finalMaskRecursive) = applyExcludes(file, fileSetsAt, newMask, newMaskRecursive)
    val newExcludes = updateExcludes(finalMaskRecursive, fileSetsAt)
    return TraverseState(finalMask, finalMaskRecursive, newExcludes)
  }

  /**
   * Applies scoped exclusions to the given masks.
   * Returns a pair of (newMask, newMaskRecursive) with excluded kinds removed.
   */
  private fun applyExcludes(
    file: VirtualFile,
    fileSetsAt: WorkspaceFileSets,
    mask: Int,
    maskRecursive: Int,
  ): Pair<Int, Int> {
    var newMask = mask
    var newMaskRecursive = maskRecursive

    /** apply [exclude] to [newMask] and [newMaskRecursive] */
    fun applyScopedExclude(exclude: WorkspaceExcludeFileSet, kindsForFile: Int, kindsForChildren: Int) {
      if (exclude.isUnscoped() || (newMask and kindsForFile) or (newMaskRecursive and kindsForChildren) == 0) return
      val excludedKinds = exclude.excludedKinds(file)
      newMask = newMask and (excludedKinds and kindsForFile).inv()
      newMaskRecursive = newMaskRecursive and (excludedKinds and kindsForChildren).inv()
    }

    for ((exclude, kinds) in activeExcludes) {
      applyScopedExclude(exclude, kinds and fileSetsAt.mask.inv(), kinds and fileSetsAt.maskRecursive.inv())
    }
    for (exclude in fileSetsAt.excludes) {
      applyScopedExclude(exclude, WorkspaceFileKindMask.ALL, WorkspaceFileKindMask.ALL)
    }
    return Pair(newMask, newMaskRecursive)
  }

  private fun isExcludedByUnscoped(file: VirtualFile, fileSetsAt: WorkspaceFileSets): Boolean {
    return (excludes.asSequence() + fileSetsAt.excludes).any { exclude -> exclude.isUnscoped() && exclude.excludedKinds(file) != 0 }
  }

  /** update exclude kinds for excludes */
  private fun updateExcludes(newMaskRecursive: Int, fileSetsAt: WorkspaceFileSets): List<ActiveExclude> {
    // A scoped exclusion matters only for the kinds which the children inherit.
    // A nested file set of other kinds resets the exclusions for its kinds.
    val keptScopedKinds = newMaskRecursive and fileSetsAt.maskRecursive.inv()
    val newExcludes = ArrayList<ActiveExclude>(activeExcludes.size + fileSetsAt.excludes.size)
    for (exclude in activeExcludes) {
      val newExcludeKinds = if (exclude.rule.isUnscoped()) exclude.kinds else exclude.kinds and keptScopedKinds
      if (newExcludeKinds == 0) continue

      val newExclude = exclude.withKinds(newExcludeKinds)
      newExcludes.add(newExclude)
    }
    for (exclude in fileSetsAt.excludes) {
      val newKinds = if (exclude.isUnscoped()) WorkspaceFileKindMask.ALL else newMaskRecursive
      if (newKinds != 0) newExcludes.add(ActiveExclude(exclude, newKinds))
    }

    return newExcludes
  }

  private fun hasSameMasks(newMask: Int, newMaskRecursive: Int): Boolean = newMask == mask && newMaskRecursive == maskRecursive

  override fun toString(): String =
    "TraverseState{mask=$mask, maskRecursive=$maskRecursive, excludes=$excludes}"

  companion object {
    /**
     * Returns the top-down traverse state for [file] in [project].
     * Collects the included kinds and the exclusions from the ancestors of [file].
     *
     * @return TraverseState for file or `null` if file and all descendants are excluded
     *
     * @throws IllegalArgumentException if [file] is a [CacheAvoidingVirtualFile]
     */
    @JvmStatic
    fun getTraverseState(file: VirtualFile, project: Project): TraverseState? {
      require(file !is CacheAvoidingVirtualFile) { "$file must be cached in VFS" }
      val path = ArrayList<VirtualFile>()
      var current: VirtualFile? = file
      while (current != null) {
        path.add(current)
        // A thin-client file can return itself as its parent.
        current = current.parent?.takeIf { it != current }
      }

      val fileIndex = WorkspaceFileIndexEx.getInstance(project)
      val urlManager = project.workspaceModel.getVirtualFileUrlManager() as VirtualFileUrlManagerImpl
      val root = path.last()
      val rootUrl = root.url
      var url: VirtualFileUrl? = urlManager.findNode(rootUrl.removeSuffix("/"))
      var state: TraverseState? = emptyState().goDown(root, if (rootUrl.endsWith('/')) url?.findChild("") else url, fileIndex)
      for (child in path.asReversed().asSequence().drop(1)) {
        if (state == null) return@getTraverseState null
        url = url?.findChild(child.name)
        state = state.goDown(child, url, fileIndex)
      }
      return state
    }

    /** Returns the state of a file which no file set includes, and which no exclusion affects. */
    private fun emptyState(): TraverseState = TraverseState(0, 0, emptyList())
  }
}

private const val INDEXABLE_KINDS =
  WorkspaceFileKindMask.ALL and (WorkspaceFileKindMask.CONTENT_NON_INDEXABLE or WorkspaceFileKindMask.EXTERNAL_NON_INDEXABLE).inv()

/** An unscoped exclusion applies also inside a file set nested below its root. */
private fun WorkspaceExcludeFileSet.isUnscoped(): Boolean = this is WorkspaceExcludeFileSet.ByUnscopedCondition

/** Returns the kinds which this exclusion removes for [file], as [WorkspaceFileKindMask] bits. */
private fun WorkspaceExcludeFileSet.excludedKinds(file: VirtualFile): Int =
  WorkspaceFileKindMask.ALL and inPlaceComputeMasks(file, WorkspaceFileKindMask.ALL).inv()
