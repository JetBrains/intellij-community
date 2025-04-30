// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.FrontendViewportDataCache.ViewportInfo
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asDisposable
import com.intellij.util.concurrency.annotations.RequiresReadLock
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

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
internal class FrontendEditorLinesBreakpointTypesManager(private val project: Project, private val cs: CoroutineScope) {
  private val editorsMap = ConcurrentHashMap<Editor, EditorBreakpointTypesMap>()

  init {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        editorsMap.putIfAbsent(editor, EditorBreakpointTypesMap(cs, editor, project))
      }

      override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        editorsMap.remove(editor)?.dispose()
      }
    }, cs.asDisposable())
  }

  suspend fun getTypesForLine(editor: Editor, line: Int): List<XBreakpointTypeProxy> {
    val editorMap = editorsMap[editor]
    if (editorMap == null) {
      return getAvailableBreakpointTypesFromServer(project, editor, line)
    }
    return editorMap.getTypesForLine(line)
  }

  /**
   * Returns cached breakpoint types for the line, or null if the data is not available yet.
   * Schedules data fetching if needed, so the next calls will hopefully return cached data.
   */
  @RequiresReadLock
  fun getTypesForLineFast(editor: Editor, line: Int): List<XBreakpointTypeProxy> {
    val editorMap = editorsMap[editor] ?: return emptyList()
    val currentEditorStamp = editor.document.modificationStamp
    val cached = editorMap.getTypesForLineInternal(line, currentEditorStamp)
    if (cached == null) {
      cs.launch {
        getTypesForLine(editor, line)
      }
      return emptyList()
    }
    return cached
  }

  companion object {
    fun getInstance(project: Project): FrontendEditorLinesBreakpointTypesManager = project.service()
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

  private val breakpointsMap: FrontendViewportDataCache<List<XBreakpointTypeProxy>> = FrontendViewportDataCache(
    loadData = { firstIndex, lastIndexInclusive ->
      getAvailableBreakpointTypesFromServer(project, editor, firstIndex, lastIndexInclusive)
    }
  )

  init {
    mapUpdateRequest.tryEmit(Unit)
    cs.launch {
      debouncedUpdateRequests.debounce(DOCUMENTS_UPDATE_DEBOUNCE).collectLatest {
        mapUpdateRequest.tryEmit(Unit)
      }
    }
    cs.launch {
      mapUpdateRequest.collectLatest {
        val (currentStamp, editorLinesCount) = readAction {
          editor.document.modificationStamp to editor.document.lineCount
        }
        val (firstViewportIndex, lastViewportIndex) = editor.viewportIndicesInclusive()
        breakpointsMap.update(ViewportInfo(firstViewportIndex, lastViewportIndex), editorLinesCount - 1, currentStamp)
      }
    }

    editor.document.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        breakpointsMap.clear()
        debouncedUpdateRequests.tryEmit(Unit)
      }
    }, cs.asDisposable())

    FrontendXBreakpointTypesManager.getInstance(project).subscribeOnBreakpointTypesChanges(cs) {
      breakpointsMap.clear()
      mapUpdateRequest.tryEmit(Unit)
    }

    editor.scrollingModel.addVisibleAreaListener({ debouncedUpdateRequests.tryEmit(Unit) }, cs.asDisposable())
  }

  suspend fun getTypesForLine(line: Int): List<XBreakpointTypeProxy> {
    // let's try to find breakpoint types from the current map
    val currentEditorStamp = readAction { editor.document.modificationStamp }
    val cachedTypes = getTypesForLineInternal(line, currentEditorStamp)
    if (cachedTypes != null) {
      return cachedTypes
    }

    // No cached data or document was modified, let's make an rpc call for that, data will be fetched later
    return getAvailableBreakpointTypesFromServer(project, editor, line)
  }

  fun getTypesForLineInternal(line: Int, currentEditorStamp: Long): List<XBreakpointTypeProxy>? {
    return breakpointsMap.getData(line, currentEditorStamp)
  }

  fun dispose() {
    cs.cancel()
  }

  private suspend fun Editor.viewportIndicesInclusive(): Pair<Int, Int> {
    return withContext(Dispatchers.EDT) {
      val visibleRange = calculateVisibleRange()
      readAction {
        val firstVisibleLine = document.getLineNumber(visibleRange.startOffset)
        val lastVisibleLine = document.getLineNumber(visibleRange.endOffset)
        firstVisibleLine to lastVisibleLine
      }
    }
  }
}

internal suspend fun getAvailableBreakpointTypesFromServer(project: Project, editor: Editor, line: Int): List<XBreakpointTypeProxy> {
  val availableTypeIds = XBreakpointTypeApi.getInstance().getAvailableBreakpointTypesForLine(project.projectId(), editor.editorId(), line)
  val breakpointTypesManager = FrontendXBreakpointTypesManager.getInstance(project)
  return availableTypeIds.mapNotNull { breakpointTypesManager.getTypeById(it) }
}

private suspend fun getAvailableBreakpointTypesFromServer(project: Project, editor: Editor, start: Int, endInclusive: Int): List<List<XBreakpointTypeProxy>>? {
  val breakpointTypesManager = FrontendXBreakpointTypesManager.getInstance(project)
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