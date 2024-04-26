// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "SSBasedInspection")

package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.EditorWindow.RelativePosition
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JSplitPane

@Service(Service.Level.PROJECT)
internal class SplitterService(private val project: Project) {
  companion object {
    fun getInstance(project: Project): SplitterService = project.service()
  }

  private class ActiveState(
    val window: EditorWindow,
    val file: VirtualFile,
    val splitChooser: EditorWindow.SplitChooser,
  )

  private var state: ActiveState? = null

  val activeWindow: EditorWindow?
    get() = state?.window

  var initialEditorWindow: EditorWindow? = null
    private set

  val isActive: Boolean
    get() = activeWindow != null

  fun activateSplitChooser(window: EditorWindow, file: VirtualFile, openedFromEditor: Boolean) {
    state?.let {
      stopSplitChooser(interrupted = true, currentState = it)
    }

    state = ActiveState(window, file, window.showSplitChooser(project, showInfoPanel = true))
    if (openedFromEditor) {
      initialEditorWindow = activeWindow
    }
  }

  private fun switchWindow(window: EditorWindow, currentState: ActiveState) {
    currentState.splitChooser.dispose()
    state = ActiveState(window, currentState.file, window.showSplitChooser(project, showInfoPanel = false))
  }

  fun stopSplitChooser(interrupted: Boolean) {
    stopSplitChooser(interrupted = interrupted, currentState = state ?: return)
  }

  private fun stopSplitChooser(interrupted: Boolean, currentState: ActiveState) {
    currentState.splitChooser.dispose()
    this.state = null
    initialEditorWindow = null
    if (!interrupted) {
      currentState.window.requestFocus(true)
    }
  }

  fun nextWindow() {
    val currentState = state ?: return
    val orderedWindows = currentState.window.owner.getOrderedWindows()
    val index = (orderedWindows.indexOf(currentState.window) + 1) % orderedWindows.size
    switchWindow(window = orderedWindows.get(index), currentState = currentState)
  }

  fun previousWindow() {
    val currentState = state ?: return

    val orderedWindows = currentState.window.owner.getOrderedWindows()
    var index = orderedWindows.indexOf(currentState.window) - 1
    index = if (index < 0) orderedWindows.size - 1 else index
    switchWindow(window = orderedWindows.get(index), currentState = currentState)
  }

  fun split(move: Boolean) {
    val state = state!!
    val activeWindow = state.window
    val initialWindow = initialEditorWindow
    val position = state.splitChooser.position
    stopSplitChooser(interrupted = false)

    // if a position is default and focus is still in the same editor window => nothing needs to be done
    if (position == RelativePosition.CENTER && initialWindow == activeWindow) {
      return
    }

    val file = state.file
    if (position == RelativePosition.CENTER) {
      activeWindow.manager.openFile(file = file, window = activeWindow, options = FileEditorOpenOptions(requestFocus = true))
    }
    else {
      activeWindow.split(
        orientation = if (position == RelativePosition.UP || position == RelativePosition.DOWN) {
          JSplitPane.VERTICAL_SPLIT
        }
        else {
          JSplitPane.HORIZONTAL_SPLIT
        },
        forceSplit = true,
        virtualFile = file,
        focusNew = true,
        fileIsSecondaryComponent = position != RelativePosition.LEFT && position != RelativePosition.UP
      )
    }

    if (initialWindow != null && move) {
      initialWindow.closeFile(file = file)
    }
  }

  fun setSplitSide(side: RelativePosition) {
    val state = state!!
    if (side != state.splitChooser.position) {
      state.splitChooser.positionChanged(side)
    }
    else {
      switchWindow(window = state.window.getAdjacentEditors().get(side) ?: return, currentState = state)
    }
  }
}