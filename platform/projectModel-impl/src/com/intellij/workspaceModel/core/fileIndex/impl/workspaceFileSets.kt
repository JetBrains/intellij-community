// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

/**
 * Base interface for file sets stored in [WorkspaceFileIndexData].
 */
internal sealed interface StoredFileSet {
  val entityReference: EntityReference<WorkspaceEntity>
}

internal class WorkspaceFileSetImpl(override val root: VirtualFile,
                                    override val kind: WorkspaceFileKind,
                                    override val entityReference: EntityReference<WorkspaceEntity>,
                                    override val data: WorkspaceFileSetData)
  : WorkspaceFileSetWithCustomData<WorkspaceFileSetData>, StoredFileSet, WorkspaceFileInternalInfo {
  fun isUnloaded(project: Project): Boolean {
    return (data as? UnloadableFileSetData)?.isUnloaded(project) == true
  }
}

internal class MultipleWorkspaceFileSets(val fileSets: List<WorkspaceFileSetImpl>) : WorkspaceFileInternalInfo

internal object DummyWorkspaceFileSetData : WorkspaceFileSetData

internal object WorkspaceFileKindMask {
  const val CONTENT = 1
  const val EXTERNAL_BINARY = 2
  const val EXTERNAL_SOURCE = 4
  const val EXTERNAL = EXTERNAL_SOURCE or EXTERNAL_BINARY
  const val ALL = CONTENT or EXTERNAL
}

internal sealed interface ExcludedFileSet : StoredFileSet {
  class ByFileKind(@MagicConstant(flagsFromClass = WorkspaceFileKindMask::class) val mask: Int,
                   override val entityReference: EntityReference<WorkspaceEntity>) : ExcludedFileSet

  class ByPattern(val root: VirtualFile, patterns: List<String>,
                  override val entityReference: EntityReference<WorkspaceEntity>) : ExcludedFileSet {
    val table = FileTypeAssocTable<Boolean>()

    init {
      for (pattern in patterns) {
        table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), true)
      }
    }

    fun isExcluded(file: VirtualFile): Boolean {
      var current = file
      while (current != root) {
        if (table.findAssociatedFileType(current.nameSequence) != null) {
          return true
        }
        current = current.parent
      }
      return false
    }
  }

  class ByCondition(val root: VirtualFile, val condition: (VirtualFile) -> Boolean,
                    override val entityReference: EntityReference<WorkspaceEntity>) : ExcludedFileSet {
    fun isExcluded(file: VirtualFile): Boolean {
      var current = file
      while (current != root) {
        if (condition(current)) {
          return true
        }
        current = current.parent
      }

      return condition(root)
    }
  }
}

internal inline fun <K, V> MultiMap<K, V>.removeValueIf(key: K, crossinline valuePredicate: (V) -> Boolean) {
  val collection = get(key)
  collection.removeIf { valuePredicate(it) }
  if (collection.isEmpty()) {
    remove(key)
  }
}