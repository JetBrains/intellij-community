// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.external.ExternalDiffTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.EditorTabPreviewBase
import com.intellij.openapi.vcs.changes.ui.TreeHandlerChangesTreeTracker
import com.intellij.openapi.vcs.changes.ui.TreeHandlerEditorDiffPreview
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.lang.ref.WeakReference
import javax.swing.JComponent
import kotlin.math.roundToInt

private fun toggleDiffPreviewOnPropertyChange(uiProperties: VcsLogUiProperties,
                                              parent: Disposable,
                                              showDiffPreview: (Boolean) -> Unit) =
  onBooleanPropertyChange(uiProperties, CommonUiProperties.SHOW_DIFF_PREVIEW, parent, showDiffPreview)


private fun toggleDiffPreviewOrientationOnPropertyChange(uiProperties: VcsLogUiProperties,
                                                         parent: Disposable,
                                                         changeShowDiffPreviewOrientation: (Boolean) -> Unit) =
  onBooleanPropertyChange(uiProperties, MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT, parent, changeShowDiffPreviewOrientation)


private fun onBooleanPropertyChange(uiProperties: VcsLogUiProperties,
                                    property: VcsLogUiProperty<Boolean>,
                                    parent: Disposable,
                                    onPropertyChangeAction: (Boolean) -> Unit) {
  val propertiesChangeListener: PropertiesChangeListener = object : PropertiesChangeListener {
    override fun <T> onPropertyChanged(p: VcsLogUiProperty<T>) {
      if (property == p) {
        onPropertyChangeAction(uiProperties.get(property))
      }
    }
  }
  uiProperties.addChangeListener(propertiesChangeListener, parent)
}

class FrameDiffPreview(val previewDiff: DiffEditorViewer,
                       uiProperties: VcsLogUiProperties,
                       mainComponent: JComponent,
                       @NonNls splitterProportionKey: String,
                       vertical: Boolean = false,
                       defaultProportion: Float = 0.7f) {
  private val previewDiffSplitter: Splitter = OnePixelSplitter(vertical, splitterProportionKey, defaultProportion)

  val mainComponent: JComponent
    get() = previewDiffSplitter

  init {
    previewDiffSplitter.firstComponent = mainComponent

    toggleDiffPreviewOnPropertyChange(uiProperties, previewDiff.disposable, ::showDiffPreview)
    toggleDiffPreviewOrientationOnPropertyChange(uiProperties, previewDiff.disposable, ::changeDiffPreviewOrientation)
    invokeLater { showDiffPreview(uiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)) }
  }

  private fun showDiffPreview(state: Boolean) {
    previewDiffSplitter.secondComponent = if (state) previewDiff.component else null
    previewDiffSplitter.secondComponent?.let {
      val defaultMinimumSize = it.minimumSize
      val actionButtonSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      it.minimumSize = JBUI.size(defaultMinimumSize.width.coerceAtMost((actionButtonSize.width * 1.5f).roundToInt()),
                                 defaultMinimumSize.height.coerceAtMost((actionButtonSize.height * 1.5f).roundToInt()))
    }
    if (!state) {
      TreeHandlerChangesTreeTracker.clearDiffViewer(previewDiff)
    }
  }

  private fun changeDiffPreviewOrientation(bottom: Boolean) {
    previewDiffSplitter.orientation = bottom
  }
}

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

  override fun handleEscapeKey() {
    closePreview()
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
}
