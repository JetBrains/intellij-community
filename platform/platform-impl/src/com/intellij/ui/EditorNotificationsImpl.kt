// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.ui

import com.intellij.ProjectTopics
import com.intellij.diagnostic.PluginException
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
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
import com.intellij.util.SingleAlarm
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.CancellationException
import java.util.function.BiFunction
import javax.swing.JComponent

class EditorNotificationsImpl(private val project: Project) : EditorNotifications(), Disposable {
  private val updateAllAlarm = SingleAlarm(::doUpdateAllNotifications, 100, this)

  private val fileToUpdateNotificationJob = CollectionFactory.createConcurrentWeakMap<VirtualFile, Job>()
  private val fileEditorToMap =
    CollectionFactory.createConcurrentWeakMap<FileEditor, MutableMap<Class<out EditorNotificationProvider>, JComponent>>()

  private val coroutineScope: CoroutineScope = project.coroutineScope.childScope()

  init {
    val connection = project.messageBus.connect()
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        updateNotifications(file)
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        val editor = event.newEditor ?: return
        if (editor.getUserData(PENDING_UPDATE) == java.lang.Boolean.TRUE) {
          editor.putUserData(PENDING_UPDATE, null)
          updateEditors(file, listOf(editor))
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
        override fun extensionAdded(extension: EditorNotificationProvider, pluginDescriptor: PluginDescriptor) {
          updateAllNotifications()
        }

        override fun extensionRemoved(extension: EditorNotificationProvider, pluginDescriptor: PluginDescriptor) {
          updateNotifications(extension)
        }
      }, false, null)
  }

  override fun dispose() {
    coroutineScope.cancel()
    // help GC
    fileToUpdateNotificationJob.clear()
    fileEditorToMap.clear()
  }

  companion object {
    private val PENDING_UPDATE = Key.create<Boolean>("pending.notification.update")
  }

  @VisibleForTesting
  fun getNotificationPanels(fileEditor: FileEditor): MutableMap<Class<out EditorNotificationProvider>, JComponent> {
    return fileEditorToMap.computeIfAbsent(fileEditor) { WeakHashMap() }
  }

  @TestOnly
  fun completeAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    runUnderModalProgressIfIsEdt {
      val parentJob = coroutineScope.coroutineContext[Job]!!
      while (true) {
        // process all events in EDT
        withContext(Dispatchers.EDT) {
          yield()
        }

        val jobs = parentJob.children.toList()
        if (jobs.isEmpty()) {
          break
        }

        jobs.joinAll()

        // process all events in EDT
        withContext(Dispatchers.EDT) {
          yield()
        }
      }
    }
    check(fileToUpdateNotificationJob.isEmpty())
  }

  override fun updateNotifications(provider: EditorNotificationProvider) {
    for (file in FileEditorManager.getInstance(project).openFilesWithRemotes) {
      for (editor in getEditors(file).toList()) {
        updateNotification(fileEditor = editor, provider = provider, component = null)
      }
    }
  }

  override fun updateNotifications(file: VirtualFile) {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      if (!file.isValid) {
        return@launch
      }

      doUpdateNotifications(file)
    }
  }

  @RequiresEdt
  private fun doUpdateNotifications(file: VirtualFile) {
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
    updateEditors(file, editors.toList())
  }

  private fun getEditors(file: VirtualFile): Sequence<FileEditor> {
    return FileEditorManager.getInstance(project).getAllEditors(file).asSequence().filter { it !is TextEditor || isEditorLoaded(it.editor) }
  }

  private fun updateEditors(file: VirtualFile, fileEditors: List<FileEditor>) {
    val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
      // delay for debounce
      delay(100)

      // Please don't remove this readAction {} here, it's needed for checking of validity of injected files,
      // and many unpleasant exceptions appear in case if validity check is not wrapped.
      if (!readAction { file.isValid }) {
        return@launch
      }

      // light project is not disposed in tests
      if (project.isDisposed) {
        return@launch
      }

      coroutineContext.ensureActive()
      val point = EditorNotificationProvider.EP_NAME.getPoint(project) as ExtensionPointImpl<EditorNotificationProvider>
      for (adapter in point.sortedAdapters.toTypedArray()) {
        coroutineContext.ensureActive()

        try {
          if (project.isDisposed) {
            return@launch
          }
          val provider = adapter.createInstance<EditorNotificationProvider>(project) ?: continue

          coroutineContext.ensureActive()

          if (DumbService.isDumb(project) && !DumbService.isDumbAware(provider)) {
            continue
          }

          val componentProvider = readAction {
            if (file.isValid && !project.isDisposed) {
              provider.collectNotificationData(project, file)
            }
            else {
              null
            }
          }
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            if (!file.isValid) {
              return@withContext
            }

            for (fileEditor in fileEditors) {
              updateNotification(fileEditor = fileEditor, provider = provider, component = componentProvider?.apply(fileEditor))
            }
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
    job.invokeOnCompletion { fileToUpdateNotificationJob.remove(file, job) }

    fileToUpdateNotificationJob.merge(file, job, BiFunction { old, new ->
      old.cancel()
      new
    })
    job.start()
  }

  @RequiresEdt
  private fun updateNotification(fileEditor: FileEditor, provider: EditorNotificationProvider, component: JComponent?) {
    val panels = fileEditorToMap.get(fileEditor)
    val providerClass = provider.javaClass
    panels?.get(providerClass)?.let { old ->
      FileEditorManager.getInstance(project).removeTopComponent(fileEditor, old)
    }
    if (component == null) {
      panels?.remove(providerClass)
    }
    else {
      if (component is EditorNotificationPanel) {
        component.setClassConsumer {
          logHandlerInvoked(project, provider, it)
        }
      }
      logNotificationShown(project, provider)
      FileEditorManager.getInstance(project).addTopComponent(fileEditor, component)

      (panels ?: getNotificationPanels(fileEditor)).put(providerClass, component)
    }
  }

  override fun updateAllNotifications() {
    if (project.isDefault) {
      throw UnsupportedOperationException("Editor notifications aren't supported for default project")
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      doUpdateAllNotifications()
    }
    else {
      updateAllAlarm.cancelAndRequest()
    }
  }

  @RequiresEdt
  private fun doUpdateAllNotifications() {
    val fileEditorManager = FileEditorManager.getInstance(project) ?: throw IllegalStateException("No FileEditorManager for $project")
    for (file in fileEditorManager.openFilesWithRemotes) {
      doUpdateNotifications(file)
    }
  }

  internal class RefactoringListenerProvider : RefactoringElementListenerProvider {
    override fun getListener(element: PsiElement): RefactoringElementListener? {
      if (element !is PsiFile) {
        return null
      }

      return object : RefactoringElementAdapter() {
        override fun elementRenamedOrMoved(newElement: PsiElement) {
          if (newElement is PsiFile) {
            val vFile = newElement.getContainingFile().virtualFile ?: return
            getInstance(element.getProject()).updateNotifications(vFile)
          }
        }

        override fun undoElementMovedOrRenamed(newElement: PsiElement, oldQualifiedName: String) {
          elementRenamedOrMoved(newElement)
        }
      }
    }
  }
}