// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.scope.NonProjectFilesScope
import com.intellij.ui.FileColorManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
abstract class XStackFramesListColorsCache(project: Project) {

  protected val colorsManager: FileColorManager = FileColorManager.getInstance(project)

  @RequiresEdt
  abstract fun get(stackFrame: XStackFrame): Color?

  class Monolith(private val session: XDebugSessionImpl, framesList: XDebuggerFramesList) : XStackFramesListColorsCache(session.project) {

    private val myFileColors = mutableMapOf<VirtualFile, ColorState>()
    private val myCurrentlyComputingFiles = AtomicInteger(0)

    init {
      session.coroutineScope.launch {
        session.fileColorsComputer.fileColors.collect { (file, colorState) ->
          val oldState = myFileColors.put(file, colorState)

          if (colorState is ColorState.Computing) {
            myCurrentlyComputingFiles.incrementAndGet()
          }
          else if (oldState === ColorState.Computing) {
            if (myCurrentlyComputingFiles.decrementAndGet() == 0) {
              framesList.repaint()
            }
          }
        }
      }
    }

    override fun get(stackFrame: XStackFrame): Color? {
      val virtualFile = stackFrame.sourcePosition?.file
      if (virtualFile == null) {
        return colorsManager.getScopeColor(NonProjectFilesScope.NAME)
      }

      val res = myFileColors[virtualFile]
      if (res != null) {
        return res.color
      }

      session.fileColorsComputer.sendRequest(virtualFile)

      return null
    }
  }
}

@ApiStatus.Internal
class FileColorsComputer(project: Project, private val cs: CoroutineScope) {
  private val myColorsManager: FileColorManager = FileColorManager.getInstance(project)

  private val _fileColors = MutableSharedFlow<VirtualFileColor>(replay = 1)
  val fileColors: SharedFlow<VirtualFileColor> get() = _fileColors

  fun sendRequest(virtualFile: VirtualFile) {
    cs.launch(Dispatchers.Default) {
      if (!virtualFile.isValid) {
        _fileColors.emit(VirtualFileColor(virtualFile, ColorState.NoColor))
        return@launch
      }
      _fileColors.emit(VirtualFileColor(virtualFile, ColorState.Computing))
      val color = readAction { myColorsManager.getFileColor(virtualFile) }
      _fileColors.emit(VirtualFileColor(virtualFile, color.asState()))
    }
  }
}

@ApiStatus.Internal
data class VirtualFileColor(
  val file: VirtualFile,
  val colorState: ColorState,
)

@ApiStatus.Internal
sealed interface ColorState {
  val color: Color?

  object NoColor : ColorState {
    override val color: Color?
      get() = null
  }

  object Computing : ColorState {
    override val color: Color?
      get() = null
  }

  data class Computed(override val color: Color) : ColorState
}

internal fun Color?.asState() = if (this == null) ColorState.NoColor else ColorState.Computed(this)
