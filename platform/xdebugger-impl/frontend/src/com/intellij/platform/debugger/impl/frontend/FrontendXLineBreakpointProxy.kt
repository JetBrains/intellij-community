// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.util.TextRangeDto
import com.intellij.ide.rpc.util.textRange
import com.intellij.ide.rpc.util.toRpc
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.markup.GutterDraggableObject
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.platform.debugger.impl.frontend.util.SequentialRpcRequestsExecutor
import com.intellij.platform.debugger.impl.rpc.XBreakpointApi
import com.intellij.platform.debugger.impl.rpc.XBreakpointDto
import com.intellij.platform.debugger.impl.rpc.XLineBreakpointInfo
import com.intellij.platform.debugger.impl.rpc.patchVersion
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointAttachment
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointAttachmentNotifier
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import com.intellij.xdebugger.impl.breakpoints.BreakpointDraggableObjectFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

internal enum class RegistrationStatus {
  NOT_STARTED, IN_PROGRESS, REGISTERED, DEREGISTERED
}

private suspend fun XLineBreakpointProxy.document(): Document? {
  return readAction { getFile()?.findDocument() }
}

/**
 * Retries [request] until the backend accepts the document version.
 * The version and the tracked highlight range come from one read action, so the range offsets belong to the reported version.
 */
private suspend fun retryUntilVersionMatchWithRange(
  breakpoint: FrontendXLineBreakpointProxy,
  request: suspend (DocumentPatchVersion?, TextRangeDto?) -> Boolean,
) {
  val document = breakpoint.document()
  while (true) {
    val (version, range) = readAction {
      document?.patchVersion(breakpoint.project) to breakpoint.trackedHighlightRange()?.toRpc()
    }
    if (request(version, range)) return
  }
}

private sealed interface BreakpointRequest {
  val requestId: Long
  suspend fun sendRequest(breakpoint: FrontendXLineBreakpointProxy, requestId: Long)

  class SetLine(override val requestId: Long, val line: Int, private val redraw: () -> Unit) : BreakpointRequest {
    override suspend fun sendRequest(breakpoint: FrontendXLineBreakpointProxy, requestId: Long) {
      retryUntilVersionMatchWithRange(breakpoint) { version, range ->
        XBreakpointApi.getInstance().setLine(breakpoint.id, requestId, line, version, range)
      }
      redraw()
    }
  }

  class UpdatePosition(override val requestId: Long) : BreakpointRequest {
    override suspend fun sendRequest(breakpoint: FrontendXLineBreakpointProxy, requestId: Long) {
      retryUntilVersionMatchWithRange(breakpoint) { version, range ->
        XBreakpointApi.getInstance().updatePosition(breakpoint.id, requestId, version, range)
      }
    }
  }
}

private class RequestsDebouncer(
  cs: CoroutineScope,
  private val breakpoint: FrontendXLineBreakpointProxy,
  private val sequentialExecutor: SequentialRpcRequestsExecutor,
) {
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
        val request = sequentialExecutor.submit {
          it.sendRequest(breakpoint, it.requestId)
        }
        try {
          request.await()
        }
        finally {
          request.cancel()
        }
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
  creationTrigger: XBreakpointCreationTrigger,
) : FrontendXBreakpointProxy(project, parentCs, dto, type, manager.breakpointRequestCounter),
    XLineBreakpointProxy,
    XBreakpointAttachmentNotifier,
    FrontendXLineBreakpointVisualizable {
  private val debouncer = RequestsDebouncer(cs, this, sequentialExecutor)

  private var lineSourcePosition: XSourcePosition? = null

  override val visualRepresentation = XBreakpointVisualRepresentation(cs, this)
  private val breakpointDraggableObjectFactory = BreakpointDraggableObjectFactory(manager, this)

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
  val attachments: List<XBreakpointAttachment> =
    FrontendXLineBreakpointAttachmentProvider.createAttachments(this, attachmentScope, creationTrigger)

  override fun notifyBreakpointAttachments() {
    for (attachment in attachments) {
      attachment.breakpointChanged()
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

  override fun getPlacement(): XLineBreakpointVerticalPlacement {
    return lineBreakpointInfo.placement
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

  override fun setPlacement(placement: XLineBreakpointVerticalPlacement) {
    updateLineBreakpointStateIfNeeded(
      newValue = placement,
      getter = { it.placement },
      copy = { it.copy(placement = placement) },
      afterStateChanged = {
        visualRepresentation.removeHighlighter()
      },
    ) { requestId ->
      XBreakpointApi.getInstance().setPlacement(id, requestId, placement)
      visualRepresentation.redrawInlineInlays(getFile(), getLine())
    }
  }

  override fun getHighlightRange(): XLineBreakpointHighlighterRange {
    val range = lineBreakpointInfo.highlightingRange
    if (range == UNAVAILABLE_RANGE) return XLineBreakpointHighlighterRange.Unavailable
    return XLineBreakpointHighlighterRange.Available(range?.textRange())
  }

  fun updatePosition() {
    val highlighter: RangeMarker? = visualRepresentation.rangeMarker
    if (highlighter != null && highlighter.isValid()) {
      lineSourcePosition = null // reset the source position even if the line number has not changed, as the offset may be cached inside
      positionChanged(highlighter.getDocument().getLineNumber(highlighter.getStartOffset()), visualLineMightBeChanged = false)
    }
  }

  fun getHighlighter(): RangeHighlighter? {
    return visualRepresentation.highlighter
  }

  /**
   * The range the highlighter tracks across document edits, or null for a whole-line highlighter.
   */
  internal fun trackedHighlightRange(): TextRange? {
    val highlighter = visualRepresentation.rangeMarker as? RangeHighlighter ?: return null
    if (!highlighter.isValid || highlighter.targetArea != HighlighterTargetArea.EXACT_RANGE) return null
    return highlighter.textRange
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

  fun createBreakpointDraggableObject(): GutterDraggableObject {
    return breakpointDraggableObjectFactory.create()
  }

  override fun getGutterIconRenderer(): GutterIconRenderer {
    return visualRepresentation.highlighter?.gutterIconRenderer ?: super.getGutterIconRenderer()
  }

  override fun updateIcon() {
    // TODO IJPL-185322 should we cache icon like in Monolith?
  }

  override fun toString(): String {
    return this::class.simpleName + "(id=$id, type=${type.id}, line=${getLine()}, file=${getFileUrl()})"
  }

  @TestOnly
  internal fun installRangeMarkerForTest(rangeMarker: RangeMarker) {
    visualRepresentation.installRangeMarkerForTest(rangeMarker)
  }
}

private val UNAVAILABLE_RANGE = TextRangeDto(-1, -1)
private fun XLineBreakpointInfo.invalidateHighlightingRangeOrNull() = if (highlightingRange == null) null else UNAVAILABLE_RANGE

@ApiStatus.Internal
@TestOnly
fun installRangeMarkerForTest(breakpoint: XLineBreakpointProxy, rangeMarker: RangeMarker) {
  (breakpoint as FrontendXLineBreakpointProxy).installRangeMarkerForTest(rangeMarker)
}
