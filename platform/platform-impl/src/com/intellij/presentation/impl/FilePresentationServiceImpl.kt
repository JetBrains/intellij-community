// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.presentation.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.presentation.FilePresentationService
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.awt.Color

internal class FilePresentationServiceImpl(private val project: Project) : FilePresentationService {
  @RequiresReadLock
  @RequiresBackgroundThread
  override fun getFileBackgroundColor(file: VirtualFile): Color? {
    require(file.isValid)
    return VfsPresentationUtil.getFileBackgroundColor(project, file)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun getFileBackgroundColor(element: PsiElement): Color? {
    PsiUtilCore.ensureValid(element)
    val file = PsiUtilCore.getVirtualFile(element)
    if (file != null) {
      return getFileBackgroundColor(file)
    }
    else if (element is PsiDirectory) {
      return getFileBackgroundColor(element.virtualFile)
    }
    else if (element is PsiDirectoryContainer) {
      var result: Color? = null
      for (dir in element.directories) {
        val color = getFileBackgroundColor(dir.virtualFile) ?: continue
        if (result == null) {
          // color of the first directory
          result = color
        }
        else if (result != color) {
          // more than 1 directory with different colors
          return null
        }
      }
      return result
    }
    return null
  }
}
