// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.SearchScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
fun interface SourceFileChangeFilter<T> {
  suspend fun isApplicable(change: T): Boolean
}

@ApiStatus.Internal
class SourceFileChangesCollectorImpl(
  private val coroutineScope: CoroutineScope,
  private val listener: SourceFileChangesListener<VirtualFile>,
  vararg val filters: SourceFileChangeFilter<VirtualFile>,
) : SourceFileChangesCollector<VirtualFile>, Disposable.Default {
  private val currentChanges = AtomicReference(mutableSetOf<VirtualFile>())

  init {
    val eventMulticaster = EditorFactory.getInstance().eventMulticaster
    eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        onDocumentChange(event.document)
      }
    }, this)
  }

  override fun getChanges(): Set<VirtualFile> = currentChanges.get()
  override fun resetChanges() {
    currentChanges.set(mutableSetOf())
  }

  private fun onDocumentChange(document: Document) {
    coroutineScope.launch(Dispatchers.Default) {
      val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return@launch
      if (filters.any { !it.isApplicable(virtualFile) }) return@launch
      currentChanges.get().add(virtualFile)
      listener.onFileChange(virtualFile)
    }
  }
}

@ApiStatus.Internal
class SearchScopeFilter(private val searchScope: SearchScope) : SourceFileChangeFilter<VirtualFile> {
  override suspend fun isApplicable(change: VirtualFile): Boolean {
    return readAction { searchScope.contains(change) }
  }
}
