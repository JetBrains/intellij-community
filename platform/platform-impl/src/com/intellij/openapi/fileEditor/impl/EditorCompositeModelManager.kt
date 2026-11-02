// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.TrustedFiles
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.CreatedFileEditorSink
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.fileEditor.FileEntry
import com.intellij.platform.ide.ideFingerprint
import com.intellij.platform.util.progress.ProgressPipe
import com.intellij.platform.util.progress.createProgressPipe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeListener
import java.lang.ref.Reference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

private val LOG: Logger
  get() = logger<EditorCompositeModelManager>()

/**
 * Starts preparation before collection and delivers one model after [publicationReady] completes.
 * The source must emit one model with a [CreatedFileEditorSink], without buffering.
 * The single collector must claim the editors before it returns normally.
 */
internal fun prepareStartupEditorModel(
  source: Flow<EditorCompositeModel>,
  coroutineScope: CoroutineScope,
  publicationReady: Deferred<Unit>,
  progress: ProgressPipe = coroutineScope.createProgressPipe(),
): Flow<EditorCompositeModel> {
  val delivery = StartupEditorDelivery()

  val preparation = coroutineScope.async(CoroutineName("startup editor preparation")) {
    progress.collectProgressUpdates {
      source.collect { model ->
        delivery.completeAndAwaitClaim(model)
      }
    }
  }
  preparation.invokeOnCompletion(delivery::failPreparation)

  return flow {
    delivery.startCollection()
    try {
      progress.doUnderPreparationProgress {
        publicationReady.await()
        val model = delivery.awaitModel()
        delivery.deliver(model, preparation, this@flow)
      }
      preparation.await()
    }
    finally {
      preparation.cancel()
    }
  }
}

private class StartupEditorDelivery {
  private val modelReady = CompletableDeferred<EditorCompositeModel>()
  private val collectorStarted = AtomicBoolean()
  private val mutex = Mutex()
  private var closed = false

  fun startCollection() {
    check(collectorStarted.compareAndSet(false, true)) {
      "A startup editor model can only be collected once"
    }
  }

  suspend fun completeAndAwaitClaim(model: EditorCompositeModel) {
    try {
      val createdEditors = checkNotNull(model.createdEditors) {
        "Startup preparation requires a created editor sink"
      }
      completeModel(model)
      createdEditors.awaitClaimed()
    }
    finally {
      close()
    }
  }

  private fun completeModel(model: EditorCompositeModel) {
    check(modelReady.complete(model)) {
      "Startup preparation must produce one editor model"
    }
  }

  suspend fun awaitModel(): EditorCompositeModel = modelReady.await()

  fun failPreparation(cause: Throwable?) {
    modelReady.completeExceptionally(cause ?: IllegalStateException("Startup preparation produced no editor model"))
  }

  suspend fun deliver(model: EditorCompositeModel, preparation: Job, collector: FlowCollector<EditorCompositeModel>) {
    mutex.withLock {
      currentCoroutineContext().ensureActive()
      preparation.ensureActive()
      check(!closed) { "Startup editor delivery is closed" }
      collector.emit(model)
    }
  }

  private suspend fun close() {
    withContext(NonCancellable) {
      mutex.withLock {
        closed = true
      }
    }
  }
}

@ApiStatus.Internal
fun createEditorCompositeModel(
  editorPropertyChangeListener: PropertyChangeListener,
  fileProvider: suspend () -> VirtualFile,
  project: Project,
  fileEntry: FileEntry? = null,
  coroutineScope: CoroutineScope,
): Flow<EditorCompositeModel> {
  return createEditorCompositeModelImpl(editorPropertyChangeListener, fileProvider, project, fileEntry, coroutineScope, null)
}

internal class DocumentPreparedEditorModel(
  model: Flow<EditorCompositeModel>,
  preparation: EditorDocumentPreparation,
) : Flow<EditorCompositeModel> by model {
  val currentProgress: ProgressPipe = preparation.progress
}

internal fun createEditorCompositeModelWithPreparedDocument(
  editorPropertyChangeListener: PropertyChangeListener,
  file: VirtualFile,
  project: Project,
  coroutineScope: CoroutineScope,
  preparation: EditorDocumentPreparation,
): Flow<EditorCompositeModel> {
  return DocumentPreparedEditorModel(
    createEditorCompositeModelImpl(editorPropertyChangeListener, { file }, project, null, coroutineScope, preparation),
    preparation,
  )
}

private inline fun createEditorCompositeModelImpl(
  editorPropertyChangeListener: PropertyChangeListener,
  crossinline fileProvider: suspend () -> VirtualFile,
  project: Project,
  fileEntry: FileEntry?,
  coroutineScope: CoroutineScope,
  preparation: EditorDocumentPreparation?,
): Flow<EditorCompositeModel> {
  return flow {
    coroutineScope {
      val file = fileProvider()
      val document = if (preparation == null) {
        async {
          serviceAsync<FileDocumentManager>().getOrLoadDocumentUnderProgress(file)
        }
      }
      else {
        null
      }

      val deferredProviders = computeFileEditorProviders(
        fileEntry = fileEntry,
        project = project,
        file = file,
      )

      EditorCompositeModelManager(
        editorPropertyChangeListener = editorPropertyChangeListener,
        editorCoroutineScope = coroutineScope,
      ).fileEditorWithProviderFlow(
        providers = deferredProviders.await(),
        file = file,
        project = project,
        document = if (preparation == null) checkNotNull(document).await() else preparation.await(),
        state = fileEntry,
        flowCollector = this@flow,
      )
    }
  }
}

private fun CoroutineScope.computeFileEditorProviders(
  fileEntry: FileEntry?,
  project: Project,
  file: VirtualFile,
): Deferred<List<FileEditorProvider>> {
  // never resolve persisted provider ids for an untrusted file: a provider persisted while the file
  // (or the whole session) was trusted would bypass the untrusted-file filtering in FileEditorProviderManagerImpl
  if (fileEntry == null || fileEntry.ideFingerprint != ideFingerprint() || !TrustedFiles.isTrusted(file, project)) {
    return async {
      span("editor provider computing") {
        serviceAsync<FileEditorProviderManager>().getProvidersAsync(project, file)
      }
    }
  }

  return async {
    span("editor provider resolving") {
      val fileEditorProviderManager = serviceAsync<FileEditorProviderManager>()
      val list = fileEntry.providers.keys.mapNotNullTo(ArrayList(fileEntry.providers.size)) {
        fileEditorProviderManager.getProvider(it)
      }
      // if some provider is not found, compute without taking cache in an account
      if (fileEntry.providers.size == list.size && list.isNotEmpty()) {
        list
      }
      else {
        LOG.warn("Cannot use saved provider list (savedProviders=${fileEntry.providers}, resolvedProvider=$list)")
        fileEditorProviderManager.getProvidersAsync(project, file)
      }
    }
  }
}

internal class EditorCompositeModelManager(
  private val editorPropertyChangeListener: PropertyChangeListener,
  private val editorCoroutineScope: CoroutineScope,
) {
  suspend fun fileEditorWithProviderFlow(
    providers: List<FileEditorProvider>,
    project: Project,
    document: Document?,
    file: VirtualFile,
    state: FileEntry? = null,
    flowCollector: FlowCollector<EditorCompositeModel>,
  ) {
    // Cancellation (e.g., the file is closed while the composite is still loading) discards the result of async/withContext,
    // and such editors are not yet registered in the composite, so EditorComposite.dispose cannot release them.
    // The sink collects both what the providers return and what they create internally before suspending again.
    val createdEditors = CreatedFileEditorSink()
    try {
      val editorsWithProviders = coroutineScope {
        providers.map { provider ->
          async(ModalityState.any().asContextElement() + createdEditors) {
            try {
              span("Creating file editor for $provider") {
                if (provider is AsyncFileEditorProvider) {
                  val editor = provider.createFileEditor(
                    project = project,
                    file = file,
                    document = document,
                    editorCoroutineScope = editorCoroutineScope,
                  )
                  // register for cleanup immediately - any later suspension point may discard the result on cancellation.
                  // What a provider creates internally is registered by the provider itself, see AsyncFileEditorProvider.createFileEditor.
                  createdEditors.register(editor)
                  FileEditorWithProvider(editor, provider)
                }
                else {
                  withContext(Dispatchers.EDT) {
                    val editor = provider.createEditor(project, file)
                    createdEditors.register(editor)
                    FileEditorWithProvider(editor, provider)
                  }
                }
              }
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (e: Throwable) {
              val pluginDescriptor = PluginManager.getPluginByClass(provider.javaClass)
              LOG.error(PluginException("Cannot create editor by provider ${provider.javaClass.name}", e, pluginDescriptor?.pluginId))
              null
            }
          }
        }
      }.mapNotNull { it.getCompleted() }

      postProcessFileEditorWithProviderList(editorsWithProviders)
      flowCollector.emit(EditorCompositeModel(
        fileEditorAndProviderList = editorsWithProviders,
        state = state,
        createdEditors = createdEditors,
      ))
      createdEditors.awaitClaimed()
    }
    catch (e: Throwable) {
      // Whatever is still in the sink has no owner: a composite that adopted the editors has claimed and thereby emptied it, so this
      // releases exactly the abandoned ones and never an editor a live composite is using. Catching Throwable and not only
      // cancellation matters because postProcess and the collector both run plugin code that can throw.
      for (editor in createdEditors.toList()) {
        disposeAbandonedFileEditor(editor)
      }
      throw e
    }
    finally {
      Reference.reachabilityFence(document)
    }
  }

  fun blockingFileEditorWithProviderFlow(
    editorsWithProviders: List<FileEditorWithProvider>,
  ): Flow<EditorCompositeModel> {
    postProcessFileEditorWithProviderList(editorsWithProviders)
    return PrecomputedFlow(
      model = EditorCompositeModel(fileEditorAndProviderList = editorsWithProviders, state = null),
      fireFileOpened = true,
    )
  }

  private fun postProcessFileEditorWithProviderList(editorsWithProviders: List<FileEditorWithProvider>) {
    for (editorWithProvider in editorsWithProviders) {
      postProcessFileEditorWithProvider(editorWithProvider, editorPropertyChangeListener)
    }
  }
}

/**
 * Disposes a file editor that was created for a composite whose opening got cancelled before the editor was registered
 * in the composite (e.g., the file was closed while still loading) - otherwise the backing `EditorImpl` is never released.
 */
@ApiStatus.Internal
suspend fun disposeAbandonedFileEditor(editor: FileEditor) {
  withContext(NonCancellable + Dispatchers.EDT + ModalityState.any().asContextElement()) {
    @Suppress("DEPRECATION")
    if (!Disposer.isDisposed(editor)) {
      Disposer.dispose(editor)
    }
  }
}

internal fun postProcessFileEditorWithProvider(
  editorWithProvider: FileEditorWithProvider,
  editorPropertyChangeListener: PropertyChangeListener,
) {
  val editor = editorWithProvider.fileEditor
  editor.addPropertyChangeListener(editorPropertyChangeListener)
  editor.putUserData(FileEditorManagerKeys.DUMB_AWARE, DumbService.isDumbAware(editorWithProvider.provider))
}
