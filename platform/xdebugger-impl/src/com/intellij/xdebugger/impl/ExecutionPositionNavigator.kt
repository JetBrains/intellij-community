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
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch


enum class ExecutionPositionNavigationMode {
  SCROLL, OPEN,
}

internal class ExecutionPositionNavigator(
  private val project: Project,
  coroutineScope: CoroutineScope,
  private val sourcePosition: XSourcePosition,
  private val isTopFrame: Boolean,
  updateFlow: Flow<Boolean>,
) {
  private var openedEditor: Editor? = null
  private var openFileDescriptor: OpenFileDescriptor? = null

  init {
    coroutineScope.launch {
      updateFlow.collect { isToScrollToPosition ->
        invalidate()
        if (isToScrollToPosition) {
          navigateTo(ExecutionPositionNavigationMode.SCROLL)
        }
      }
    }
  }

  fun navigateTo(navigationMode: ExecutionPositionNavigationMode, isActiveSourceKind: Boolean = true) {
    val effectiveNavigationMode = if (isActiveSourceKind) navigationMode else ExecutionPositionNavigationMode.SCROLL
    val descriptor = getDescriptor()
    navigateTo(descriptor, effectiveNavigationMode)
  }

  private fun invalidate() {
    synchronized(this) {
      openFileDescriptor?.dispose()
    }
  }

  private fun getDescriptor(): OpenFileDescriptor {
    synchronized(this) {
      var descriptor = openFileDescriptor
      if (descriptor == null || descriptor.rangeMarker?.isValid == false) {
        openFileDescriptor?.dispose()
        descriptor = createOpenFileDescriptor()
        openFileDescriptor = descriptor
      }
      return descriptor
    }
  }

  private fun navigateTo(openFileDescriptor: OpenFileDescriptor, navigationMode: ExecutionPositionNavigationMode) {
    when (navigationMode) {
      ExecutionPositionNavigationMode.OPEN -> {
        openedEditor = XDebuggerUtilImpl.createEditor(openFileDescriptor)
      }
      ExecutionPositionNavigationMode.SCROLL -> {
        val fileEditorManager = FileEditorManager.getInstance(openFileDescriptor.project)
        val editor = openedEditor?.takeUnless { it.isDisposed }
                     ?: fileEditorManager.getSelectedEditor(openFileDescriptor.file).asSafely<TextEditor>()?.editor
                     ?: return
        openFileDescriptor.navigateIn(editor)
      }
    }
  }

  private fun createOpenFileDescriptor(): OpenFileDescriptor {
    return createPositionNavigatable().apply {
      isUseCurrentWindow = false //see IDEA-125645 and IDEA-63459
      isUsePreviewTab = true
      setScrollType(scrollType())
    }
  }

  private fun createPositionNavigatable(): OpenFileDescriptor {
    val navigatable = sourcePosition.createNavigatable(project)
    return if (navigatable is OpenFileDescriptor) {
      navigatable
    }
    else {
      XDebuggerUtilImpl.createOpenFileDescriptor(project, sourcePosition)
    }
  }

  private fun scrollType(): ScrollType {
    if (XDebuggerSettingManagerImpl.getInstanceImpl().generalSettings.isScrollToCenter) return ScrollType.CENTER
    return if (isTopFrame) ScrollType.MAKE_VISIBLE else ScrollType.CENTER
  }
}
