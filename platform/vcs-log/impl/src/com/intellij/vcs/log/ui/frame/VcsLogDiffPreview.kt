// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.DiffPreviewProvider
import com.intellij.openapi.vcs.changes.EditorTabPreview
import com.intellij.openapi.vcs.changes.PreviewDiffVirtualFile
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

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
    updatePreview(state)
  }

  private fun changeDiffPreviewOrientation(bottom: Boolean) {
    previewDiffSplitter.orientation = bottom
  }
}

abstract class EditorDiffPreview(private val uiProperties: VcsLogUiProperties,
                                 private val owner: Disposable) : DiffPreviewProvider {

  protected fun init(project: Project) {
    toggleDiffPreviewOnPropertyChange(uiProperties, owner) { state ->
      if (state) {
        openPreviewInEditor(project, this, getOwnerComponent())
      }
      else {
        //'equals' for such files is overridden and means the equality of its owner
        FileEditorManager.getInstance(project).closeFile(PreviewDiffVirtualFile(this))
      }
    }

    @Suppress("LeakingThis")
    addSelectionListener {
      if (uiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)) {
        openPreviewInEditor(project, this, getOwnerComponent())
      }
    }
  }

  override fun getOwner(): Disposable = owner

  abstract fun getOwnerComponent(): JComponent

  abstract fun addSelectionListener(listener: () -> Unit)
}

class VcsLogEditorDiffPreview(project: Project, uiProperties: VcsLogUiProperties, private val mainFrame: MainFrame) :
  EditorDiffPreview(uiProperties, mainFrame.changesBrowser) {

  init {
    init(project)
  }

  override fun createDiffRequestProcessor(): DiffRequestProcessor {
    val preview = mainFrame.createDiffPreview(true, owner)
    preview.updatePreview(true)
    return preview
  }

  override fun getEditorTabName(): @Nls String {
    return VcsLogBundle.message("vcs.log.diff.preview.editor.tab.name")
  }

  override fun getOwnerComponent(): JComponent = mainFrame.changesBrowser.preferredFocusedComponent

  override fun addSelectionListener(listener: () -> Unit) {
    mainFrame.changesBrowser.viewer.addSelectionListener(Runnable {
      if (mainFrame.changesBrowser.selectedChanges.isNotEmpty()) {
        listener()
      }
    }, owner)
  }
}

private fun openPreviewInEditor(project: Project, diffPreviewProvider: DiffPreviewProvider, componentToFocus: JComponent) {
  val escapeHandler = Runnable {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
    toolWindow?.activate({ IdeFocusManager.getInstance(project).requestFocus(componentToFocus, true) }, false)
  }
  EditorTabPreview.openPreview(project, PreviewDiffVirtualFile(diffPreviewProvider), false, escapeHandler)
}