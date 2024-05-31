// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.openapi.fileEditor.impl

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
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
  file: VirtualFile,
  project: Project,
  fileEntry: FileEntry? = null,
): Flow<EditorCompositeModel> {
  return flow {
    coroutineScope {
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
        project = project,
      ).fileEditorWithProviderFlow(
        providers = createBuilders(
          providers = deferredProviders.await(),
          file = file,
          project = project,
          document = document.await(),
        ),
        file = file,
        state = null,
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
  else {
    return async(CoroutineName("editor provider resolving")) {
      val fileEditorProviderManager = serviceAsync<FileEditorProviderManager>()
      val list = fileEntry.providers.keys.mapNotNullTo(ArrayList(fileEntry.providers.size)) {
        fileEditorProviderManager.getProvider(it)
      }

      // if some provider is not found, compute without taking cache in an account
      if (fileEntry.providers.size == list.size) {
        list
      }
      else {
        fileEditorProviderManager.getProvidersAsync(project, file)
      }
    }
  }
}

internal class EditorCompositeModelManager(
  private val editorPropertyChangeListener: PropertyChangeListener,
  private val project: Project,
) {
  suspend fun fileEditorWithProviderFlow(
    providers: List<Pair<FileEditorProvider, AsyncFileEditorProvider.Builder?>>,
    file: VirtualFile,
    state: FileEntry? = null,
    flowCollector: FlowCollector<EditorCompositeModel>,
  ) {
    val editorsWithProviders = providers.mapNotNull { (provider, builder) ->
      try {
        val editor = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          builder?.build() ?: provider.createEditor(project, file)
        }
        if (editor.isValid) {
          FileEditorWithProvider(editor, provider)
        }
        else {
          val pluginDescriptor = PluginManager.getPluginByClass(provider.javaClass)
          LOG.error(PluginException("Invalid editor created by provider ${provider.javaClass.name}", pluginDescriptor?.pluginId))
          null
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
        null
      }
    }

    postProcessFileEditorWithProviderList(editorsWithProviders)
    flowCollector.emit(EditorCompositeModel(fileEditorAndProviderList = editorsWithProviders, state = state))
  }

  suspend fun fileEditorWithProviderFlow(
    editorsWithProviders: List<FileEditorWithProvider>,
    flowCollector: FlowCollector<EditorCompositeModel>,
  ) {
    postProcessFileEditorWithProviderList(editorsWithProviders)
    flowCollector.emit(EditorCompositeModel(fileEditorAndProviderList = editorsWithProviders, state = null))
  }

  private fun postProcessFileEditorWithProviderList(editorsWithProviders: List<FileEditorWithProvider>) {
    for (editorWithProvider in editorsWithProviders) {
      val editor = editorWithProvider.fileEditor
      editor.addPropertyChangeListener(editorPropertyChangeListener)
      editor.putUserData(DUMB_AWARE, DumbService.isDumbAware(editorWithProvider.provider))
    }
  }
}

internal suspend fun createBuilders(
  providers: List<FileEditorProvider>,
  file: VirtualFile,
  project: Project,
  document: Document?,
): List<Pair<FileEditorProvider, AsyncFileEditorProvider.Builder?>> {
  return coroutineScope {
    providers.map { provider ->
      async {
        if (provider is AsyncFileEditorProvider) {
          try {
            provider to provider.createEditorBuilder(project = project, file = file, document = document)
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: Throwable) {
            LOG.error(e)
            null
          }
        }
        else {
          provider to null
        }
      }
    }
  }.mapNotNull { it.getCompleted() }
}