// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.local.CoreLocalFileSystem
import com.intellij.util.SmartList
import java.io.File

internal class PathChooserDialogHelper(private val descriptor: FileChooserDescriptor) {
  private val localFileSystem by lazy {
    val app = ApplicationManager.getApplication()
    if (app == null) CoreLocalFileSystem() else LocalFileSystem.getInstance()
  }

  fun getChosenFiles(files: Array<File>): List<VirtualFile> {
    val virtualFiles = files.mapNotNullTo(SmartList()) {
      val virtualFile = fileToVirtualFile(it)
      if (virtualFile != null && virtualFile.isValid) {
        virtualFile
      }
      else {
        null
      }
    }
    return FileChooserUtil.getChosenFiles(descriptor, virtualFiles)
  }

  fun fileToVirtualFile(file: File): VirtualFile? {
    return localFileSystem.refreshAndFindFileByPath(FileUtilRt.toSystemIndependentName(file.absolutePath))
  }
}