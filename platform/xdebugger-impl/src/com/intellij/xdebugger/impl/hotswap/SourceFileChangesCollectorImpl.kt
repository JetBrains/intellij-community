// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.history.LocalHistory
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.SearchScope
import it.unimi.dsi.fastutil.objects.Object2IntFunction
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
fun interface SourceFileChangeFilter<T> {
  suspend fun isApplicable(change: T): Boolean
}

/**
 * Default implementation of [SourceFileChangesCollector], that track modification of all available documents.
 */
@ApiStatus.Internal
class SourceFileChangesCollectorImpl(
  private val coroutineScope: CoroutineScope,
  private val listener: SourceFileChangesListener,
  private vararg val filters: SourceFileChangeFilter<VirtualFile>,
) : SourceFileChangesCollector<VirtualFile>, Disposable {
  private val channel = Channel<Update>(Channel.UNLIMITED)

  @Volatile
  private var currentChanges: MutableSet<VirtualFile> = hashSetOf()

  @Volatile
  private var lastResetTimeStamp: Long = System.currentTimeMillis()

  @TestOnly
  internal var customLocalHistory: LocalHistory? = null

  init {
    val eventMulticaster = EditorFactory.getInstance().eventMulticaster
    eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        onDocumentChange(event.document)
      }
    }, this)
    coroutineScope.collectChanges()
  }

  override fun dispose() {
    channel.close()
  }

  override fun getChanges(): Set<VirtualFile> = currentChanges
  override fun resetChanges() {
    lastResetTimeStamp = System.currentTimeMillis()
    currentChanges = hashSetOf()
  }

  private fun onDocumentChange(document: Document) {
    coroutineScope.launch(Dispatchers.Default) {
      val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return@launch
      if (filters.any { !it.isApplicable(virtualFile) }) return@launch
      channel.send(Update(virtualFile, document))
    }
  }

  private fun CoroutineScope.collectChanges() = launch(Dispatchers.Default) {
    var timestamp = lastResetTimeStamp
    val cache = Object2IntOpenHashMap<VirtualFile>()
    fun checkCacheValidity() {
      val current = lastResetTimeStamp
      if (timestamp == current) return
      timestamp = current
      cache.clear()
    }

    for ((file, document) in channel) {
      checkCacheValidity()
      val currentChanges = currentChanges
      val contentHash = Strings.stringHashCode(document.immutableCharSequence)

      if (hasChangesSinceLastReset(file, contentHash, cache)) {
        currentChanges.add(file)
      }
      else {
        currentChanges.remove(file)
      }

      val isEmpty = currentChanges.isEmpty()
      if (isEmpty) listener.onChangesCanceled() else listener.onNewChanges()
    }
  }

  private fun hasChangesSinceLastReset(file: VirtualFile, contentHash: Int, cache: Object2IntOpenHashMap<VirtualFile>): Boolean {
    val oldHash = cache.computeIfAbsent(file, Object2IntFunction { getContentHashBeforeLastReset(file) })
    if (oldHash == -1) return true
    return contentHash != oldHash
  }

  private fun getContentHashBeforeLastReset(file: VirtualFile): Int {
    val localHistory = customLocalHistory ?: LocalHistory.getInstance()
    val bytes = localHistory.getByteContent(file) { timestamp -> timestamp < lastResetTimeStamp } ?: return -1
    val content = LoadTextUtil.getTextByBinaryPresentation(bytes, file, false, false)
    return Strings.stringHashCode(content)
  }
}

private data class Update(val file: VirtualFile, val contentHash: Document)

@ApiStatus.Internal
class SearchScopeFilter(private val searchScope: SearchScope) : SourceFileChangeFilter<VirtualFile> {
  override suspend fun isApplicable(change: VirtualFile): Boolean {
    return readAction { searchScope.contains(change) }
  }
}
