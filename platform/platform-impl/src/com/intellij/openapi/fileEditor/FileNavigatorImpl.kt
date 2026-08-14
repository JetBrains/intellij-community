// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.INativeFileType
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class FileNavigatorImpl : FileNavigator {
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
    navigate(descriptor, requestFocus, requestedEditor = contextEditor)
  }

  override fun navigate(descriptor: OpenFileDescriptor, requestFocus: Boolean, requestedEditor: Editor?) {
    check(canNavigate(descriptor)) { "target not valid" }
    if (!descriptor.file.isDirectory && navigateInEditorOrNativeApp(descriptor, requestFocus, requestedEditor)) {
      return
    }
    else {
      ProjectFileNavigatorImpl.getInstance(descriptor.project).scheduleNavigateInProjectView(descriptor.file, requestFocus)
    }
  }

  private fun navigateInEditorOrNativeApp(descriptor: OpenFileDescriptor, requestFocus: Boolean, requestedEditor: Editor?): Boolean {
    val type = FileTypeManager.getInstance().getKnownFileTypeOrAssociate(descriptor.file, descriptor.project)
    if (type == null || !descriptor.file.isValid) {
      return false
    }

    if (type is INativeFileType) {
      return type.openFileInAssociatedApplication(descriptor.project, descriptor.file)
    }
    else {
      return navigateInEditor(descriptor, requestFocus, requestedEditor)
    }
  }

  override fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    return navigateInEditor(descriptor, requestFocus, contextEditor)
  }

  private fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean, requestedEditor: Editor?): Boolean {
    return navigateInRequestedEditor(descriptor, requestedEditor, requestFocus) || navigateInAnyFileEditor(descriptor, requestFocus)
  }

  private val contextEditor: Editor?
    get() {
      @Suppress("DEPRECATION")
      val dataContext = DataManager.getInstance().dataContext
      val e = OpenFileDescriptor.NAVIGATE_IN_EDITOR.getData(dataContext) ?: return null
      if (e.isDisposed) {
        // a disposed editor in the current data context means the context itself is stale
        LOG.error("Disposed editor returned for NAVIGATE_IN_EDITOR from $dataContext")
        return null
      }
      return e
  }

  private fun navigateInRequestedEditor(descriptor: OpenFileDescriptor, requestedEditor: Editor?, requestFocus: Boolean = false): Boolean {
    if (requestedEditor == null) {
      return false
    }
    return navigateInEditorIfShowsFile(descriptor, requestedEditor, FileDocumentManager.getInstance(), requestFocus)
  }

  suspend fun navigateInRequestedEditorAsync(
    descriptor: OpenFileDescriptor,
    editor: Editor,
    requestFocus: Boolean = false,
  ): Boolean {
    if (editor.isDisposed) {
      // the editor may be closed while the navigation is being prepared; fall back to opening the file
      LOG.debug { "Disposed editor requested for navigation to ${descriptor.file}" }
      return false
    }

    val fileDocumentManager = serviceAsync<FileDocumentManager>()
    return withContext(Dispatchers.EDT) {
      navigateInEditorIfShowsFile(descriptor, editor, fileDocumentManager, requestFocus)
    }
  }

  @RequiresEdt
  private fun navigateInEditorIfShowsFile(
    descriptor: OpenFileDescriptor,
    editor: Editor,
    fileDocumentManager: FileDocumentManager,
    requestFocus: Boolean = false,
  ): Boolean {
    if (editor.isDisposed || fileDocumentManager.getFile(editor.document) != descriptor.file) {
      return false
    }
    navigateInEditorAndMaybeFocus(descriptor, editor, requestFocus)
    return true
  }

  companion object {
    private val LOG: Logger = logger<FileNavigatorImpl>()
  }
}

@RequiresEdt
private fun navigateInEditorAndMaybeFocus(descriptor: OpenFileDescriptor, editor: Editor, requestFocus: Boolean) {
  OpenFileDescriptor.navigateInEditor(descriptor, editor)
  if (requestFocus) {
    IdeFocusManager.getGlobalInstance().requestFocusInProject(editor.contentComponent, descriptor.project)
  }
}

@RequiresEdt
private fun navigateInAnyFileEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): Boolean {
  val fileEditorManager = FileEditorManager.getInstance(descriptor.project)
  if (BinaryFileTypeDecompilers.getInstance().hasDecompiler(descriptor.file) &&
      Registry.`is`("hyperlink.ide.decompiler.open.file")) {
    runWithModalProgressBlocking(descriptor.project, IdeBundle.message("progress.title.preparing.navigation")) {
      readAction {
        FileDocumentManager.getInstance().getDocument(descriptor.file) //force decompilation
      }
    }
  }
  val editors = fileEditorManager.openFileEditor(descriptor, focusEditor)
  for (editor in editors) {
    if (editor is TextEditor) {
      fileEditorManager.runWhenLoaded(editor.editor) { OpenFileDescriptor.unfoldCurrentLine(editor.editor) }
    }
  }
  return !editors.isEmpty()
}
