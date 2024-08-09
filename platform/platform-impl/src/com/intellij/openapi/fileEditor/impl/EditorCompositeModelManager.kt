// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.Companion.DUMB_AWARE
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.fileEditor.FileEntry
import com.intellij.platform.ide.ideFingerprint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import java.beans.PropertyChangeListener
import kotlin.coroutines.cancellation.CancellationException

private val LOG: Logger
  get() = logger<EditorCompositeModelManager>()

internal fun createEditorCompositeModel(
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
  if (fileEntry == null || fileEntry.ideFingerprint != ideFingerprint()) {
    return async(CoroutineName("editor provider computing")) {
      serviceAsync<FileEditorProviderManager>().getProvidersAsync(project, file)
    }
  }

  return async(CoroutineName("editor provider resolving")) {
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
    val editorsWithProviders = coroutineScope {
      providers.map { provider ->
        async(ModalityState.any().asContextElement()) {
          try {
            val editor = if (provider is AsyncFileEditorProvider) {
              provider.createFileEditor(
                project = project,
                file = file,
                document = document,
                editorCoroutineScope = editorCoroutineScope,
              )
            }
            else {
              withContext(Dispatchers.EDT) {
                writeIntentReadAction {
                  provider.createEditor(project, file)
                }
              }
            }

            FileEditorWithProvider(editor, provider)
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
    flowCollector.emit(EditorCompositeModel(fileEditorAndProviderList = editorsWithProviders, state = state))
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
      val editor = editorWithProvider.fileEditor
      editor.addPropertyChangeListener(editorPropertyChangeListener)
      editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(editorWithProvider.provider))
    }
  }
}