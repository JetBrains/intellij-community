// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xdebugger.breakpoints.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val LOG = Logger.getInstance(BreakpointVariantPriorityTracker::class.java)
private val TIMEOUT = 10.seconds

private enum class EventKind {
  BREAKPOINT_ADDED,
  BREAKPOINT_REMOVED,
}

private data class Event(val breakpoint: XLineBreakpoint<*>,
                         val fileUrl: String, val line: Int,
                         val kind: EventKind, val time: Long)

internal class BreakpointVariantPriorityTracker(private val coroutineScope: CoroutineScope)
  : XBreakpointListener<XBreakpoint<*>> {

  private val events = ArrayDeque<Event>()
  private val cleanUpRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private fun isEnabled(): Boolean =
    ApplicationManager.getApplication().let { it.isInternal && !it.isUnitTestMode }

  init {
    coroutineScope.launch {
      @OptIn(FlowPreview::class)
      cleanUpRequests
        .debounce(TIMEOUT * 2)
        .collectLatest {
          cleanUpEvents()
        }
    }
  }

  override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
    addEvent(breakpoint, EventKind.BREAKPOINT_ADDED)
  }

  override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
    addEvent(breakpoint, EventKind.BREAKPOINT_REMOVED)
  }

  override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
    if (!breakpoint.isEnabled) {
      // Treat breakpoint disabling as removal.
      breakpointRemoved(breakpoint)
    }
  }

  private fun addEvent(breakpoint: XBreakpoint<*>, kind: EventKind) {
    if (!isEnabled()) return

    val lineBreakpoint = breakpoint as? XLineBreakpoint<*> ?: return

    val fileUrl = lineBreakpoint.fileUrl
    val line = lineBreakpoint.line

    val event = Event(lineBreakpoint, fileUrl, line, kind, System.currentTimeMillis())
    synchronized(events) {
      events.add(event)
    }
    cleanUpRequests.tryEmit(Unit)

    coroutineScope.launch {
      checkPutOfNonDefaultBreakpointVariant(event)
    }
  }

  private fun cleanUpEvents() {
    val timeLowLimit = System.currentTimeMillis() - TIMEOUT.inWholeMilliseconds
    synchronized(events) {
      events.removeIf { it.time < timeLowLimit }
    }
  }

  private suspend fun checkPutOfNonDefaultBreakpointVariant(event: Event) {
    val timeLowLimit = event.time - TIMEOUT.inWholeMilliseconds
    val fileUrl = event.fileUrl
    val line = event.line
    val lineEvents =
      synchronized(events) {
        events
          .filter {
            it.fileUrl == fileUrl && it.line == line &&
            timeLowLimit <= it.time && it.time <= event.time
          }
          .dropLastWhile { it != event } // those events will be considered later
          .takeLast(3)
      }

    // We are interested only in case when user added breakpoint, added one more and removed the first one.
    // Or added first, disabled first and added second.

    if (lineEvents.size != 3 ||
        lineEvents[0].kind != EventKind.BREAKPOINT_ADDED) {
      return
    }
    val defaultBreakpoint = lineEvents[0].breakpoint

    val expectedBreakpoint =
      when (lineEvents[2].kind) {
        EventKind.BREAKPOINT_REMOVED -> {
          // added default, added new one, removed default
          if (lineEvents[1].kind != EventKind.BREAKPOINT_ADDED ||
              lineEvents[2].breakpoint != defaultBreakpoint) {
            return
          }
          lineEvents[1].breakpoint
        }

        EventKind.BREAKPOINT_ADDED -> {
          // added default, removed (disabled) default and added new one
          if (lineEvents[1].kind != EventKind.BREAKPOINT_REMOVED ||
              lineEvents[1].breakpoint != defaultBreakpoint) {
            return
          }
          lineEvents[2].breakpoint
        }
      }

    readAction {
      if (lookSimilar(defaultBreakpoint, expectedBreakpoint)) {
        // Added, removed and added again the same breakpoint, not our case.
        return@readAction
      }

      assert(isEnabled()) // Better to do a double check.

      LOG.error("""
            |Non-default breakpoint variant was set. Not an error, but we are glad to collect them. Thank you for reporting!
            |
            |Default variant: ${getDescription(fileUrl, defaultBreakpoint)}
            |Expected variant: ${getDescription(fileUrl, expectedBreakpoint)}
            |
            |Context at $fileUrl:${line + 1}:
            |${getFileContext(fileUrl, line)}
            |""".trimMargin())
    }
  }

  private fun readDocument(fileUrl: String, read: (Document) -> String): String {
    val document = VirtualFileManager.getInstance().findFileByUrl(fileUrl)?.let { file ->
      FileDocumentManager.getInstance().getDocument(file)
    }
    return when {
      document != null -> read(document)
      else -> "document content is unavailable"
    }
  }

  @RequiresReadLock
  private fun <P1 : XBreakpointProperties<*>, P2 : XBreakpointProperties<*>> lookSimilar(b1: XLineBreakpoint<P1>, b2: XLineBreakpoint<P2>): Boolean {
    return b1.type == b2.type &&
           b1.type.getHighlightRange(b1) == b2.type.getHighlightRange(b2) &&
           b1.type.getGeneralDescription(b1) == b2.type.getGeneralDescription(b2)
  }

  @RequiresReadLock
  private fun <P : XBreakpointProperties<*>> getDescription(fileUrl: String, b: XLineBreakpoint<P>): String {
    val t = b.type
    val kind = t.getGeneralDescription(b)
    val text = when (val range = t.getHighlightRange(b)) {
      null -> "<whole line>"
      else -> readDocument(fileUrl) { it.getText(range) }
    }
    return "$kind: $text"
  }

  @RequiresReadLock
  private fun getFileContext(fileUrl: String, line: Int): String {
    return readDocument(fileUrl) { document ->
      (line - 3 ..  line + 3)
        .filter { 0 <= it && it < document.lineCount }
        .joinToString("\n") {
          val prefix = if (it == line) ">> " else "   "
          val text = document.getText(TextRange.create(
            document.getLineStartOffset(it),
            document.getLineEndOffset(it)))
          prefix + text
        }
    }
  }
}