// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

@ApiStatus.Internal
internal interface AsyncFileEditorOpener {
  suspend fun openFileImpl5(window: EditorWindow,
                            virtualFile: VirtualFile,
                            entry: HistoryEntry?,
                            options: FileEditorOpenOptions): Pair<List<FileEditor>, List<FileEditorProvider>>
}

// todo convert FileEditorManagerImpl to kotlin
open class FileEditorManagerExImpl(project: Project) : FileEditorManagerImpl(project), AsyncFileEditorOpener {
  companion object {
    private val LOG = logger<FileEditorManagerImpl>()
  }

  override suspend fun openFileImpl5(window: EditorWindow,
                                     virtualFile: VirtualFile,
                                     entry: HistoryEntry?,
                                     options: FileEditorOpenOptions): Pair<List<FileEditor>, List<FileEditorProvider>> {
    if (!ClientId.isCurrentlyUnderLocalId) {
      val clientManager = clientFileEditorManager ?: return Pair(emptyList(), emptyList())
      val result = clientManager.openFile(file = virtualFile, forceCreate = false)
      return Pair(result.map { it.fileEditor }, result.map { it.provider })
    }

    val file = getOriginalFile(virtualFile)
    var composite: EditorComposite? = if (options.isReopeningOnStartup) {
      null
    }
    else {
      withContext(Dispatchers.EDT) {
        window.getComposite(file)
      }
    }

    val newProviders: List<FileEditorProvider>?
    val builders: Array<AsyncFileEditorProvider.Builder?>?
    if (composite == null) {
      if (!canOpenFile(file)) {
        val p = EditorComposite.retrofit(null)
        return Pair(p.first.toList(), p.second.toList())
      }

      // File is not opened yet. In this case we have to create editors and select the created EditorComposite.
      newProviders = FileEditorProviderManager.getInstance().getProvidersAsync(project, file)
      builders = arrayOfNulls(newProviders.size)
      for (i in newProviders.indices) {
        try {
          val provider = newProviders[i]
          builders[i] = readAction {
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
        }
        catch (e: AssertionError) {
          LOG.error(e)
        }
      }
    }
    else {
      newProviders = null
      builders = null
    }

    withContext(Dispatchers.EDT) {
      if (!file.isValid) {
        return@withContext
      }

      // execute as part of project open process - maybe under modal progress, maybe not,
      // so, we cannot use NON_MODAL to get write-safe context, because it will lead to a deadlock
      (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
        runBulkTabChange(window.owner) {
          composite = openFileImpl4Edt(window, file, entry, options, newProviders, builders)
        }
      }
    }

    val p = EditorComposite.retrofit(composite ?: return Pair(emptyList(), emptyList()))
    return Pair(p.first.toList(), p.second.toList())
  }
}