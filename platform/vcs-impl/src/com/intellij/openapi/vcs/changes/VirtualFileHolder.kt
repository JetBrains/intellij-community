// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import java.util.*

class VirtualFileHolder(private val myProject: Project, private val myType: FileHolder.HolderType) : FileHolder {
  private val myFiles = HashSet<VirtualFile>()

  // todo track number of copies made
  val files: List<VirtualFile>
    get() = ArrayList(myFiles)

  override fun getType(): FileHolder.HolderType {
    return myType
  }

  override fun notifyVcsStarted(vcs: AbstractVcs) {}

  override fun cleanAll() = myFiles.clear()

  override fun cleanAndAdjustScope(scope: VcsModifiableDirtyScope) = cleanScope(myFiles, scope)

  fun addFile(file: VirtualFile) {
    myFiles.add(file)
  }

  fun removeFile(file: VirtualFile) {
    myFiles.remove(file)
  }

  override fun copy(): VirtualFileHolder {
    val copyHolder = VirtualFileHolder(myProject, myType)
    copyHolder.myFiles.addAll(myFiles)
    return copyHolder
  }

  fun containsFile(file: VirtualFile): Boolean {
    return myFiles.contains(file)
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val that = (o as? VirtualFileHolder?) ?: return false

    return myFiles == that.myFiles

  }

  override fun hashCode() = myFiles.hashCode()

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

    private fun fileDropped(file: VirtualFile): Boolean {
      return !file.isValid
    }
  }
}
