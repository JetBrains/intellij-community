// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VirtualFile

internal fun reportInfiniteRecursion(file: VirtualFile, fileIndex: WorkspaceFileIndexImpl) {
  val parentsData = runReadAction { 
    generateSequence(file, VirtualFile::getParent).joinToString("\n") { file ->
      val fileInfo = fileIndex.getFileInfo(file, true, true, true, false, false, false)
      val symlinkData = if (file.`is`(VFileProperty.SYMLINK)) {
        ", link to ${file.canonicalFile}, recursive=${file.isRecursiveOrCircularSymlink}"
      }
      else ""
      "${file.name}, info=$fileInfo$symlinkData"
    }
  }
  logger<WorkspaceFileIndexImpl>().error("Infinite recursion detected", Attachment("parent-directories.txt", parentsData))
}