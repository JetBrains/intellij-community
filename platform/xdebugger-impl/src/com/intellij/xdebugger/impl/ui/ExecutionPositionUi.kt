// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.xdebugger.impl.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import com.intellij.xdebugger.impl.ExecutionPointVm
import com.intellij.xdebugger.impl.ExecutionPositionVm
import com.intellij.xdebugger.ui.DebuggerColors
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds


private val LOG = logger<ExecutionPositionUi>()


internal fun showExecutionPointUi(project: Project, coroutineScope: CoroutineScope, vmFlow: Flow<ExecutionPointVm?>) {
  showExecutionPositionUi(project, coroutineScope, vmFlow.map { it?.mainPositionVm })
  showExecutionPositionUi(project, coroutineScope, vmFlow.map { it?.alternativePositionVm })
}

internal fun showExecutionPositionUi(project: Project, coroutineScope: CoroutineScope, vmFlow: Flow<ExecutionPositionVm?>) {
  coroutineScope.launch(Dispatchers.EDT) {
    vmFlow
      .distinctUntilChanged()
      .mapLatest { vm ->
        if (vm == null) {
          delay(25.milliseconds)  // delay hiding the highlight for a while to reduce visual flickering
        }
        vm
      }
      .collectLatest { vm ->
        kotlin.runCatching {
          if (vm == null) awaitCancellation()
          ExecutionPositionUi.showUntilCancelled(project, vm)
        }.getOrLogException<Nothing>(LOG)
      }
  }
}

internal class ExecutionPositionUi private constructor(
  coroutineScope: CoroutineScope,
  vm: ExecutionPositionVm,
  private val rangeHighlighter: RangeHighlighter,
) : AutoCloseable {
  init {
    coroutineScope.launch(start = UNDISPATCHED) {
      vm.isActiveSourceKindState.collect { isActiveSourceKind ->
        val useTopFrameAttributes = vm.isTopFrame && isActiveSourceKind
        val attributesKey = if (useTopFrameAttributes) DebuggerColors.EXECUTIONPOINT_ATTRIBUTES else DebuggerColors.NOT_TOP_FRAME_ATTRIBUTES
        rangeHighlighter.setTextAttributesKey(attributesKey)
        rangeHighlighter.putUserData(ExecutionPointHighlighter.EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY, useTopFrameAttributes)
      }
    }
    coroutineScope.launch(start = UNDISPATCHED) {
      vm.gutterVm.gutterIconRendererState.collect { gutterIconRenderer ->
        rangeHighlighter.gutterIconRenderer = gutterIconRenderer
      }
    }
  }

  override fun close() {
    rangeHighlighter.dispose()
  }

  companion object {
    suspend fun showUntilCancelled(project: Project, vm: ExecutionPositionVm): Nothing {
      vm.invalidationUpdateFlow.onStart { emit(Unit) }.collectLatest {
        coroutineScope {
          create(this, project, vm).use {
            awaitCancellation()
          }
        }
      }
      error("not reached")
    }

    private suspend fun create(coroutineScope: CoroutineScope, project: Project, vm: ExecutionPositionVm): ExecutionPositionUi? {
      EDT.assertIsEdt()
      val rangeHighlighter = createRangeHighlighter(project, vm) ?: return null
      rangeHighlighter.editorFilter = MarkupEditorFilter { it.editorKind == EditorKind.MAIN_EDITOR }

      return ExecutionPositionUi(coroutineScope, vm, rangeHighlighter)
    }

    private suspend fun createRangeHighlighter(project: Project, vm: ExecutionPositionVm): RangeHighlighter? {
      EDT.assertIsEdt()
      val document = readAction {
        FileDocumentManager.getInstance().getDocument(vm.file)
      } ?: return null

      val line = vm.line
      if (!DocumentUtil.isValidLine(line, document)) return null

      val markupModel = DocumentMarkupModel.forDocument(document, project, true)
      val range = vm.exactRange
      if (range != null) {
        return markupModel.addRangeHighlighter(null, range.startOffset, range.endOffset,
                                               DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                                               HighlighterTargetArea.EXACT_RANGE)
      }
      return markupModel.addLineHighlighter(null, line, DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER)
    }

    @RequiresEdt
    fun isFullLineHighlighterAt(file: VirtualFile, line: Int, project: Project, isToCheckTopFrameOnly: Boolean = false): Boolean {
      val rangeHighlighter = findPositionHighlighterAt(file, line, project) ?: return false
      return isFullLineHighlighter(rangeHighlighter, isToCheckTopFrameOnly)
    }

    private fun findPositionHighlighterAt(file: VirtualFile, line: Int, project: Project): RangeHighlighter? {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
      if (line < 0 || line >= document.lineCount) return null
      val lineStartOffset = document.getLineStartOffset(line)
      val lineEndOffset = document.getLineEndOffset(line)
      val markupModel = DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx ?: return null
      var result: RangeHighlighter? = null
      markupModel.processRangeHighlightersOverlappingWith(lineStartOffset, lineEndOffset) { rangeHighlighter ->
        val foundIt = rangeHighlighter.getUserData(ExecutionPointHighlighter.EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY) != null
        if (foundIt) {
          result = rangeHighlighter
        }
        !foundIt
      }
      return result
    }

    private fun isFullLineHighlighter(rangeHighlighter: RangeHighlighter, isToCheckTopFrameOnly: Boolean): Boolean {
      val isTopFrame = rangeHighlighter.getUserData(ExecutionPointHighlighter.EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY) ?: return false
      return rangeHighlighter.isValid &&
             rangeHighlighter.targetArea == HighlighterTargetArea.LINES_IN_RANGE &&
             (isTopFrame || !isToCheckTopFrameOnly)
    }
  }
}
