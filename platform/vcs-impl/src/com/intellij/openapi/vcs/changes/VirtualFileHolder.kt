// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

class VirtualFileHolder(private val myProject: Project) : FileHolder {
  private val myFiles = hashSetOf<VirtualFile>()

  val files: List<VirtualFile> get() = myFiles.toList()

  override fun cleanAll() = myFiles.clear()
  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) = cleanScope(myFiles, scope)

  fun addFile(file: VirtualFile) {
    myFiles.add(file)
  }

  fun removeFile(file: VirtualFile) {
    myFiles.remove(file)
  }

  override fun copy(): VirtualFileHolder =
    VirtualFileHolder(myProject).also {
      it.myFiles.addAll(myFiles)
    }

  fun containsFile(file: VirtualFile): Boolean = file in myFiles

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as VirtualFileHolder

    return myFiles == other.myFiles
  }

  override fun hashCode(): Int = myFiles.hashCode()

  companion object {

    fun cleanScope(files: MutableCollection<VirtualFile>, scope: VcsModifiableDirtyScope) {
      ProgressManager.checkCanceled()
      if (files.isEmpty()) return

      if (scope.recursivelyDirtyDirectories.size == 0) {
        val dirtyFiles = scope.dirtyFiles
        var cleanDroppedFiles = false

        for (dirtyFile in dirtyFiles) {
          val f = dirtyFile.virtualFile
          if (f != null) {
            files.remove(f)
          }
          else {
            cleanDroppedFiles = true
          }
        }
        if (cleanDroppedFiles) {
          val iterator = files.iterator()
          while (iterator.hasNext()) {
            val file = iterator.next()
            if (fileDropped(file)) {
              iterator.remove()
              scope.addDirtyFile(VcsUtil.getFilePath(file))
            }
          }
        }
      }
      else {
        val iterator = files.iterator()
        while (iterator.hasNext()) {
          val file = iterator.next()
          val fileDropped = fileDropped(file)
          if (fileDropped) {
            scope.addDirtyFile(VcsUtil.getFilePath(file))
          }
          if (fileDropped || scope.belongsTo(VcsUtil.getFilePath(file))) {
            iterator.remove()
          }
        }
      }
    }

    private fun fileDropped(file: VirtualFile): Boolean = !file.isValid
  }
}
