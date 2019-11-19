// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import java.util.*

class FilePathHolder(private val project: Project) : FileHolder {
  private val files = HashSet<FilePath>()

  // todo track number of copies made
  fun getFiles() = files.toList()

  override fun cleanAll() {
    files.clear()
  }

  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) {
    cleanScope(files, scope)
  }

  fun addFile(file: FilePath) {
    files.add(file)
  }

  fun removeFile(file: FilePath) {
    files.remove(file)
  }

  override fun copy(): FilePathHolder {
    val copyHolder = FilePathHolder(project)
    copyHolder.files.addAll(files)
    return copyHolder
  }

  fun containsFile(file: FilePath) = files.contains(file)

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val that = (o as? FilePathHolder?) ?: return false

    return files == that.files
  }

  override fun hashCode() = files.hashCode()

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