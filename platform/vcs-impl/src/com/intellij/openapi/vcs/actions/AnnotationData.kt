// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.vfs.ContentRevisionVirtualFile
import com.intellij.openapi.vcs.vfs.VcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class AnnotationData(val vcs: AbstractVcs, val filePath: FilePath, val revisionNumber: VcsRevisionNumber) {
  companion object {
    @JvmStatic
    fun extractFrom(project: Project, file: VirtualFile): AnnotationData? {
      val filePath: FilePath
      val revisionNumber: VcsRevisionNumber
      when (file) {
        is VcsVirtualFile -> {
          val revision = file.fileRevision ?: return null
          filePath = if (revision is VcsFileRevisionEx) revision.path else VcsUtil.getFilePath(file)
          revisionNumber = revision.revisionNumber
        }
        is ContentRevisionVirtualFile -> {
          val revision = file.contentRevision
          filePath = revision.file
          revisionNumber = revision.revisionNumber
        }
        else -> return null
      }

      if (revisionNumber is TextRevisionNumber || revisionNumber === VcsRevisionNumber.NULL) return null

      val vcs = VcsUtil.getVcsFor(project, filePath) ?: return null
      return AnnotationData(vcs, filePath, revisionNumber)
    }
  }
}
