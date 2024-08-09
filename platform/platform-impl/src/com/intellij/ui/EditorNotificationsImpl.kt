// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
@file:OptIn(FlowPreview::class)

package com.intellij.ui

import com.intellij.codeInsight.intention.IntentionActionProvider
import com.intellij.codeInsight.intention.IntentionActionWithOptions
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
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.listeners.RefactoringElementAdapter
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.CancellationException
import java.util.function.BiFunction
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

private val EDITOR_NOTIFICATION_PROVIDER: Key<MutableMap<Class<EditorNotificationProvider>, JComponent>> =
  Key.create("editor.notification.provider")

private val PENDING_UPDATE: Key<Boolean> = Key.create("pending.notification.update")
private val FILE_LEVEL_INTENTIONS: Key<List<IntentionActionWithOptions>> = Key.create("file.level.intentions")

class EditorNotificationsImpl(private val project: Project,
                              coroutineScope: CoroutineScope) : EditorNotifications(), Disposable {

  /**
   * The scope passed in constructor can be a project scope,
   * for example, in [com.intellij.httpClient.http.request.utils.prepareEditorNotifications].
   * Since it's canceled in [dispose], we have to create a child.
   */
  private val coroutineScope: CoroutineScope = coroutineScope.childScope("EditorNotificationsImpl")
  private val updateAllRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val fileToUpdateNotificationJob = CollectionFactory.createConcurrentWeakMap<VirtualFile, Job>()

  private val updateAllRequestFlowJob: Job

  init {
    val connection = project.messageBus.connect()
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        val editor = event.newEditor ?: return
        if (editor.getUserData(PENDING_UPDATE) == true) {
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
    connection.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
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

    updateAllRequestFlowJob = coroutineScope.launch {
      updateAllRequests
        .debounce(100.milliseconds)
        .collectLatest {
          withContext(Dispatchers.EDT) {
            doUpdateAllNotifications()
          }
        }
    }
  }

  override fun dispose() {
    // todo fix HttpRequestBaseFixtureTestCase and then remove `dispose`.
    coroutineScope.cancel()
    // help GC
    fileToUpdateNotificationJob.clear()
  }

  @TestOnly
  fun getNotificationPanels(fileEditor: FileEditor): Map<Class<EditorNotificationProvider>, JComponent> {
    return fileEditor.getUserData(EDITOR_NOTIFICATION_PROVIDER) ?: emptyMap()
  }

  @TestOnly
  fun completeAsyncTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    @Suppress("DEPRECATION")
    runUnderModalProgressIfIsEdt {
      val parentJob = coroutineScope.coroutineContext[Job]!!
      while (true) {
        // process all events in EDT
        withContext(Dispatchers.EDT) {
          yield()
        }

        val jobs = parentJob.children.filter { it != updateAllRequestFlowJob }.toList()
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
      if (file.isValid) {
        doUpdateNotifications(file)
      }
    }
  }

  override fun scheduleUpdateNotifications(editor: TextEditor) {
    ((editor as? TextEditorImpl)?.asyncLoader?.coroutineScope ?: coroutineScope).launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      if (editor.isValid) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment || UIUtil.isShowing(editor.component)) {
          updateEditors(file = editor.file, fileEditors = listOf(editor))
        }
        else {
          editor.putUserData(PENDING_UPDATE, true)
        }
      }
    }
  }

  @RequiresEdt
  private fun doUpdateNotifications(file: VirtualFile) {
    var editors = getEditors(file)
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      editors = editors.filter { fileEditor ->
        val visible = UIUtil.isShowing(fileEditor.component)
        if (!visible) {
          fileEditor.putUserData(PENDING_UPDATE, true)
        }
        visible
      }
    }
    updateEditors(file = file, fileEditors = editors.toList())
  }

  private fun getEditors(file: VirtualFile): Sequence<FileEditor> {
    return FileEditorManager.getInstance(project).getAllEditorList(file).asSequence().filter { it !is TextEditor || isEditorLoaded(it.editor) }
  }

  private fun updateEditors(file: VirtualFile, fileEditors: List<FileEditor>) {
    val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
      // delay for debouncing
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

          val result = readAction {
            if (file.isValid && !project.isDisposed &&
                DumbService.getInstance(project).isUsableInCurrentContext(provider)) {
              Optional.ofNullable(provider.collectNotificationData(project, file))
            }
            else {
              null
            }
          } ?: continue

          val componentProvider = result.orElse(null)
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            if (!file.isValid) {
              return@withContext
            }

            for (fileEditor in fileEditors) {
              writeIntentReadAction {
                updateNotification(fileEditor = fileEditor, provider = provider, component = componentProvider?.apply(fileEditor))
              }
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
    val panels = fileEditor.getUserData(EDITOR_NOTIFICATION_PROVIDER)
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

      if (panels != null) {
        panels.put(providerClass, component)
      }
      else {
        fileEditor.putUserData(EDITOR_NOTIFICATION_PROVIDER, mutableMapOf(providerClass to component))
      }
    }
    collectIntentionActions(fileEditor, project)
  }

  private fun collectIntentionActions(fileEditor: FileEditor, project: Project) {
    val fileEditorManager = FileEditorManager.getInstance(project) as? FileEditorManagerImpl ?: return
    val components = fileEditorManager.getTopComponents(fileEditor)
    val intentions = components.asSequence().filterIsInstance<IntentionActionProvider>().mapNotNull { it.intentionAction }.toList()
    fileEditor.putUserData(FILE_LEVEL_INTENTIONS, intentions)
  }

  override fun getStoredFileLevelIntentions(fileEditor: FileEditor) : List<IntentionActionWithOptions> {
    return fileEditor.getUserData(FILE_LEVEL_INTENTIONS) ?: emptyList()
  }

  override fun updateAllNotifications() {
    if (project.isDefault) {
      throw UnsupportedOperationException("Editor notifications aren't supported for default project")
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      doUpdateAllNotifications()
    }
    else {
      check(updateAllRequests.tryEmit(Unit))
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