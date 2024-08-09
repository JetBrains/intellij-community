// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.EDT
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.WeakReference


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
  private var openedEditorRef: WeakReference<Editor>? = null
  private var openedEditor: Editor?
    get() = openedEditorRef?.get()
    set(value) { openedEditorRef = value?.let(::WeakReference) }

  private var openFileDescriptor: OpenFileDescriptor? = null
  private val descriptorMutex = Mutex()

  init {
    coroutineScope.launch(Dispatchers.EDT) {
      updateFlow.collect { isToScrollToPosition ->
        invalidate()
        if (isToScrollToPosition) {
          navigateTo(ExecutionPositionNavigationMode.SCROLL)
        }
      }
    }
  }

  suspend fun navigateTo(navigationMode: ExecutionPositionNavigationMode, isActiveSourceKind: Boolean = true) {
    EDT.assertIsEdt()
    val effectiveNavigationMode = if (isActiveSourceKind) navigationMode else ExecutionPositionNavigationMode.SCROLL
    val descriptor = getDescriptor()
    writeIntentReadAction {
      navigateTo(descriptor, effectiveNavigationMode)
    }
  }

  private suspend fun invalidate() {
    descriptorMutex.withLock {
      openFileDescriptor?.dispose()
    }
  }

  private suspend fun getDescriptor(): OpenFileDescriptor {
    descriptorMutex.withLock {
      var descriptor = openFileDescriptor
      if (descriptor == null || descriptor.rangeMarker?.isValid == false) {
        openFileDescriptor?.dispose()
        descriptor = readAction {
          createOpenFileDescriptor()
        }
        openFileDescriptor = descriptor
      }
      return descriptor
    }
  }

  @RequiresEdt
  private fun navigateTo(openFileDescriptor: OpenFileDescriptor, navigationMode: ExecutionPositionNavigationMode) {
    when (navigationMode) {
      ExecutionPositionNavigationMode.OPEN -> {
        openedEditor = XDebuggerUtil.getInstance().openTextEditor(openFileDescriptor)
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

  @RequiresReadLock
  @RequiresBackgroundThread
  private fun createOpenFileDescriptor(): OpenFileDescriptor {
    return createPositionNavigatable().apply {
      isUseCurrentWindow = false //see IDEA-125645 and IDEA-63459
      isUsePreviewTab = true
      setScrollType(scrollType())
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
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
