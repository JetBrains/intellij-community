// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XSourcePositionEx.NavigationMode
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl


internal class ExecutionPositionNavigator(
  private val openFileDescriptor: OpenFileDescriptor,
) {
  var isActiveSourceKind: Boolean = false

  private var openedEditor: Editor? = null

  fun navigateTo(navigationMode: NavigationMode) {
    if (navigationMode == NavigationMode.NONE) return

    if (navigationMode == NavigationMode.OPEN && isActiveSourceKind) {
      openedEditor = XDebuggerUtilImpl.createEditor(openFileDescriptor)
    }
    else {
      val fileEditorManager = FileEditorManager.getInstance(openFileDescriptor.project)
      val editor = openedEditor?.takeUnless { it.isDisposed }
                   ?: fileEditorManager.getSelectedEditor(openFileDescriptor.file).asSafely<TextEditor>()?.editor
                   ?: return
      openFileDescriptor.navigateIn(editor)
    }
  }

  fun disposeDescriptor() {
    openFileDescriptor.dispose()
  }

  companion object {
    fun create(project: Project, sourcePosition: XSourcePosition, isTopFrame: Boolean): ExecutionPositionNavigator {
      val openFileDescriptor = createOpenFileDescriptor(project, sourcePosition).apply {
        isUseCurrentWindow = false //see IDEA-125645 and IDEA-63459
        isUsePreviewTab = true
        setScrollType(scrollType(isTopFrame))
      }
      return ExecutionPositionNavigator(openFileDescriptor)
    }

    private fun createOpenFileDescriptor(project: Project, position: XSourcePosition): OpenFileDescriptor {
      val navigatable = position.createNavigatable(project)
      return if (navigatable is OpenFileDescriptor) {
        navigatable
      }
      else {
        XDebuggerUtilImpl.createOpenFileDescriptor(project, position)
      }
    }

    private fun scrollType(isTopFrame: Boolean): ScrollType {
      if (XDebuggerSettingManagerImpl.getInstanceImpl().generalSettings.isScrollToCenter) return ScrollType.CENTER
      return if (isTopFrame) ScrollType.MAKE_VISIBLE else ScrollType.CENTER
    }
  }
}
