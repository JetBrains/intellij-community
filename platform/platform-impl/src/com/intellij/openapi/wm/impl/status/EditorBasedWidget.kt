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
import com.intellij.ui.EditorTextField
import com.intellij.util.messages.MessageBusConnection
import java.awt.Component
import java.awt.KeyboardFocusManager

open class EditorBasedWidgetHelper(val project: Project) {
  fun getFocusedEditor(statusBar: StatusBar?): Editor? {
    val component = getFocusedComponent()
    val editor = if (component is EditorComponentImpl) component.editor else getEditor(statusBar)
    return if (editor != null && !editor.isDisposed) editor else null
  }

  fun getFocusedComponent(): Component? {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner?.let {
      return it
    }

    val focusManager = IdeFocusManager.getInstance(project)
    return focusManager.getLastFocusedFor(focusManager.lastFocusedIdeWindow ?: return null)
  }

  fun getEditor(statusBar: StatusBar?): Editor? {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return FileEditorManager.getInstance(project).selectedTextEditor
    }

    val fileEditor = StatusBarUtil.getCurrentFileEditor(statusBar)
    return if (fileEditor is TextEditor) fileEditor.editor else null
  }

  open fun isOurEditor(editor: Editor?, statusBar: StatusBar?): Boolean {
    return editor != null &&
           editor.component.isShowing &&
           editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY) != java.lang.Boolean.TRUE &&
           StatusBarUtil.getStatusBar(editor.component) === statusBar
  }
}

abstract class EditorBasedWidget protected constructor(
  @Deprecated("Use project", ReplaceWith("project"))
  @JvmField protected val myProject: Project
) : StatusBarWidget {
  @JvmField
  protected var myStatusBar: StatusBar? = null
  @JvmField
  protected val myConnection: MessageBusConnection

  @Volatile
  protected var isDisposed: Boolean = false
    private set

  @Suppress("DEPRECATION")
  protected val project: Project
    get() = myProject

  protected val statusBar: StatusBar?
    get() = myStatusBar

  private val helper = EditorBasedWidgetHelper(project)

  init {
    @Suppress("LeakingThis")
    myConnection = project.messageBus.connect(this)

    @Suppress("LeakingThis")
    registerCustomListeners(myConnection)
  }

  @Deprecated("Custom listeners should be registered after class initialization to prevent using not fully constructed class in the listeners.")
  protected open fun registerCustomListeners(connection: MessageBusConnection) {
  }

  protected open fun getEditor(): Editor? {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return FileEditorManager.getInstance(project).selectedTextEditor
    }

    val fileEditor = StatusBarUtil.getCurrentFileEditor(myStatusBar)
    return if (fileEditor is TextEditor) fileEditor.editor else null
  }

  open fun isOurEditor(editor: Editor?): Boolean = helper.isOurEditor(editor, myStatusBar)

  fun getFocusedComponent(): Component? = helper.getFocusedComponent()

  fun getFocusedEditor(): Editor? = helper.getFocusedEditor(myStatusBar)

  protected open fun getSelectedFile(): VirtualFile? {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return FileEditorManager.getInstance(project).selectedTextEditor?.virtualFile
    }
    return (StatusBarUtil.getCurrentFileEditor(myStatusBar) as? TextEditor)?.file
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