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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

private val DOCUMENTS_UPDATE_DEBOUNCE = 600.milliseconds

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
internal class FrontendEditorLinesBreakpointsInfoManager(private val project: Project, private val cs: CoroutineScope) {
  private val editorsMap = ConcurrentHashMap<Editor, EditorBreakpointLinesInfoMap>()

  init {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        if (editor.project != project) {
          return
        }
        putNewLinesInfoMap(editor)
      }

      override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        if (editor.project != project) {
          return
        }
        editorsMap.remove(editor)?.dispose()
      }
    }, cs.asDisposable())

    cs.launch {
      readAction {
        for (editor in EditorFactory.getInstance().allEditors) {
          if (editor.project == project) {
            putNewLinesInfoMap(editor)
          }
        }
      }
      // dispose editors that were disposed during map initialization to prevent races with the listener
      readAction {
        for (editor in editorsMap.keys) {
          if (editor.isDisposed) {
            editorsMap.remove(editor)?.dispose()
          }
        }
      }
    }
  }

  private fun putNewLinesInfoMap(editor: Editor): EditorBreakpointLinesInfoMap {
    val newMap = EditorBreakpointLinesInfoMap(cs, editor, project)
    val oldMap = editorsMap.putIfAbsent(editor, newMap)
    if (oldMap != null) {
      newMap.dispose()
    }
    return oldMap ?: newMap
  }

  suspend fun getBreakpointsInfoForLine(editor: Editor, line: Int): EditorLineBreakpointsInfo {
    val editorMap = editorsMap[editor]
    if (editorMap == null) {
      return putNewLinesInfoMap(editor).getBreakpointsInfoForLine(line)
    }
    return editorMap.getBreakpointsInfoForLine(line)
  }

  /**
   * Returns cached breakpoint types for the line, or null if the data is not available yet.
   * Schedules data fetching if needed, so the next calls will hopefully return cached data.
   */
  @RequiresReadLock
  fun getBreakpointsInfoForLineFast(editor: Editor, line: Int): EditorLineBreakpointsInfo? {
    val editorMap = editorsMap[editor] ?: return null
    val currentEditorStamp = editor.document.modificationStamp
    return editorMap.getBreakpointsInfoForLineInternal(line, currentEditorStamp)
  }

  companion object {
    fun getInstance(project: Project): FrontendEditorLinesBreakpointsInfoManager = project.service()
  }
}

@OptIn(FlowPreview::class)
private class EditorBreakpointLinesInfoMap(
  parentCs: CoroutineScope,
  private val editor: Editor,
  private val project: Project,
) {
  private val mapUpdateRequest = MutableSharedFlow<Unit>(replay = 1)
  private val debouncedUpdateRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  private val cs: CoroutineScope = parentCs.childScope("EditorBreakpointLinesInfoMap")

  private val breakpointsMap: FrontendViewportDataCache<EditorLineBreakpointsInfo> = FrontendViewportDataCache(
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

  suspend fun getBreakpointsInfoForLine(line: Int): EditorLineBreakpointsInfo {
    // let's try to find breakpoint types from the current map
    val currentEditorStamp = readAction { editor.document.modificationStamp }
    return breakpointsMap.getDataWithCaching(line, currentEditorStamp) ?: EditorLineBreakpointsInfo(listOf(), false)
  }

  fun getBreakpointsInfoForLineInternal(line: Int, currentEditorStamp: Long): EditorLineBreakpointsInfo? {
    return breakpointsMap.getData(line, currentEditorStamp)
  }

  fun dispose() {
    cs.cancel()
  }

  private suspend fun Editor.viewportIndicesInclusive(): Pair<Int, Int> {
    return withContext(Dispatchers.EDT) {
      if (isDisposed) {
        cancel()
      }
      val visibleRange = calculateVisibleRange()
      readAction {
        val firstVisibleLine = document.getLineNumber(visibleRange.startOffset)
        val lastVisibleLine = document.getLineNumber(visibleRange.endOffset)
        firstVisibleLine to lastVisibleLine
      }
    }
  }
}

internal class EditorLineBreakpointsInfo(
  val types: List<XBreakpointTypeProxy>,
  val singleBreakpointVariant: Boolean,
)

internal suspend fun getAvailableBreakpointTypesFromServer(project: Project, editor: Editor, line: Int): EditorLineBreakpointsInfo {
  val breakpointLinesInfo = XBreakpointTypeApi.getInstance().getBreakpointsInfoForLine(project.projectId(), editor.editorId(), line)
  val breakpointTypesManager = FrontendXBreakpointTypesManager.getInstance(project)
  val types = breakpointLinesInfo.availableTypes.mapNotNull { breakpointTypesManager.getTypeById(it) }
  return EditorLineBreakpointsInfo(types, breakpointLinesInfo.singleBreakpointVariant)
}

private suspend fun getAvailableBreakpointTypesFromServer(project: Project, editor: Editor, start: Int, endInclusive: Int): List<EditorLineBreakpointsInfo>? {
  val breakpointTypesManager = FrontendXBreakpointTypesManager.getInstance(project)
  // TODO: is it possible to avoid this retry?
  val retriesCount = 5
  repeat(retriesCount) {
    val breakpointsInfoDtos = XBreakpointTypeApi.getInstance().getBreakpointsInfoForEditor(project.projectId(), editor.editorId(), start, endInclusive)
    val breakpointInfoList = breakpointsInfoDtos?.map { lineBreakpointInfo ->
      val types = lineBreakpointInfo.availableTypes.mapNotNull { breakpointTypesManager.getTypeById(it) }
      EditorLineBreakpointsInfo(types, lineBreakpointInfo.singleBreakpointVariant)
    }
    if (breakpointInfoList != null) {
      return breakpointInfoList
    }
    delay(100.milliseconds)
  }
  return null
}