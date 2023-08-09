// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class BreadcrumbsInitializingActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val connection = project.messageBus.simpleConnect()
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyFileEditorManagerListener())
    connection.subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
      override fun fileTypesChanged(event: FileTypeEvent) {
        reinitBreadcrumbsInAllEditors(project)
      }
    })
    FileBreadcrumbsCollector.EP_NAME.getPoint(project).addChangeListener({ reinitBreadcrumbsInAllEditors(project) }, project)
    VirtualFileManager.getInstance().addVirtualFileListener(MyVirtualFileListener(project), project)
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { reinitBreadcrumbsInAllEditors(project) })

    val fileEditorManager = project.serviceAsync<FileEditorManager>()
    val above = isAbove()
    for (virtualFile in fileEditorManager.openFiles) {
      for (fileEditor in fileEditorManager.getAllEditors(virtualFile)) {
        if (fileEditor is TextEditor) {
          withContext(Dispatchers.EDT) {
            blockingContext {
              reinitBreadcrumbComponent(fileEditor = fileEditor, fileEditorManager = fileEditorManager, file = virtualFile, above = above)
            }
          }
        }
      }
    }
  }
}

private fun reinitBreadcrumbsInAllEditors(project: Project) {
  if (project.isDisposed) {
    return
  }

  val fileEditorManager = FileEditorManager.getInstance(project)
  val above = isAbove()
  for (virtualFile in fileEditorManager.openFiles) {
    reinitBreadcrumbsComponent(fileEditorManager, virtualFile, above)
  }
}

private class MyFileEditorManagerListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    val above = isAbove()
    reinitBreadcrumbsComponent(source, file, above)
  }
}

private class MyVirtualFileListener(private val myProject: Project) : VirtualFileListener {
  override fun propertyChanged(event: VirtualFilePropertyEvent) {
    if (VirtualFile.PROP_NAME == event.propertyName && !myProject.isDisposed) {
      val fileEditorManager = FileEditorManager.getInstance(myProject)
      val file = event.file
      if (fileEditorManager.isFileOpen(file)) {
        val above = isAbove()
        reinitBreadcrumbsComponent(fileEditorManager = fileEditorManager, file = file, above = above)
      }
    }
  }
}

private fun isAbove() = EditorSettingsExternalizable.getInstance().isBreadcrumbsAbove

private fun reinitBreadcrumbsComponent(fileEditorManager: FileEditorManager, file: VirtualFile, above: Boolean) {
  for (fileEditor in fileEditorManager.getAllEditors(file)) {
    if (fileEditor is TextEditor) {
      reinitBreadcrumbComponent(fileEditor = fileEditor, fileEditorManager = fileEditorManager, file = file, above = above)
    }
  }
}

private fun reinitBreadcrumbComponent(fileEditor: TextEditor, fileEditorManager: FileEditorManager, file: VirtualFile, above: Boolean) {
  val editor = fileEditor.editor
  if (isSuitable(fileEditorManager.project, fileEditor, file)) {
    var wrapper = BreadcrumbsXmlWrapper.getBreadcrumbWrapper(editor)
    if (wrapper == null) {
      wrapper = BreadcrumbsXmlWrapper(editor)
      registerWrapper(fileEditorManager, fileEditor, wrapper)
    }
    else {
      if (wrapper.breadcrumbs.above != above) {
        remove(fileEditorManager, fileEditor, wrapper)
        wrapper.breadcrumbs.above = above
        add(fileEditorManager, fileEditor, wrapper)
      }
      wrapper.queueUpdate()
    }
    fileEditorManager.project.messageBus.syncPublisher(BreadcrumbsInitListener.TOPIC)
      .breadcrumbsInitialized(wrapper, fileEditor, fileEditorManager)
  }
  else {
    disposeWrapper(fileEditorManager = fileEditorManager,
                   fileEditor = fileEditor,
                   wrapper = BreadcrumbsXmlWrapper.getBreadcrumbWrapper(editor) ?: return)
  }
}

private fun isSuitable(project: Project, editor: TextEditor, file: VirtualFile): Boolean {
  if (file is HttpVirtualFile || !editor.isValid) {
    return false
  }
  for (collector in FileBreadcrumbsCollector.EP_NAME.getExtensions(project)) {
    if (collector.handlesFile(file) && collector.isShownForFile(editor.editor, file)) {
      return true
    }
  }
  return false
}

private fun add(manager: FileEditorManager, editor: FileEditor, wrapper: BreadcrumbsXmlWrapper) {
  if (wrapper.breadcrumbs.above) {
    manager.addTopComponent(editor, wrapper)
  }
  else {
    manager.addBottomComponent(editor, wrapper)
  }
}

private fun remove(manager: FileEditorManager, editor: FileEditor, wrapper: BreadcrumbsXmlWrapper) {
  if (wrapper.breadcrumbs.above) {
    manager.removeTopComponent(editor, wrapper)
  }
  else {
    manager.removeBottomComponent(editor, wrapper)
  }
}

private fun registerWrapper(fileEditorManager: FileEditorManager, fileEditor: FileEditor, wrapper: BreadcrumbsXmlWrapper) {
  add(fileEditorManager, fileEditor, wrapper)
  Disposer.register(fileEditor) { disposeWrapper(fileEditorManager, fileEditor, wrapper) }
}

private fun disposeWrapper(fileEditorManager: FileEditorManager, fileEditor: FileEditor, wrapper: BreadcrumbsXmlWrapper) {
  remove(fileEditorManager, fileEditor, wrapper)
  Disposer.dispose(wrapper)
}