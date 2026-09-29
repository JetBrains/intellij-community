// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.util.SmartList
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus

/**
 * Describes the file sets and the exclusions registered exactly at one file.
 * [WorkspaceFileIndexEx.getFileSetsAt] returns it.
 */
@ApiStatus.Internal
data class WorkspaceFileSets(
  /** The kinds of all file sets registered at the file, as [WorkspaceFileKindMask] bits. */
  @MagicConstant(flagsFromClass = WorkspaceFileKindMask::class)
  val mask: Int,
  /** The kinds of the recursive file sets registered at the file, as [WorkspaceFileKindMask] bits. */
  @MagicConstant(flagsFromClass = WorkspaceFileKindMask::class)
  val maskRecursive: Int,
  /** The exclusions registered at the file. */
  val excludes: List<WorkspaceExcludeFileSet>,
) {
  companion object {
    @JvmField
    val EMPTY: WorkspaceFileSets = WorkspaceFileSets(0, 0, emptyList())
  }
}

internal fun StoredFileSetCollection.toFileSetsAt(): WorkspaceFileSets {
  var mask = 0
  var maskRecursive = 0
  val excludes = SmartList<WorkspaceExcludeFileSet>()
  forEach { fileSet ->
    when (fileSet) {
      is WorkspaceFileSetImpl -> {
        val kindMask = fileSet.kind.toMask()
        mask = mask or kindMask
        if (fileSet.recursive) {
          maskRecursive = maskRecursive or kindMask
        }
      }
      is ExcludedFileSet -> excludes.add(fileSet)
    }
  }
  return WorkspaceFileSets(mask, maskRecursive, excludes)
}

internal fun Collection<NonExistingFileSetData>.toFileSetsAt(): WorkspaceFileSets {
  if (isEmpty()) return WorkspaceFileSets.EMPTY
  var mask = 0
  var maskRecursive = 0
  val excludes = SmartList<WorkspaceExcludeFileSet>()
  for (data in this) {
    when (data) {
      is NonExistingWorkspaceFileSet -> {
        val kindMask = data.kind.toMask()
        mask = mask or kindMask
        if (data.recursive) {
          maskRecursive = maskRecursive or kindMask
        }
      }
      is NonExistingWorkspaceExclude -> excludes.add(data)
    }
  }
  return WorkspaceFileSets(mask, maskRecursive, excludes)
}
