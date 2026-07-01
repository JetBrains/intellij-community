// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.DataManager
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorComponent
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.UIUtil
import java.awt.Component

internal object FocusedFileEditor {

  @JvmStatic
  fun getFocusedFileEditor(component: Component?): FileEditor? {
    val application = ApplicationManager.getApplication()
    val dataManager = if (application.isUnitTestMode || application.isHeadlessEnvironment) {
      DataManager.getInstance()
    } else {
      DataManager.getInstanceIfCreated()
    } ?: return null
    val focusedComponent = component ?: (dataManager as? DataManagerImpl)?.getFocusedComponent()
    if (focusedComponent != null) {
      // Fast path avoiding dataContext collection
      // Contract: removing this method should not change the behavior, only performance
      val fileEditor = getFileEditorFromComponent(focusedComponent)
      if (fileEditor != null) {
        return fileEditor
      }
    }
    val dataContext = dataManager.getDataContext(focusedComponent)
    return PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext)
  }

  private fun getFileEditorFromComponent(component: Component?): FileEditor? {
    // Only optimize the exact "focus is in an editor" case. Anything else falls back.
    if (component !is EditorComponentImpl) {
      return null
    }
    // EDITOR read reproduced from com.intellij.openapi.editor.impl.EditorComponentImpl.uiDataSnapshot. Original:
    //   if (editor.isDisposed) return
    //   if (editor.isRendererMode) return
    //   sink.set(CommonDataKeys.EDITOR, editor)
    // Diff: the disposed/renderer-mode early returns are expressed as `editor = null`.
    val editor = component.editor
    val isDisposedOrRenderer = editor.isDisposed || editor.isRendererMode

    // FILE_EDITOR from the nearest EditorCompositePanel ancestor, reproduced from
    // com.intellij.openapi.fileEditor.impl.EditorComposite (EditorCompositePanel.uiDataSnapshot):
    //   sink[PlatformCoreDataKeys.FILE_EDITOR] = composite.selectedEditor
    // The composite overrides anything more root-ward (PreCachedDataContext last-writer-wins). Any OTHER provider
    // between the composite and the focus owner could override EDITOR/FILE_EDITOR, so bail to the fallback.
    var fileEditor: FileEditor? = null
    var ancestor: Component? = UIUtil.getParent(component)
    while (ancestor != null) {
      if (ancestor is EditorCompositePanel) {
        fileEditor = ancestor.composite.selectedEditor
        break
      }
      // TextEditorComponent (and its subclass PsiAwareTextEditorComponent) is the text FileEditor's own component and
      // wraps our EditorComponentImpl in production. Its uiDataSnapshot supplies only EDITOR (== our leaf editor),
      // CARET and VIRTUAL_FILE -- never FILE_EDITOR -- so it does not affect the {EDITOR, FILE_EDITOR} result; keep
      // walking up to the EditorCompositePanel. Without this skip the fast path always bails here for real text editors.
      if (ancestor !is TextEditorComponent) {
        @Suppress("DEPRECATION")
        if (ancestor is UiDataProvider || DataManagerImpl.getDataProviderEx(ancestor) != null) {
          return null
        }
      }
      ancestor = UIUtil.getParent(ancestor)
    }

    // FILE_EDITOR fallback reproduced from com.intellij.ide.impl.dataRules.BasicUiDataRule.uiDataSnapshot
    // (the only UiDataRule producing FILE_EDITOR):
    //   if (editor != null && editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY) != true) {
    //     if (snapshot[FILE_EDITOR] == null) sink[FILE_EDITOR] = TextEditorProvider.getInstance().getTextEditor(editor)
    //   }
    if (fileEditor == null && !isDisposedOrRenderer && editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY) != true) {
      fileEditor = TextEditorProvider.getInstance().getTextEditor(editor)
    }
    return fileEditor
  }
}
