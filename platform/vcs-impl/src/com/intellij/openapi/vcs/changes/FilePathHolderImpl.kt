// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath

class FilePathHolderImpl(private val project: Project) : FilePathHolder {
  private val files = hashSetOf<FilePath>()

  override fun values(): Collection<FilePath> = files

  override fun cleanAll() = files.clear()
  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) = cleanScope(files, scope)

  override fun addFile(file: FilePath) {
    files.add(file)
  }

  fun removeFile(file: FilePath) {
    files.remove(file)
  }

  override fun copy(): FilePathHolderImpl =
    FilePathHolderImpl(project).also {
      it.files.addAll(files)
    }

  override fun containsFile(file: FilePath) = file in files

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FilePathHolderImpl

    return files == other.files
  }

  override fun hashCode(): Int = files.hashCode()

  companion object {
    internal fun cleanScope(files: MutableCollection<FilePath>, scope: VcsModifiableDirtyScope) {
      ProgressManager.checkCanceled()
      if (files.isEmpty()) return

      if (scope.recursivelyDirtyDirectories.size == 0) {
        // `files` set is case-sensitive depending on OS, `scope.dirtyFiles` set is always case-sensitive
        // `AbstractSet.removeAll()` chooses collection to iterate through depending on its size
        // so we explicitly iterate through `scope.dirtyFiles` here
        scope.dirtyFiles.forEach { files.remove(it) }
      }
      else {
        files.removeIf { scope.belongsTo(it) }
      }
    }
  }
}