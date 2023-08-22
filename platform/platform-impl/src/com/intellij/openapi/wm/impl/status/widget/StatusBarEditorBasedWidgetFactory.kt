// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.StatusBarUtil.getCurrentFileEditor
import com.intellij.openapi.wm.impl.status.StatusBarUtil.getCurrentTextEditor

abstract class StatusBarEditorBasedWidgetFactory : StatusBarWidgetFactory {
  override fun canBeEnabledOn(statusBar: StatusBar): Boolean = getTextEditor(statusBar) != null

  protected fun getFileEditor(statusBar: StatusBar): FileEditor? = getCurrentFileEditor(statusBar)

  protected fun getTextEditor(statusBar: StatusBar): Editor? = getCurrentTextEditor(statusBar)
}