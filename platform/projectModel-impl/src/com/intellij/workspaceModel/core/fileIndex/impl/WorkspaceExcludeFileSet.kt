// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus

/**
 * An exclusion rule which a top-down traversal applies to files one by one.
 *
 * The masks use [WorkspaceFileKindMask] bits. They describe the kinds of file sets which include a file.
 */
@ApiStatus.Internal
sealed interface WorkspaceExcludeFileSet {
  /**
   * Returns [mask] without the kinds which this rule excludes for [file].
   *
   * [file] is the root of this rule or a file below it. The function checks only [file] itself.
   * The traversal applies the rule to the parents of [file] before, so the function ignores them.
   * [file] can be a transient file, so do not compare it with cached files.
   */
  @MagicConstant(flagsFromClass = WorkspaceFileKindMask::class)
  fun inPlaceComputeMasks(file: VirtualFile, @MagicConstant(flagsFromClass = WorkspaceFileKindMask::class) mask: Int): Int

  /**
   * Excludes the root and its descendants from the kinds selected by [mask].
   * A nested file set can include files again.
   */
  interface ByFileKind : WorkspaceExcludeFileSet {
    @get:MagicConstant(flagsFromClass = WorkspaceFileKindMask::class)
    val mask: Int

    override fun inPlaceComputeMasks(file: VirtualFile, mask: Int): Int = mask.unsetKinds(this.mask)
  }

  /**
   * Excludes the root and its descendants from all kinds.
   * With [directoryOnly], the rule excludes only directories.
   * The exclusion also applies inside a nested file set.
   */
  interface UnscopedRoot : WorkspaceExcludeFileSet {
    val directoryOnly: Boolean

    override fun inPlaceComputeMasks(file: VirtualFile, mask: Int): Int {
      return if (!directoryOnly || file.isDirectory) mask.unsetKinds(WorkspaceFileKindMask.ALL) else mask
    }
  }

  /**
   * Excludes the files whose names match the patterns.
   * A nested file set can include files again.
   */
  interface ByPattern : WorkspaceExcludeFileSet {
    fun matches(fileName: CharSequence): Boolean

    override fun inPlaceComputeMasks(file: VirtualFile, mask: Int): Int {
      return if (matches(file.nameSequence)) mask.unsetKinds(WorkspaceFileKindMask.ALL) else mask
    }
  }

  /**
   * Excludes the root and the files below it which satisfy [condition].
   * A nested file set can include files again.
   */
  interface ByCondition : WorkspaceExcludeFileSet {
    val condition: WorkspaceFileSetExclusionCondition

    override fun inPlaceComputeMasks(file: VirtualFile, mask: Int): Int {
      return if (condition.shouldExclude(file)) mask.unsetKinds(WorkspaceFileKindMask.ALL) else mask
    }
  }

  /**
   * Uses the same condition check as [ByCondition].
   * The exclusion also applies inside a nested file set.
   */
  interface ByUnscopedCondition : WorkspaceExcludeFileSet {
    val condition: WorkspaceFileSetExclusionCondition

    override fun inPlaceComputeMasks(file: VirtualFile, mask: Int): Int {
      return if (condition.shouldExclude(file)) mask.unsetKinds(WorkspaceFileKindMask.ALL) else mask
    }
  }
}

private fun Int.unsetKinds(kinds: Int): Int = this and kinds.inv()
