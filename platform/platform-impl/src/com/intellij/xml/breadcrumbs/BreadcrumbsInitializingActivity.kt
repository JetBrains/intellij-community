// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.breadcrumbs

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector
import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.ClientEditorManager
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
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal

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
        reinitBreadcrumbsInAllEditors(project, true)
      }
    })
    FileBreadcrumbsCollector.EP_NAME.getPoint(project).addChangeListener({ reinitBreadcrumbsInAllEditors(project, true) }, project)
    VirtualFileManager.getInstance().addVirtualFileListener(MyVirtualFileListener(project), project)
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { reinitBreadcrumbsInAllEditors(project, true) })

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

@Internal
fun reinitBreadcrumbsInAllEditors(project: Project, allClients: Boolean) {
  if (project.isDisposed) {
    return
  }

  val fileEditorManager = FileEditorManager.getInstance(project)
  val above = isAbove()
  val openFiles = if (allClients) fileEditorManager.getOpenFilesWithRemotes() else fileEditorManager.openFiles.toList()
  for (virtualFile in openFiles) {
    reinitBreadcrumbsComponent(fileEditorManager, virtualFile, above, allClients)
  }
}

private class MyFileEditorManagerListener : FileEditorManagerListener {
  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    val above = isAbove()
    reinitBreadcrumbsComponent(source, file, above, false)
  }
}

private class MyVirtualFileListener(private val myProject: Project) : VirtualFileListener {
  override fun propertyChanged(event: VirtualFilePropertyEvent) {
    if (VirtualFile.PROP_NAME == event.propertyName && !myProject.isDisposed) {
      val fileEditorManager = FileEditorManager.getInstance(myProject)
      val file = event.file
      if (fileEditorManager.isFileOpen(file)) {
        val above = isAbove()
        reinitBreadcrumbsComponent(fileEditorManager = fileEditorManager, file = file, above = above, allClients = true)
      }
    }
  }
}

private fun isAbove() = ClientId.withClientId(ClientId.localId) { EditorSettingsExternalizable.getInstance().isBreadcrumbsAbove }

private fun reinitBreadcrumbsComponent(fileEditorManager: FileEditorManager, file: VirtualFile, above: Boolean, allClients: Boolean) {
  val editors = if (allClients) fileEditorManager.getAllEditors(file) else fileEditorManager.getEditors(file)
  for (fileEditor in editors) {
    if (fileEditor is TextEditor) {
      reinitBreadcrumbComponent(fileEditor = fileEditor, fileEditorManager = fileEditorManager, file = file, above = above)
    }
  }
}

@Internal
fun reinitBreadcrumbComponent(fileEditor: TextEditor, fileEditorManager: FileEditorManager, file: VirtualFile, above: Boolean) {
  val editor = fileEditor.editor
  val project = fileEditorManager.project
  val forcedShown = BreadcrumbsForceShownSettings.getForcedShown(editor)
  val editorIsValid = fileEditor.isValid
  val clientId = ClientEditorManager.getClientId(editor) ?: ClientId.localId
  (project as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.Default + clientId.asContextElement()) {
    val isSuitable = readAction {
      isSuitable(project, file, forcedShown, editorIsValid)
    }
    withContext(Dispatchers.EDT) {
      if (isSuitable) {
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
        val wrapper = BreadcrumbsXmlWrapper.getBreadcrumbWrapper(editor)
        if (wrapper != null) {
          disposeWrapper(fileEditorManager, fileEditor, wrapper)
        }
      }
    }
  }
}

@RequiresBackgroundThread
private fun isSuitable(project: Project, file: VirtualFile, forcedShown: Boolean?, editorIsValid: Boolean): Boolean {
  if (file is HttpVirtualFile || !editorIsValid) {
    return false
  }
  val providerExists = BreadcrumbsUtilEx.findProvider(file, project, forcedShown) != null

  for (collector in FileBreadcrumbsCollector.EP_NAME.getExtensions(project)) {
    if (!providerExists && collector.requiresProvider()) continue

    if (collector.handlesFile(file)) {
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
  fileEditorManager.project.messageBus.syncPublisher(BreadcrumbsInitListener.TOPIC)
    .breadcrumbsPanelRegistered(wrapper, fileEditor, fileEditorManager)
}

private fun disposeWrapper(fileEditorManager: FileEditorManager, fileEditor: FileEditor, wrapper: BreadcrumbsXmlWrapper) {
  remove(fileEditorManager, fileEditor, wrapper)
  Disposer.dispose(wrapper)
}