// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.util.TextRangeDto
import com.intellij.ide.rpc.util.textRange
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.platform.debugger.impl.rpc.XBreakpointApi
import com.intellij.platform.debugger.impl.rpc.XBreakpointDto
import com.intellij.platform.debugger.impl.rpc.XLineBreakpointInfo
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointAttachment
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.breakpoints.XBreakpointVisualRepresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

internal enum class RegistrationStatus {
  NOT_STARTED, IN_PROGRESS, REGISTERED, DEREGISTERED
}

private suspend fun XLineBreakpointProxy.document(): Document? {
  return readAction { getFile()?.findDocument() }
}

private suspend fun retryUntilVersionMatchBool(project: Project, document: Document?, request: suspend (DocumentPatchVersion?) -> Boolean) {
  retryUntilVersionMatch(project, document) { if (request(it)) true else null }
}

private sealed interface BreakpointRequest {
  val requestId: Long
  suspend fun sendRequest(breakpoint: XLineBreakpointProxy, requestId: Long)

  class SetLine(override val requestId: Long, val line: Int, private val redraw: () -> Unit) : BreakpointRequest {
    override suspend fun sendRequest(breakpoint: XLineBreakpointProxy, requestId: Long) {
      retryUntilVersionMatchBool(breakpoint.project, breakpoint.document()) { version ->
        XBreakpointApi.getInstance().setLine(breakpoint.id, requestId, line, version)
      }
      redraw()
    }
  }

  class UpdatePosition(override val requestId: Long) : BreakpointRequest {
    override suspend fun sendRequest(breakpoint: XLineBreakpointProxy, requestId: Long) {
      retryUntilVersionMatchBool(breakpoint.project, breakpoint.document()) { version ->
        XBreakpointApi.getInstance().updatePosition(breakpoint.id, requestId, version)
      }
    }
  }
}

private class RequestsDebouncer(cs: CoroutineScope, private val breakpoint: XLineBreakpointProxy) {
  private val debouncedRequests = Channel<BreakpointRequest>(Channel.UNLIMITED)

  init {
    cs.launch {
      val flows = hashMapOf<Class<out BreakpointRequest>, Channel<BreakpointRequest>>()
      for (request in debouncedRequests) {
        val flow = flows.getOrPut(request::class.java) { createRequestTypeFlow() }
        flow.send(request)
      }
    }
  }

  private fun CoroutineScope.createRequestTypeFlow(): Channel<BreakpointRequest> {
    val channel = Channel<BreakpointRequest>()
    launch {
      channel.consumeAsFlow().collectLatest {
        it.sendRequest(breakpoint, it.requestId)
      }
    }
    return channel
  }

  fun sendRequest(request: BreakpointRequest) {
    debouncedRequests.trySend(request)
  }
}

internal class FrontendXLineBreakpointProxy(
  project: Project,
  parentCs: CoroutineScope,
  dto: XBreakpointDto,
  override val type: XLineBreakpointTypeProxy,
  manager: FrontendXBreakpointManager,
) : FrontendXBreakpointProxy(project, parentCs, dto, type, manager.breakpointRequestCounter), XLineBreakpointProxy {
  private val debouncer = RequestsDebouncer(cs, this)

  private var lineSourcePosition: XSourcePosition? = null

  private val visualRepresentation = XBreakpointVisualRepresentation(cs, this, SplitDebuggerMode.isSplitDebugger(), manager)

  private val lineBreakpointInfo: XLineBreakpointInfo
    get() = currentState.lineBreakpointInfo!!

  internal val registrationInLineManagerStatus = AtomicReference(RegistrationStatus.NOT_STARTED)

  /**
   * Coroutine scope for attachments, cancelled when the breakpoint is disposed.
   */
  private val attachmentScope: CoroutineScope = cs.childScope("attachments")

  /**
   * Attachments created by [FrontendXLineBreakpointAttachmentProvider] extensions.
   * Attachments are notified when the breakpoint state changes.
   */
  override val attachments: List<XBreakpointAttachment> =
    FrontendXLineBreakpointAttachmentProvider.createAttachments(this, attachmentScope)

  init {
    attachmentScope.launch(Dispatchers.EDT) {
      for (attachment in attachments) {
        attachment.breakpointChanged()
      }
    }
  }

  override fun isTemporary(): Boolean {
    return lineBreakpointInfo.isTemporary
  }

  override fun setTemporary(isTemporary: Boolean) {
    updateLineBreakpointStateIfNeeded(newValue = isTemporary,
                                      getter = { it.isTemporary },
                                      copy = { it.copy(isTemporary = isTemporary) }) { requestId ->
      XBreakpointApi.getInstance().setTemporary(id, requestId, isTemporary)
    }
  }

  override fun getSourcePosition(): XSourcePosition? {
    if (lineSourcePosition != null) {
      return lineSourcePosition
    }
    lineSourcePosition = super.getSourcePosition()
    if (lineSourcePosition == null) {
      lineSourcePosition = XDebuggerUtil.getInstance().createPosition(getFile(), getLine())
    }
    return lineSourcePosition
  }


  override fun getFile(): VirtualFile? {
    return lineBreakpointInfo.file?.virtualFile()
  }

  override fun getFileUrl(): String {
    return lineBreakpointInfo.fileUrl
  }

  override fun getLine(): Int {
    return lineBreakpointInfo.line
  }

  override fun setFileUrl(url: String) {
    val oldFile = getFile()
    updateLineBreakpointStateIfNeeded(
      newValue = url,
      getter = { it.fileUrl },
      copy = { it.copy(fileUrl = url) },
      afterStateChanged = {
        lineSourcePosition = null
        visualRepresentation.removeHighlighter()
      }) { requestId ->
      XBreakpointApi.getInstance().setFileUrl(id, requestId, url)
      visualRepresentation.redrawInlineInlays(oldFile, getLine())
      visualRepresentation.redrawInlineInlays(getFile(), getLine())
    }
  }

  override fun setLine(line: Int) {
    return positionChanged(line, visualLineMightBeChanged = true)
  }

  private fun positionChanged(line: Int, visualLineMightBeChanged: Boolean) {
    val oldLine = getLine()
    if (oldLine != line) {
      // TODO IJPL-185322 support type.lineShouldBeChanged()
      updateLineBreakpointStateIfNeeded(
        newValue = line to lineBreakpointInfo.invalidateHighlightingRangeOrNull(),
        getter = { it.line to it.highlightingRange },
        copy = { it.copy(line = line, highlightingRange = it.invalidateHighlightingRangeOrNull()) },
        afterStateChanged = {
          lineSourcePosition = null
          if (visualLineMightBeChanged) {
            visualRepresentation.removeHighlighter()
          }
        }
      ) { requestId ->
        debouncer.sendRequest(BreakpointRequest.SetLine(requestId, line) {
          // We try to redraw inlays every time,
          // due to lack of synchronization between inlay redrawing and breakpoint changes.
          visualRepresentation.redrawInlineInlays(getFile(), oldLine)
          visualRepresentation.redrawInlineInlays(getFile(), line)
        })
      }
    }
    else {
      // We should always notify the backend the position might be changed
      updateLineBreakpointStateIfNeeded(
        newValue = lineBreakpointInfo.invalidateHighlightingRangeOrNull(),
        getter = { it.highlightingRange },
        copy = { it.copy(highlightingRange = it.invalidateHighlightingRangeOrNull()) },
        afterStateChanged = {
          // offset in file might change, pass reset to backend
          lineSourcePosition = null
        },
        forceRequestWithoutUpdate = true,
      ) { requestId ->
        debouncer.sendRequest(BreakpointRequest.UpdatePosition(requestId))
      }
    }
  }

  override fun getHighlightRange(): XLineBreakpointHighlighterRange {
    val range = lineBreakpointInfo.highlightingRange
    if (range == UNAVAILABLE_RANGE) return XLineBreakpointHighlighterRange.Unavailable
    return XLineBreakpointHighlighterRange.Available(range?.textRange())
  }

  override fun updatePosition() {
    // everything is done in fastUpdatePosition
  }

  override fun fastUpdatePosition() {
    val highlighter: RangeMarker? = visualRepresentation.rangeMarker
    if (highlighter != null && highlighter.isValid()) {
      lineSourcePosition = null // reset the source position even if the line number has not changed, as the offset may be cached inside
      positionChanged(highlighter.getDocument().getLineNumber(highlighter.getStartOffset()), visualLineMightBeChanged = false)
    }
  }

  override fun getHighlighter(): RangeHighlighter? {
    return visualRepresentation.highlighter
  }

  override fun doUpdateUI(callOnUpdate: () -> Unit) {
    visualRepresentation.doUpdateUI(callOnUpdate)
  }

  override fun getGutterIconRenderer(): GutterIconRenderer? {
    return visualRepresentation.highlighter?.gutterIconRenderer
  }

  private fun <T> updateLineBreakpointStateIfNeeded(
    newValue: T,
    getter: (XLineBreakpointInfo) -> T,
    copy: (XLineBreakpointInfo) -> XLineBreakpointInfo,
    afterStateChanged: () -> Unit = {},
    forceRequestWithoutUpdate: Boolean = false,
    sendRequest: suspend (Long) -> Unit,
  ) {
    return updateStateIfNeeded(newValue = newValue,
                               getter = { state -> getter(state.lineBreakpointInfo!!) },
                               copy = { state -> state.copy(lineBreakpointInfo = copy(state.lineBreakpointInfo!!)) },
                               afterStateChanged = afterStateChanged,
                               forceRequestWithoutUpdate = forceRequestWithoutUpdate) { requestId ->
      sendRequest(requestId)
    }
  }

  override fun createBreakpointDraggableObject(): GutterDraggableObject {
    return visualRepresentation.createBreakpointDraggableObject()
  }

  override fun updateIcon() {
    // TODO IJPL-185322 should we cache icon like in Monolith?
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, type=${type.id}, line=${getLine()}, file=${getFileUrl()})"
  }
}

private val UNAVAILABLE_RANGE = TextRangeDto(-1, -1)
private fun XLineBreakpointInfo.invalidateHighlightingRangeOrNull() = if (highlightingRange == null) null else UNAVAILABLE_RANGE