// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.INativeFileType
import com.intellij.pom.Navigatable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileNavigatorImpl : FileNavigator {
  private val ignoreContextEditor = ThreadLocal<Boolean?>()

  override fun canNavigateToSource(descriptor: OpenFileDescriptor): Boolean {
    val file = descriptor.file
    if (file.isValid) {
      return FileEditorManager.getInstance(descriptor.project).canOpenFile(file) || file.fileType is INativeFileType
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
      ProjectFileNavigatorImpl.getInstance(descriptor.project).scheduleNavigateInProjectView(descriptor.file, requestFocus)
    }
  }

  override suspend fun navigateAsync(descriptor: OpenFileDescriptor, requestFocus: Boolean) {
    check(canNavigate(descriptor)) { "target not valid" }
    if (!descriptor.file.isDirectory && navigateInEditorOrNativeAppAsync(descriptor, requestFocus)) {
      return
    }
    else {
      ProjectFileNavigatorImpl.getInstance(descriptor.project).scheduleNavigateInProjectView(descriptor.file, requestFocus)
    }
  }

  private fun navigateInEditorOrNativeApp(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    return tryNavigateInNativeAppOrRunCallback(descriptor) { navigateInEditor(descriptor, requestFocus) }
  }

  private suspend fun navigateInEditorOrNativeAppAsync(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    return tryNavigateInNativeAppOrRunCallback(descriptor) { navigateInEditorAsync(descriptor, requestFocus) }
  }

  private inline fun tryNavigateInNativeAppOrRunCallback(descriptor: OpenFileDescriptor, navigateInEditor: () -> Boolean): Boolean {
    val type = FileTypeManager.getInstance().getKnownFileTypeOrAssociate(descriptor.file, descriptor.project)
    if (type == null || !descriptor.file.isValid) {
      return false
    }

    if (type is INativeFileType) {
      return type.openFileInAssociatedApplication(descriptor.project, descriptor.file)
    }
    else {
      return navigateInEditor()
    }
  }

  override fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    return navigateInRequestedEditor(descriptor) || navigateInAnyFileEditor(descriptor, requestFocus)
  }

  override suspend fun navigateInEditorAsync(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    return withContext(Dispatchers.EDT) { navigateInRequestedEditor(descriptor) } ||
           navigateInAnyFileEditorAsync(descriptor, requestFocus)
  }

  fun navigateInRequestedEditor(descriptor: OpenFileDescriptor): Boolean {
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

  suspend fun navigateInRequestedEditorAsync(descriptor: OpenFileDescriptor, dataContext: DataContext): Boolean {
    val e = OpenFileDescriptor.NAVIGATE_IN_EDITOR.getData(dataContext) ?: return false
    if (e.isDisposed) {
      logger<FileNavigatorImpl>().error("Disposed editor returned for NAVIGATE_IN_EDITOR from $dataContext")
      return false
    }

    if (serviceAsync<FileDocumentManager>().getFile(e.document) != descriptor.file) {
      return false
    }

    withContext(Dispatchers.EDT) {
      OpenFileDescriptor.navigateInEditor(descriptor, e)
    }
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
      fileEditorManager.runWhenLoaded(editor.editor) { OpenFileDescriptor.unfoldCurrentLine(editor.editor) }
    }
  }
  return !editors.isEmpty()
}

private suspend fun navigateInAnyFileEditorAsync(descriptor: OpenFileDescriptor, focusEditor: Boolean): Boolean {
  val fileEditorManager = FileEditorManagerEx.getInstanceExAsync(descriptor.project)
  val editors = fileEditorManager.openFileEditorAsync(descriptor, focusEditor)
  withContext(Dispatchers.EDT) {
    for (editor in editors) {
      if (editor is TextEditor) {
        fileEditorManager.runWhenLoaded(editor.editor) { OpenFileDescriptor.unfoldCurrentLine(editor.editor) }
      }
    }
  }
  return !editors.isEmpty()
}
