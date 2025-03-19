// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.PlatformUtils
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

internal class BreakpointVariantPriorityTracker(private val project: Project, private val coroutineScope: CoroutineScope)
  : XBreakpointListener<XBreakpoint<*>> {

  private val events = ArrayDeque<Event>()
  private val cleanUpRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private fun isEnabled(): Boolean =
    Registry.`is`("debugger.report.non.default.inline.breakpoint") &&
    IntelliJProjectUtil.isIntelliJPlatformProject(project) &&
    !ApplicationManager.getApplication().isUnitTestMode

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

    // There are problems because of the way how CWM sets breakpoints, so ignore all this stuff for the sake of simplicity (IDEA-353218).
    if (PlatformUtils.isJetBrainsClient()) return

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
      if (defaultBreakpoint.type == expectedBreakpoint.type &&
          defaultBreakpoint.highlightRange == expectedBreakpoint.highlightRange &&
          defaultBreakpoint.generalDescription == expectedBreakpoint.generalDescription) {
        // Added, removed and added again the same breakpoint, not our case.
        return@readAction
      }

      assert(isEnabled()) // Better to do a double check.

      val safeDesc = """
        |Default variant: ${getDescription(defaultBreakpoint)}
        |Expected variant: ${getDescription(expectedBreakpoint)}
        """.trimMargin()

      val msg = """
        |Non-default breakpoint variant was set. Not an error, but we are glad to collect them. Thank you for reporting!
        |If you don't ever want to report this, set registry debugger.report.non.default.inline.breakpoint=false.
        |
        |$safeDesc
        """.trimMargin()
      val context = """
        |$safeDesc
        |
        |Default variant text: ${getText(fileUrl, defaultBreakpoint)}
        |Expected variant text: ${getText(fileUrl, expectedBreakpoint)}
        |
        |Context at $fileUrl:${line + 1}:
        |${getFileContext(fileUrl, line)}
        """.trimMargin()
      LOG.error(msg, Attachment("context.txt", context))
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
  private fun getDescription(b: XLineBreakpoint<*>): String =
    "${b.type.id}, ${b.generalDescription}"

  @RequiresReadLock
  private fun getText(fileUrl: String, b: XLineBreakpoint<*>): String =
    when (val range = b.highlightRange) {
      null -> "<whole line>"
      else -> readDocument(fileUrl) { it.getText(range) }
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