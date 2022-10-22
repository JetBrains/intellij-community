// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("PrivatePropertyName", "ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

private val LOG: Logger = logger<FileEditorManagerImpl>()

// todo convert FileEditorManagerImpl to kotlin
open class FileEditorManagerExImpl(project: Project) : FileEditorManagerImpl(project) {
  internal suspend fun openFileOnStartup(window: EditorWindow,
                                         virtualFile: VirtualFile,
                                         entry: HistoryEntry?,
                                         options: FileEditorOpenOptions) {
    assert(options.isReopeningOnStartup)

    if (!ClientId.isCurrentlyUnderLocalId) {
      (clientFileEditorManager ?: return).openFile(file = virtualFile, forceCreate = false)
      return
    }

    val file = getOriginalFile(virtualFile)
    val newProviders = FileEditorProviderManager.getInstance().getProvidersAsync(project, file)
    if (!canOpenFile(file, newProviders)) {
      return
    }

    // file is not opened yet - in this case we have to create editors and select the created EditorComposite.

    val builders = ArrayList<AsyncFileEditorProvider.Builder?>(newProviders.size)
    for (provider in newProviders) {
      val builder = try {
        readAction {
          if (!file.isValid) {
            return@readAction null
          }

          LOG.assertTrue(provider.accept(project, file), "Provider $provider doesn't accept file $file")
          if (provider is AsyncFileEditorProvider) provider.createEditorAsync(project, file) else null
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error(e)
        null
      }
      catch (e: AssertionError) {
        LOG.error(e)
        null
      }
      builders.add(builder)
    }

    withContext(Dispatchers.EDT) {
      if (!file.isValid) {
        return@withContext
      }

      val splitters = window.owner
      splitters.insideChange++
      try {
        doOpenInEdtImpl(window, file, entry, options, newProviders, builders)
      }
      finally {
        splitters.insideChange--
      }
    }
  }
}