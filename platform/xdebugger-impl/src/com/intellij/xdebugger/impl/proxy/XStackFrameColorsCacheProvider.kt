// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.proxy.XStackFramesListColorsCache
import com.intellij.psi.search.scope.NonProjectFilesScope
import com.intellij.ui.FileColorManager
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.awt.Color
import java.util.concurrent.atomic.AtomicInteger


internal class MonolithFramesColorCache(session: XDebugSessionImpl, onAllComputed: () -> Unit) : XStackFramesListColorsCache {
  private val computer = FileColorsComputer(session.project, session.coroutineScope)

  private val myFileColors = mutableMapOf<VirtualFile, ColorState>()
  private val myCurrentlyComputingFiles = AtomicInteger(0)

  init {
    session.coroutineScope.launch {
      computer.fileColors.collect { (file, colorState) ->
        val oldState = myFileColors.put(file, colorState)

        if (colorState is ColorState.Computing) {
          myCurrentlyComputingFiles.incrementAndGet()
        }
        else if (oldState === ColorState.Computing) {
          if (myCurrentlyComputingFiles.decrementAndGet() == 0) {
            onAllComputed()
          }
        }
      }
    }
  }

  override fun get(stackFrame: XStackFrame, project: Project): Color? {
    val virtualFile = stackFrame.sourcePosition?.file
    if (virtualFile == null) {
      return FileColorManager.getInstance(project).getScopeColor(NonProjectFilesScope.NAME)
    }

    val res = myFileColors[virtualFile]
    if (res != null) {
      return res.color
    }

    computer.sendRequest(virtualFile)

    return null
  }
}

private class FileColorsComputer(project: Project, private val cs: CoroutineScope) {
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

private data class VirtualFileColor(
  val file: VirtualFile,
  val colorState: ColorState,
)

private sealed interface ColorState {
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

private fun Color?.asState() = if (this == null) ColorState.NoColor else ColorState.Computed(this)
