// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeApi
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

private val DOCUMENTS_UPDATE_DEBOUNCE = 600.milliseconds

private const val WINDOW_LINES_COUNT = 100

private val LOG = logger<FrontendEditorLinesBreakpointTypesManager>()

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
internal class FrontendEditorLinesBreakpointTypesManager(private val project: Project, private val cs: CoroutineScope) {
  private val editorsMap = ConcurrentHashMap<Editor, EditorBreakpointTypesMap>()

  fun editorCreated(editor: Editor) {
    editorsMap.putIfAbsent(editor, EditorBreakpointTypesMap(cs, editor, project))
  }

  fun editorReleased(editor: Editor) {
    editorsMap.remove(editor)?.dispose()
  }

  suspend fun getTypesForLine(editor: Editor, line: Int): List<XBreakpointTypeProxy> {
    val editorMap = editorsMap[editor]
    if (editorMap == null) {
      return getAvailableBreakpointTypesFromServer(project, editor, line)
    }
    return editorMap.getTypesForLine(line)
  }

  companion object {
    fun getInstance(project: Project): FrontendEditorLinesBreakpointTypesManager = project.service()
  }
}

internal class FrontendEditorLinesBreakpointTypesManagerEditorsListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return
    FrontendEditorLinesBreakpointTypesManager.getInstance(project).editorCreated(editor)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return
    FrontendEditorLinesBreakpointTypesManager.getInstance(project).editorReleased(editor)
  }
}

@OptIn(FlowPreview::class)
private class EditorBreakpointTypesMap(
  parentCs: CoroutineScope,
  private val editor: Editor,
  private val project: Project,
) {
  private val mapUpdateRequest = MutableSharedFlow<Unit>(replay = 1)
  private val debouncedUpdateRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val cs: CoroutineScope = parentCs.childScope("EditorBreakpointTypesMap")

  @Volatile
  private var breakpointTypesMap: StampedBreakpointTypesMap? = null

  init {
    mapUpdateRequest.tryEmit(Unit)
    cs.launch {
      debouncedUpdateRequests.debounce(DOCUMENTS_UPDATE_DEBOUNCE).collectLatest {
        mapUpdateRequest.tryEmit(Unit)
      }
    }
    cs.launch {
      mapUpdateRequest.collectLatest {
        val currentBreakpointTypesMap = breakpointTypesMap
        val currentStamp = readAction { editor.document.modificationStamp }
        val (firstIndex, lastIndex) = calculateWindowIndices(editor)
        if (currentBreakpointTypesMap == null || currentBreakpointTypesMap.shouldBeUpdated(currentStamp, firstIndex, lastIndex)) {
          val types = getAvailableBreakpointTypesFromServer(project, editor, firstIndex, lastIndex)
          if (types != null) {
            breakpointTypesMap = StampedBreakpointTypesMap(currentStamp, firstIndex, lastIndex, types)
          }
          else {
            breakpointTypesMap = null
            LOG.warn("Debugger breakpoints map is not calculated for $editor")
          }
        }
      }
    }

    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        breakpointTypesMap = null
        debouncedUpdateRequests.tryEmit(Unit)
      }
    }, cs.asDisposable())

    editor.scrollingModel.addVisibleAreaListener({ debouncedUpdateRequests.tryEmit(Unit) }, cs.asDisposable())
  }

  suspend fun getTypesForLine(line: Int): List<XBreakpointTypeProxy> {
    // let's try to find breakpoint types from the current map
    val currentTimestampedMap = breakpointTypesMap
    if (currentTimestampedMap != null) {
      val breakpointTypes = currentTimestampedMap.getLineBreakpointTypes(editor, line)
      if (breakpointTypes != null) {
        return breakpointTypes
      }
    }

    // No cached data or document was modified, let's make an rpc call for that, data will be fetched later
    return getAvailableBreakpointTypesFromServer(project, editor, line)
  }

  fun dispose() {
    cs.cancel()
  }

  private class StampedBreakpointTypesMap(
    private val modificationStamp: Long,
    private val firstIndex: Int,
    private val lastIndex: Int,
    private val types: List<List<XBreakpointTypeProxy>>,
  ) {
    fun shouldBeUpdated(currentStamp: Long, firstIndex: Int, lastIndexInclusive: Int): Boolean {
      return modificationStamp != currentStamp || this.firstIndex != firstIndex || this.lastIndex != lastIndexInclusive
    }

    suspend fun getLineBreakpointTypes(editor: Editor, line: Int): List<XBreakpointTypeProxy>? {
      val currentStamp = readAction { editor.document.modificationStamp }
      if (modificationStamp != currentStamp) {
        return null
      }
      if (line !in firstIndex..lastIndex) {
        return null
      }
      return types[line - firstIndex]
    }
  }

  companion object {
    // both indices are inclusive
    private suspend fun calculateWindowIndices(editor: Editor): Pair<Int, Int> {
      val visibleRange = withContext(Dispatchers.EDT) {
        editor.calculateVisibleRange()
      }

      return readAction {
        val lastDocumentIndex = (editor.document.lineCount - 1).coerceAtLeast(0)
        val firstVisibleLine = editor.document.getLineNumber(visibleRange.startOffset)
        val lastVisibleLine = editor.document.getLineNumber(visibleRange.endOffset)
        val firstIndex = ((firstVisibleLine / WINDOW_LINES_COUNT - 2) * WINDOW_LINES_COUNT).coerceIn(0, lastDocumentIndex)
        val lastIndex = ((lastVisibleLine / WINDOW_LINES_COUNT + 2) * WINDOW_LINES_COUNT).coerceIn(0, lastDocumentIndex)
        firstIndex to lastIndex
      }
    }
  }
}

internal suspend fun getAvailableBreakpointTypesFromServer(project: Project, editor: Editor, line: Int): List<XBreakpointTypeProxy> {
  val availableTypeIds = XBreakpointTypeApi.getInstance().getAvailableBreakpointTypesForLine(project.projectId(), editor.editorId(), line)
  val breakpointTypesManager = FrontendXBreakpointTypesManager.getInstanceSuspending(project)
  return availableTypeIds.mapNotNull { breakpointTypesManager.getTypeById(it) }
}

private suspend fun getAvailableBreakpointTypesFromServer(project: Project, editor: Editor, start: Int, endInclusive: Int): List<List<XBreakpointTypeProxy>>? {
  val breakpointTypesManager = FrontendXBreakpointTypesManager.getInstanceSuspending(project)
  // TODO: is it possible to avoid this retry?
  val retriesCount = 5
  repeat(retriesCount) {
    val availableTypeIds = XBreakpointTypeApi.getInstance().getAvailableBreakpointTypesForEditor(project.projectId(), editor.editorId(), start, endInclusive)
    val types = availableTypeIds?.map { it.mapNotNull { id -> breakpointTypesManager.getTypeById(id) } }
    if (types != null) {
      return types
    }
    delay(100.milliseconds)
  }
  return null
}