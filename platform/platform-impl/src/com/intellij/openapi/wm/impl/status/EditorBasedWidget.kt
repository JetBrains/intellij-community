// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.EditorTextField
import com.intellij.util.messages.MessageBusConnection
import java.awt.Component
import java.awt.KeyboardFocusManager

abstract class EditorBasedWidget protected constructor(@JvmField protected val myProject: Project) : StatusBarWidget {
  @JvmField
  protected var myStatusBar: StatusBar? = null
  @JvmField
  protected val myConnection: MessageBusConnection

  @Volatile
  protected var isDisposed = false
    private set

  protected val project: Project
    get() = myProject

  init {
    @Suppress("LeakingThis")
    myConnection = project.messageBus.connect(this)
  }

  protected open fun getEditor(): Editor? {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return FileEditorManager.getInstance(project).selectedTextEditor
    }

    val fileEditor = StatusBarUtil.getCurrentFileEditor(myStatusBar)
    return if (fileEditor is TextEditor) fileEditor.editor else null
  }

  open fun isOurEditor(editor: Editor?): Boolean {
    return editor != null &&
           editor.component.isShowing &&
           java.lang.Boolean.TRUE != editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY) &&
           WindowManager.getInstance().getStatusBar(editor.component, editor.project) === myStatusBar
  }

  fun getFocusedComponent(): Component? {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.let {
      return it
    }

    val focusManager = IdeFocusManager.getInstance(project)
    return focusManager.getLastFocusedFor(focusManager.lastFocusedIdeWindow ?: return null)
  }

  fun getFocusedEditor(): Editor? {
    val component = getFocusedComponent()
    val editor = if (component is EditorComponentImpl) component.editor else getEditor()
    return if (editor != null && !editor.isDisposed) editor else null
  }

  protected open fun getSelectedFile(): VirtualFile? {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      val textEditor = FileEditorManager.getInstance(project).selectedTextEditor
      return textEditor?.virtualFile
    }
    val fileEditor = StatusBarUtil.getCurrentFileEditor(myStatusBar)
    return (fileEditor as? TextEditor)?.file
  }

  override fun install(statusBar: StatusBar) {
    assert(statusBar.project == null || statusBar.project == project) {
      "Cannot install widget from one project on status bar of another project"
    }
    myStatusBar = statusBar
  }

  override fun dispose() {
    isDisposed = true
    myStatusBar = null
  }
}