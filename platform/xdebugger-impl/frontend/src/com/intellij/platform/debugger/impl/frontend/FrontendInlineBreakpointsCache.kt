// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.rpc.util.textRange
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeApi
import com.intellij.platform.debugger.impl.rpc.XInlineBreakpointVariantDto
import com.intellij.platform.debugger.impl.shared.InlineBreakpointsCache
import com.intellij.platform.debugger.impl.shared.proxy.InlineLightBreakpoint
import com.intellij.platform.debugger.impl.shared.proxy.InlineVariantWithMatchingBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInlineVariantProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.project.projectId
import com.intellij.xdebugger.impl.breakpoints.InlineBreakpointInlayManager
import com.intellij.xdebugger.impl.breakpoints.asInlineLightBreakpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Icon

private class DocumentCacheEntry(document: Document) {
  val lineCache = hashMapOf<Int, List<InlineVariantWithMatchingBreakpointProxy>>()
  val documentTimestamp = document.modificationStamp
}

internal class FrontendInlineBreakpointsCache(
  override val project: Project,
  private val cs: CoroutineScope,
  private val breakpointsManager: FrontendXBreakpointManager,
) : InlineBreakpointsCache {
  private val entries = WeakHashMap<Document, DocumentCacheEntry>()
  private val entriesLock = Mutex()
  private val requestCounter = AtomicLong()

  internal fun incrementRequestId() = requestCounter.incrementAndGet()

  private fun getDocumentCache(document: Document): DocumentCacheEntry? {
    assert(entriesLock.isLocked) { "This method should be called under the lock" }
    val cachedData = entries[document] ?: return null
    if (cachedData.documentTimestamp != document.modificationStamp) {
      entries.remove(document)
      return null
    }
    return cachedData
  }

  private fun getOrCreateDocumentCache(document: Document): DocumentCacheEntry {
    assert(entriesLock.isLocked) { "This method should be called under the lock" }
    val cachedData = getDocumentCache(document)
    if (cachedData != null) return cachedData
    return DocumentCacheEntry(document).also { entries[document] = it }
  }

  override fun editorReleased(editor: Editor) {
    val document = editor.document
    cs.launch(Dispatchers.EDT) {
      entriesLock.withLock {
        if (document !in entries) return@launch
        val documentAbandoned = EditorFactory.getInstance().editors(document, project).allMatch { it.isDisposed }
        if (documentAbandoned) {
          entries.remove(document)
        }
      }
    }
  }

  override suspend fun performWithVariants(
    document: Document,
    lines: Set<Int>,
    block: suspend (Map<Int, List<InlineVariantWithMatchingBreakpointProxy>>) -> Boolean,
  ) {
    if (lines.isEmpty()) {
      block(emptyMap())
      return
    }
    val cachedVariants = getCachedVariants(document, lines)
    // First, use cached variants if available
    if (cachedVariants != null && !block(cachedVariants)) {
      // the caller marked this request as outdated
      return
    }
    // Second, request variants from backend and updated the cache
    block(cacheVariantsFromBackend(document, lines))
  }

  private suspend fun cacheVariantsFromBackend(
    document: Document,
    lines: Set<Int>,
  ): Map<Int, List<InlineVariantWithMatchingBreakpointProxy>> {
    val requestId = incrementRequestId()
    // create the document cache before requesting backend to save the document stamp
    val documentCache = entriesLock.withLock {
      getOrCreateDocumentCache(document)
    }
    val beVariants = requestVariantsFromBackend(document, lines)
    entriesLock.withLock {
      val linesToRemoveFromCache = lines.toSet().minus(beVariants.keys)
      documentCache.lineCache.keys.removeAll(linesToRemoveFromCache)
      for ((line, lineVariants) in beVariants) {
        val cachedVariants = documentCache.lineCache[line]
        val variants = merge(lineVariants, cachedVariants, requestId)
        documentCache.lineCache[line] = variants
      }
      return beVariants.mapValues { (line, _) -> documentCache.lineCache[line]!! }
    }
  }

  private fun merge(
    beVariants: List<InlineVariantWithMatchingBreakpointProxy>,
    cachedVariants: List<InlineVariantWithMatchingBreakpointProxy>?,
    requestId: Long,
  ): List<InlineVariantWithMatchingBreakpointProxy> {
    if (cachedVariants == null) return beVariants
    // cached variants are out of sync, we cannot merge, so just clear cached data
    if (cachedVariants.size != beVariants.size) return beVariants
    if (cachedVariants.none { it.isPendingInstall(requestId) }) return beVariants
    return cachedVariants.zip(beVariants) { cached, be ->
      if (cached.isPendingInstall(requestId)) {
        be.copy(lightBreakpoint = cached.lightBreakpoint)
      }
      else {
        be
      }
    }
  }

  private suspend fun requestVariantsFromBackend(
    document: Document,
    lines: Set<Int>,
  ): Map<Int, List<InlineVariantWithMatchingBreakpointProxy>> {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyMap()
    val variantsOnLines = retryUntilVersionMatch(project, document) { version ->
      XBreakpointTypeApi.getInstance().computeInlineBreakpointVariants(project.projectId(), file.rpcId(), lines, version)
    }
    return variantsOnLines.associate { (line, variants) ->
      line to variants.mapNotNull { dto ->
        val (variantDto, breakpointId) = dto
        val breakpoint = breakpointId?.let { breakpointsManager.getBreakpointById(it) } as? XLineBreakpointProxy
        val variant = variantDto?.let { FrontendXLineBreakpointInlineVariantProxy(it, cs, this) }
        if (variant == null && breakpoint == null) {
          // Maybe it would be better to retry the whole computation
          return@mapNotNull null
        }
        InlineVariantWithMatchingBreakpointProxy(variant, breakpoint?.asInlineLightBreakpoint())
      }
    }
  }

  private suspend fun getCachedVariants(document: Document, lines: Set<Int>): Map<Int, List<InlineVariantWithMatchingBreakpointProxy>>? {
    return entriesLock.withLock {
      val documentCache = getDocumentCache(document) ?: return null
      lines.associateWith { line ->
        // Do not use cache if there is yet an uncached value
        var entry = documentCache.lineCache[line] ?: return null
        // replace entries with disposed breakpoints to null breakpoints
        if (entry.any { it.isBreakpointDisposed() }) {
          entry = entry.mapNotNull {
            if (it.isBreakpointDisposed()) {
              if (it.variant == null) null else it.copy(lightBreakpoint = null)
            }
            else {
              it
            }
          }
          documentCache.lineCache[line] = entry
        }
        entry
      }
    }
  }

  suspend fun replaceVariantWithBreakpoint(variant: XLineBreakpointInlineVariantProxy, document: Document, line: Int, breakpoint: InlineLightBreakpoint) {
    entriesLock.withLock {
      val documentCache = getDocumentCache(document) ?: return
      // Do nothing if no data in the cache
      val variants = documentCache.lineCache[line] ?: return
      val variantEntry = variants.firstOrNull { it.variant === variant } ?: return
      // If a breakpoint has been already installed, the temporary breakpoint is outdated
      if (variantEntry.lightBreakpoint != null) return
      // Replace a null breakpoint with a temporary one
      documentCache.lineCache[line] = variants.map {
        if (it === variantEntry) {
          it.copy(lightBreakpoint = breakpoint)
        }
        else {
          it
        }
      }
    }
  }
}

private fun InlineVariantWithMatchingBreakpointProxy.isPendingInstall(requestId: Long): Boolean {
  val lightBreakpoint = lightBreakpoint as? FrontendInlineLightBreakpoint ?: return false
  return lightBreakpoint.unlockedBeforeRequestId > requestId
}

private fun InlineVariantWithMatchingBreakpointProxy.isBreakpointDisposed(): Boolean = lightBreakpoint?.breakpointProxy?.isDisposed() == true

private class FrontendXLineBreakpointInlineVariantProxy(
  private val variant: XInlineBreakpointVariantDto,
  private val cs: CoroutineScope,
  private val cache: FrontendInlineBreakpointsCache,
) : XLineBreakpointInlineVariantProxy {
  override val highlightRange: TextRange?
    get() = variant.highlightRange?.textRange()
  override val icon: Icon
    get() = variant.icon.icon()
  override val tooltipDescription: String
    get() = variant.tooltipDescription

  override fun createBreakpoint(project: Project, file: VirtualFile, document: Document, line: Int) {
    cs.launch {
      val lightBreakpoint = FrontendInlineLightBreakpoint(this@FrontendXLineBreakpointInlineVariantProxy)
      try {
        cache.replaceVariantWithBreakpoint(this@FrontendXLineBreakpointInlineVariantProxy, document, line, lightBreakpoint)
        InlineBreakpointInlayManager.getInstance(project).redrawLine(document, line)
        XBreakpointTypeApi.getInstance().createVariantBreakpoint(project.projectId(), file.rpcId(), line, variant.id)
      }
      finally {
        lightBreakpoint.unlockedBeforeRequestId = cache.incrementRequestId()
      }
    }
  }

  override fun toString(): String {
    val range = highlightRange?.toString() ?: "[full line]"
    return "FrontendXLineBreakpointInlineVariantProxy($range)"
  }
}

private class FrontendInlineLightBreakpoint(variant: FrontendXLineBreakpointInlineVariantProxy) : InlineLightBreakpoint {
  @Volatile
  var unlockedBeforeRequestId = Long.MAX_VALUE
  override val highlightRange: XLineBreakpointHighlighterRange = XLineBreakpointHighlighterRange.Available(variant.highlightRange)
  override val icon: Icon = variant.icon
  override val tooltipDescription: String = variant.tooltipDescription
  override val breakpointProxy: XBreakpointProxy? = null
}
