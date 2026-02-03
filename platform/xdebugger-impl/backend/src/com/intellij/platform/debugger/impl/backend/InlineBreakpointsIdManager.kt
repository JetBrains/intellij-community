// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XInlineBreakpointVariantId
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the lifecycle of inline breakpoint variant IDs by binding to the lifecycle of editors that might contain the variant.
 */
@Service(Service.Level.PROJECT)
internal class InlineBreakpointsIdManager(private val project: Project, private val cs: CoroutineScope) {
  // only accessed on EDT
  private val trackedDocuments = hashMapOf<Document, MutableSet<InlineBreakpointVariantModel>>()

  init {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) = editorLinksChanged(event.editor, 1)
      override fun editorReleased(event: EditorFactoryEvent) = editorLinksChanged(event.editor, -1)

      fun editorLinksChanged(editor: Editor, delta: Int) {
        val models = trackedDocuments[editor.document] ?: return
        val toRemove = models.filter { it.shouldBeDisposedOnEditorCountChange(delta) }.toSet()
        models.removeAll(toRemove)
        if (models.isEmpty()) {
          trackedDocuments.remove(editor.document)
        }
      }
    }, project)
  }

  suspend fun createId(variant: XLineBreakpointType<*>.XLineBreakpointVariant, document: Document): XInlineBreakpointVariantId = withContext(Dispatchers.EDT) {
    val initialEditorsCount = EditorFactory.getInstance().editors(document, project).count().toInt()
    val scope = cs.childScope("InlineBreakpointVariantModel")
    val model = InlineBreakpointVariantModel(variant, scope, initialEditorsCount)
    trackedDocuments.computeIfAbsent(document) { hashSetOf() }.add(model)
    storeValueGlobally(model.cs, model, InlineBreakpointVariantIdType)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): InlineBreakpointsIdManager = project.service()
  }
}

internal fun XInlineBreakpointVariantId.findValue(): XLineBreakpointType<*>.XLineBreakpointVariant? {
  return findValueById(this, InlineBreakpointVariantIdType)?.variant
}

private class InlineBreakpointVariantModel(
  val variant: XLineBreakpointType<*>.XLineBreakpointVariant,
  val cs: CoroutineScope,
  initialEditorCount: Int,
) {
  private val connectedEditorsCount = AtomicInteger(initialEditorCount)

  fun shouldBeDisposedOnEditorCountChange(delta: Int): Boolean {
    val newCount = connectedEditorsCount.addAndGet(delta)
    if (newCount <= 0) {
      cs.cancel()
      return true
    }
    return false
  }
}

private object InlineBreakpointVariantIdType : BackendValueIdType<XInlineBreakpointVariantId, InlineBreakpointVariantModel>(::XInlineBreakpointVariantId)
