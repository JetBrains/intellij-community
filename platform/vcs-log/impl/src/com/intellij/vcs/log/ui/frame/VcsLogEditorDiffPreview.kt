// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.external.ExternalDiffTool
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.EditorTabPreviewBase
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.content.Content
import com.intellij.vcs.log.VcsLogBundle
import java.awt.Component
import java.lang.ref.WeakReference

class VcsLogEditorDiffPreview(private val changesBrowser: VcsLogChangesBrowser)
  : TreeHandlerEditorDiffPreview(changesBrowser.viewer, VcsLogDiffPreviewHandler(changesBrowser)) {

  init {
    Disposer.register(changesBrowser, this)
  }

  private var oldToolWindowFocus: ToolWindowFocus? = null

  override fun openPreview(requestFocus: Boolean): Boolean {
    oldToolWindowFocus = getCurrentToolWindowFocus()
    return super.openPreview(requestFocus)
  }

  override fun returnFocusToTree() {
    restoreToolWindowFocus(oldToolWindowFocus)
  }

  private fun getCurrentToolWindowFocus(): ToolWindowFocus? {
    val focusOwner = IdeFocusManager.getInstance(project).focusOwner ?: return null
    val toolWindowId = InternalDecoratorImpl.findTopLevelDecorator(focusOwner)?.toolWindowId ?: return null
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) ?: return null
    val selectedContent = toolWindow.contentManagerIfCreated?.selectedContent ?: return null
    return ToolWindowFocus(focusOwner, toolWindowId, selectedContent)
  }

  private fun restoreToolWindowFocus(oldToolWindowFocus: ToolWindowFocus?) {
    if (oldToolWindowFocus == null) return
    val component = oldToolWindowFocus.component.get() ?: return
    val content = oldToolWindowFocus.content.get() ?: return

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(oldToolWindowFocus.toolWindowId) ?: return
    val contentManager = toolWindow.contentManagerIfCreated ?: return
    if (contentManager.getIndexOfContent(content) < 0) return
    contentManager.setSelectedContent(content)
    toolWindow.activate({ IdeFocusManager.getInstance(project).requestFocus(component, true) }, false)
  }

  private class ToolWindowFocus(component: Component, val toolWindowId: String, content: Content) {
    val component: WeakReference<Component> = WeakReference(component)
    val content: WeakReference<Content> = WeakReference(content)
  }


  override fun createViewer(): DiffEditorViewer {
    return changesBrowser.createChangeProcessor(true)
  }

  override fun getEditorTabName(wrapper: ChangeViewDiffRequestProcessor.Wrapper?): String {
    val filePath = wrapper?.filePath
    return if (filePath == null) VcsLogBundle.message("vcs.log.diff.preview.editor.empty.tab.name")
    else VcsLogBundle.message("vcs.log.diff.preview.editor.tab.name", filePath.name)
  }

  override fun performDiffAction(): Boolean {
    if (ExternalDiffTool.isEnabled()) {
      val diffProducers = VcsTreeModelData.getListSelectionOrAll(changesBrowser.viewer)
        .map { change -> changesBrowser.getDiffRequestProducer(change, false) }
      if (EditorTabPreviewBase.showExternalToolIfNeeded(project, diffProducers)) {
        return true
      }
    }

    return super.performDiffAction()
  }

  override fun isOpenPreviewWithNextDiffShortcut(): Boolean = false
}
