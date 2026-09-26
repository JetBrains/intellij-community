// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeListener
import kotlin.coroutines.cancellation.CancellationException

private val LOG: Logger
  get() = logger<EditorCompositeModelManager>()

@ApiStatus.Internal
fun createEditorCompositeModel(
  editorPropertyChangeListener: PropertyChangeListener,
  fileProvider: suspend () -> VirtualFile,
  project: Project,
  fileEntry: FileEntry? = null,
  coroutineScope: CoroutineScope,
): Flow<EditorCompositeModel> {
  return flow {
    coroutineScope {
      val file = fileProvider()
      val document = async {
        val fileDocumentManager = serviceAsync<FileDocumentManager>()
        readAction {
          fileDocumentManager.getDocument(file)
        }
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
        document = document.await(),
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
                    writeIntentReadAction {
                      val editor = provider.createEditor(project, file)
                      createdEditors.register(editor)
                      FileEditorWithProvider(editor, provider)
                    }
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
      // The collector is not necessarily the composite: the startup path shares this flow, so `emit` can merely put the model into a
      // replay cache and return, leaving the editors with no owner. Stay around until a composite actually adopts them, so that a
      // scope cancelled in between (the tab is closed before it is ever shown) still runs the cleanup below.
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
    writeIntentReadAction {
      @Suppress("DEPRECATION")
      if (!Disposer.isDisposed(editor)) {
        Disposer.dispose(editor)
      }
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
