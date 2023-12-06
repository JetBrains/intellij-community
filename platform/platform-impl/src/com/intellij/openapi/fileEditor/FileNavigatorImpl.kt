// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.*
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.INativeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

class FileNavigatorImpl : FileNavigator {
  private val ignoreContextEditor = ThreadLocal<Boolean?>()

  override fun canNavigateToSource(descriptor: OpenFileDescriptor): Boolean {
    val file = descriptor.file
    if (file.isValid) {
      return FileEditorManagerEx.getInstanceEx(descriptor.project).canOpenFile(file) || file.fileType is INativeFileType
    }
    else {
      return false
    }
  }

  override fun navigate(descriptor: OpenFileDescriptor, requestFocus: Boolean) {
    check(canNavigate(descriptor)) { "target not valid" }
    if (!descriptor.file.isDirectory && navigateInEditorOrNativeApp(descriptor, requestFocus)) {
      return
    }
    else {
      ProjectFileNavigatorImpl.getInstance(descriptor.project).navigateInProjectView(descriptor.file, requestFocus)
    }
  }

  private fun navigateInEditorOrNativeApp(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    val type = FileTypeManager.getInstance().getKnownFileTypeOrAssociate(descriptor.file, descriptor.project)
    if (type == null || !descriptor.file.isValid) {
      return false
    }

    if (type is INativeFileType) {
      return type.openFileInAssociatedApplication(descriptor.project, descriptor.file)
    }
    else {
      return navigateInEditor(descriptor, requestFocus)
    }
  }

  override fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    return navigateInRequestedEditor(descriptor) || navigateInAnyFileEditor(descriptor, requestFocus)
  }

  private fun navigateInRequestedEditor(descriptor: OpenFileDescriptor): Boolean {
    if (ignoreContextEditor.get() == true) {
      return false
    }

    @Suppress("DEPRECATION")
    val dataContext = DataManager.getInstance().dataContext
    val e = OpenFileDescriptor.NAVIGATE_IN_EDITOR.getData(dataContext) ?: return false
    if (e.isDisposed) {
      logger<FileNavigatorImpl>().error("Disposed editor returned for NAVIGATE_IN_EDITOR from $dataContext")
      return false
    }
    if (FileDocumentManager.getInstance().getFile(e.document) != descriptor.file) {
      return false
    }
    OpenFileDescriptor.navigateInEditor(descriptor, e)
    return true
  }

  @ApiStatus.Experimental
  fun navigateIgnoringContextEditor(navigatable: Navigatable): Boolean {
    if (!navigatable.canNavigate()) {
      return false
    }

    ignoreContextEditor.set(true)
    try {
      navigatable.navigate(true)
    }
    finally {
      ignoreContextEditor.set(null)
    }
    return true
  }
}

@RequiresEdt
private fun navigateInAnyFileEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): Boolean {
  val fileEditorManager = FileEditorManager.getInstance(descriptor.project)
  val editors = fileEditorManager.openFileEditor(descriptor, focusEditor)
  for (editor in editors) {
    if (editor is TextEditor) {
      val e = editor.editor
      fileEditorManager.runWhenLoaded(e) { OpenFileDescriptor.unfoldCurrentLine(e) }
    }
  }
  return !editors.isEmpty()
}
