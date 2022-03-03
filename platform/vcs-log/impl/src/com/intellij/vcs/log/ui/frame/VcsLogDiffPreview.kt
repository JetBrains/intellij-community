// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.SimpleDiffRequestChain
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.EditorTabPreview.Companion.registerEscapeHandler
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
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
  uiProperties.addChangeListener(propertiesChangeListener)
  Disposer.register(parent, Disposable { uiProperties.removeChangeListener(propertiesChangeListener) })
}

abstract class FrameDiffPreview<D : DiffRequestProcessor>(protected val previewDiff: D,
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

    toggleDiffPreviewOnPropertyChange(uiProperties, previewDiff, ::showDiffPreview)
    toggleDiffPreviewOrientationOnPropertyChange(uiProperties, previewDiff, ::changeDiffPreviewOrientation)
    invokeLater { showDiffPreview(uiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)) }
  }

  abstract fun updatePreview(state: Boolean)

  private fun showDiffPreview(state: Boolean) {
    previewDiffSplitter.secondComponent = if (state) previewDiff.component else null
    previewDiffSplitter.secondComponent?.let {
      val defaultMinimumSize = it.minimumSize
      val actionButtonSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      it.minimumSize = JBUI.size(defaultMinimumSize.width.coerceAtMost((actionButtonSize.width * 1.5f).roundToInt()),
                                 defaultMinimumSize.height.coerceAtMost((actionButtonSize.height * 1.5f).roundToInt()))
    }
    updatePreview(state)
  }

  private fun changeDiffPreviewOrientation(bottom: Boolean) {
    previewDiffSplitter.orientation = bottom
  }
}

abstract class EditorDiffPreview(protected val project: Project,
                                 private val owner: Disposable) : DiffPreviewProvider, DiffPreview {

  private val previewFileDelegate = lazy { PreviewDiffVirtualFile(this) }
  private val previewFile by previewFileDelegate

  protected fun init() {
    @Suppress("LeakingThis")
    addSelectionListener {
      updatePreview(true)
    }
  }

  override fun setPreviewVisible(isPreviewVisible: Boolean, focus: Boolean) {
    if (isPreviewVisible) openPreviewInEditor(focus) else closePreview()
  }

  fun openPreviewInEditor(focusEditor: Boolean) {
    val currentFocusOwner = IdeFocusManager.getInstance(project).focusOwner
    val escapeHandler = Runnable {
      closePreview()
      val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
      toolWindow?.activate({ IdeFocusManager.getInstance(project).requestFocus(currentFocusOwner, true) }, false)
    }

    registerEscapeHandler(previewFile, escapeHandler)
    EditorTabPreview.openPreview(project, previewFile, focusEditor)
  }

  fun closePreview() {
    if (previewFileDelegate.isInitialized()) {
      FileEditorManager.getInstance(project).closeFile(previewFile)
    }
  }

  override fun getOwner(): Disposable = owner

  abstract fun getOwnerComponent(): JComponent

  abstract fun addSelectionListener(listener: () -> Unit)
}

class VcsLogEditorDiffPreview(project: Project, private val changesBrowser: VcsLogChangesBrowser) :
  EditorDiffPreview(project, changesBrowser), ChainBackedDiffPreviewProvider {

  init {
    init()
  }

  override fun createDiffRequestProcessor(): DiffRequestProcessor {
    val preview = changesBrowser.createChangeProcessor(true)
    preview.updatePreview(true)
    return preview
  }

  override fun getEditorTabName(processor: DiffRequestProcessor?): @Nls String {
    val filePath = (processor as? VcsLogChangeProcessor)?.currentChange?.filePath
    return if (filePath == null) VcsLogBundle.message("vcs.log.diff.preview.editor.empty.tab.name")
    else VcsLogBundle.message("vcs.log.diff.preview.editor.tab.name", filePath.name)
  }

  override fun getOwnerComponent(): JComponent = changesBrowser.preferredFocusedComponent

  override fun addSelectionListener(listener: () -> Unit) {
    changesBrowser.viewer.addSelectionListener(Runnable {
      if (changesBrowser.selectedChanges.isNotEmpty()) {
        listener()
      }
    }, owner)
    changesBrowser.addListener(VcsLogChangesBrowser.Listener { updatePreview(true) }, owner)
  }

  override fun createDiffRequestChain(): DiffRequestChain? {
    val producers = VcsTreeModelData.getListSelectionOrAll(changesBrowser.viewer).map {
      changesBrowser.getDiffRequestProducer(it, false)
    }
    return SimpleDiffRequestChain.fromProducers(producers.list, producers.selectedIndex)
  }
}
