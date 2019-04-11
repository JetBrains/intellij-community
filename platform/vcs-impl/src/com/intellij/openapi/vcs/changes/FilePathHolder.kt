// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import java.util.*

class FilePathHolder(private val project: Project, private val type: FileHolder.HolderType) : FileHolder {
  private val files = HashSet<FilePath>()

  // todo track number of copies made
  fun getFiles() = files.toList()

  override fun getType() = type

  override fun notifyVcsStarted(vcs: AbstractVcs<*>) {}

  override fun cleanAll() {
    files.clear()
  }

  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) {
    cleanScope(project, files, scope)
  }

  fun addFile(file: FilePath) {
    files.add(file)
  }

  fun removeFile(file: FilePath) {
    files.remove(file)
  }

  override fun copy(): FilePathHolder {
    val copyHolder = FilePathHolder(project, type)
    copyHolder.files.addAll(files)
    return copyHolder
  }

  fun containsFile(file: FilePath) = files.contains(file)

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val that = o as FilePathHolder?

    return files == that!!.files
  }

  override fun hashCode() = files.hashCode()

  companion object {

    internal fun cleanScope(project: Project, files: MutableCollection<FilePath>, scope: VcsModifiableDirtyScope) {
      runReadAction {
        if (project.isDisposed || files.isEmpty()) return@runReadAction

        if (scope.recursivelyDirtyDirectories.size == 0) {
          val dirtyFiles = scope.dirtyFiles
          var cleanDroppedFiles = false

          for (dirtyFile in dirtyFiles) {
            if (dirtyFile.ioFile.exists()) {
              files.remove(dirtyFile)
            }
            else {
              cleanDroppedFiles = true
            }
          }
          if (cleanDroppedFiles) {
            val iterator = files.iterator()
            while (iterator.hasNext()) {
              val filePath = iterator.next()
              if (fileDropped(filePath)) {
                iterator.remove()
                scope.addDirtyFile(filePath)
              }
            }
          }
        }
        else {
          val iterator = files.iterator()
          while (iterator.hasNext()) {
            val filePath = iterator.next()
            val fileDropped = fileDropped(filePath)
            if (fileDropped) {
              scope.addDirtyFile(filePath)
            }
            if (fileDropped || scope.belongsTo(filePath)) {
              iterator.remove()
            }
          }
        }
      }
    }

    private fun fileDropped(filePath: FilePath): Boolean {
      return !filePath.ioFile.exists()
    }
  }
}