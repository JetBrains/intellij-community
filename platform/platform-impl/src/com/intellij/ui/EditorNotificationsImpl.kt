// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.ProjectTopics
import com.intellij.diagnostic.PluginException
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.listeners.RefactoringElementAdapter
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.CancellationException
import java.util.function.BiFunction
import javax.swing.JComponent

class EditorNotificationsImpl(private val  project: Project) : EditorNotifications() {
  private val updateMerger = MergingUpdateQueue("EditorNotifications update merger", 100, true, null, project).usePassThroughInUnitTestMode()

  private val fileToUpdateNotificationJob = CollectionFactory.createConcurrentWeakMap<VirtualFile, Job>()

  init {
    val connection = project.messageBus.connect()
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        updateNotifications(file)
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile
        val editor = event.newEditor
        if (file != null && editor != null && java.lang.Boolean.TRUE == editor.getUserData(PENDING_UPDATE)) {
          editor.putUserData(PENDING_UPDATE, null)
          updateEditor(file, editor)
        }
      }
    })
    connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun enteredDumbMode() {
        updateAllNotifications()
      }

      override fun exitDumbMode() {
        updateAllNotifications()
      }
    })
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        updateAllNotifications()
      }
    })
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, AdditionalLibraryRootsListener { _, _, _, _ -> updateAllNotifications() })
    EditorNotificationProvider.EP_NAME.getPoint(project)
      .addExtensionPointListener(object : ExtensionPointListener<EditorNotificationProvider> {
        override fun extensionAdded(extension: EditorNotificationProvider, descriptor: PluginDescriptor) {
          updateAllNotifications()
        }

        override fun extensionRemoved(extension: EditorNotificationProvider, descriptor: PluginDescriptor) {
          updateNotifications(extension)
        }
      }, false, null)
  }

  companion object {
    private val EDITOR_NOTIFICATION_PROVIDER =
      Key.create<MutableMap<Class<out EditorNotificationProvider>, JComponent?>>("editor.notification.provider")

    private val PENDING_UPDATE = Key.create<Boolean>("pending.notification.update")

    @VisibleForTesting
    @JvmStatic
    fun getNotificationPanels(editor: FileEditor): MutableMap<Class<out EditorNotificationProvider>, JComponent?> {
      editor.getUserData(EDITOR_NOTIFICATION_PROVIDER)?.let {
        return it
      }

      editor.putUserData(EDITOR_NOTIFICATION_PROVIDER, WeakHashMap())
      editor.getUserData(EDITOR_NOTIFICATION_PROVIDER)?.let {
        return it
      }
      val editorClass = editor.javaClass
      val pluginException = PluginException.createByClass(
        "User data is not supported; editorClass='${editorClass.name}'; key='$EDITOR_NOTIFICATION_PROVIDER'",
        null,
        editorClass)
      Logger.getInstance(editorClass).error(pluginException)
      throw pluginException
    }

    @TestOnly
    @JvmStatic
    fun completeAsyncTasks(project: Project) {
      runUnderModalProgressIfIsEdt {
        withContext(Dispatchers.EDT) {
          yield()
        }

        val editorNotificationManager = getInstance(project) as EditorNotificationsImpl
        for (job in editorNotificationManager.fileToUpdateNotificationJob.values.toList()) {
          try {
            job.join()
          }
          catch (ignore: CancellationException) {
          }
        }

        withContext(Dispatchers.EDT) {
          yield()
        }
      }
    }
  }

  override fun updateNotifications(provider: EditorNotificationProvider) {
    for (file in FileEditorManager.getInstance(project).openFilesWithRemotes) {
      for (editor in getEditors(file)) {
        updateNotification(editor, provider, null)
      }
    }
  }

  override fun updateNotifications(file: VirtualFile) {
    AppUIExecutor
      .onUiThread(ModalityState.any())
      .expireWith(project)
      .execute {
        if (project.isDisposed || !file.isValid) {
          return@execute
        }
        var editors = getEditors(file)
        if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
          editors = editors.filter { fileEditor ->
            val visible = UIUtil.isShowing(fileEditor.component)
            if (!visible) {
              fileEditor.putUserData(PENDING_UPDATE, java.lang.Boolean.TRUE)
            }
            visible
          }
        }
        for (editor in editors) {
          updateEditor(file, editor)
        }
      }
  }

  private fun getEditors(file: VirtualFile): List<FileEditor> {
    return FileEditorManager.getInstance(project).getAllEditors(file).filter { it !is TextEditor || isEditorLoaded(it.editor) }
  }

  private fun updateEditor(file: VirtualFile, fileEditor: FileEditor) {
    // light project is not disposed in tests
    if (project.isDisposed) {
      return
    }

    val job = project.coroutineScope.launch(start = CoroutineStart.LAZY) {
      if (!file.isValid) {
        return@launch
      }

      coroutineContext.ensureActive()
      try {
        val point = EditorNotificationProvider.EP_NAME.getPoint(project) as ExtensionPointImpl<EditorNotificationProvider>
        for (adapter in point.sortedAdapters) {
          coroutineContext.ensureActive()

          try {
            val provider = adapter.createInstance<EditorNotificationProvider>(project) ?: continue

            coroutineContext.ensureActive()

            if (DumbService.isDumb(project) && !DumbService.isDumbAware(provider)) {
              continue
            }

            val componentProvider = readAction {
              if (file.isValid) {
                provider.collectNotificationData(project, file)
              }
              else {
                null
              }
            } ?: continue
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              val component = componentProvider.apply(fileEditor)
              updateNotification(fileEditor, provider, component)
            }
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: Exception) {
            val pluginException = if (e is PluginException) e else PluginException(e, adapter.pluginDescriptor.pluginId)
            logger<EditorNotificationsImpl>().error(pluginException)
          }
        }
      }
      finally {
        fileToUpdateNotificationJob.remove(file, coroutineContext.job)
      }
    }
    job.invokeOnCompletion { fileToUpdateNotificationJob.remove(file, job) }

    fileToUpdateNotificationJob.merge(file, job, BiFunction { old, new ->
      old.cancel()
      new
    })
    job.start()
  }

  @RequiresEdt
  private fun updateNotification(editor: FileEditor, provider: EditorNotificationProvider, component: JComponent?) {
    val panels = getNotificationPanels(editor)
    val providerClass = provider.javaClass
    panels.get(providerClass)?.let { old ->
      FileEditorManager.getInstance(project).removeTopComponent(editor, old)
    }
    if (component != null) {
      if (component is EditorNotificationPanel) {
        component.setClassConsumer {
          logHandlerInvoked(project, provider, it)
        }
      }
      logNotificationShown(project, provider)
      FileEditorManager.getInstance(project).addTopComponent(editor, component)
    }
    panels.put(providerClass, component)
  }

  override fun updateAllNotifications() {
    if (project.isDefault) {
      throw UnsupportedOperationException("Editor notifications aren't supported for default project")
    }

    val fileEditorManager = FileEditorManager.getInstance(project) ?: throw IllegalStateException("No FileEditorManager for $project")
    updateMerger.queue(object : Update("update") {
      override fun run() {
        for (file in fileEditorManager.openFilesWithRemotes) {
          updateNotifications(file)
        }
      }
    })
  }

  internal class RefactoringListenerProvider : RefactoringElementListenerProvider {
    override fun getListener(element: PsiElement): RefactoringElementListener? {
      if (element !is PsiFile) {
        return null
      }

      return object : RefactoringElementAdapter() {
        override fun elementRenamedOrMoved(newElement: PsiElement) {
          if (newElement is PsiFile) {
            val vFile = newElement.getContainingFile().virtualFile
            if (vFile != null) {
              getInstance(element.getProject()).updateNotifications(vFile)
            }
          }
        }

        override fun undoElementMovedOrRenamed(newElement: PsiElement, oldQualifiedName: String) {
          elementRenamedOrMoved(newElement)
        }
      }
    }
  }
}