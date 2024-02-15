// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import kotlin.math.roundToInt

abstract class FrameDiffPreview(uiProperties: VcsLogUiProperties,
                                mainComponent: JComponent,
                                @NonNls splitterProportionKey: String,
                                vertical: Boolean = false,
                                defaultProportion: Float = 0.7f,
                                parentDisposable: Disposable) : Disposable {
  private val previewDiffSplitter: Splitter = OnePixelSplitter(vertical, splitterProportionKey, defaultProportion)

  val mainComponent: JComponent get() = previewDiffSplitter

  private var isDisposed = false
  private var diffViewer: DiffEditorViewer? = null

  init {
    previewDiffSplitter.firstComponent = mainComponent

    toggleDiffPreviewOnPropertyChange(uiProperties, this, ::showDiffPreview)
    toggleDiffPreviewOrientationOnPropertyChange(uiProperties, this, ::changeDiffPreviewOrientation)
    invokeLater { showDiffPreview(uiProperties.get(CommonUiProperties.SHOW_DIFF_PREVIEW)) }

    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {
    isDisposed = true

    diffViewer?.let { Disposer.dispose(it.disposable) }
    diffViewer = null
  }

  protected abstract fun createViewer(): DiffEditorViewer

  fun getPreferredFocusedComponent(): JComponent? {
    return diffViewer?.preferredFocusedComponent
  }

  private fun showDiffPreview(state: Boolean) {
    if (isDisposed) return

    val isShown = diffViewer != null
    if (state == isShown) return

    if (state) {
      val newDiffViewer = createViewer()
      val component = newDiffViewer.component
      previewDiffSplitter.secondComponent = component
      val defaultMinimumSize = component.minimumSize
      val actionButtonSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      component.minimumSize = JBUI.size(defaultMinimumSize.width.coerceAtMost((actionButtonSize.width * 1.5f).roundToInt()),
                                        defaultMinimumSize.height.coerceAtMost((actionButtonSize.height * 1.5f).roundToInt()))
      diffViewer = newDiffViewer
    }
    else {
      previewDiffSplitter.secondComponent = null
      Disposer.dispose(diffViewer!!.disposable)
      diffViewer = null
    }
  }

  private fun changeDiffPreviewOrientation(bottom: Boolean) {
    previewDiffSplitter.orientation = bottom
  }
}

private fun toggleDiffPreviewOnPropertyChange(uiProperties: VcsLogUiProperties, parent: Disposable, showDiffPreview: (Boolean) -> Unit) {
  onBooleanPropertyChange(uiProperties, CommonUiProperties.SHOW_DIFF_PREVIEW, parent, showDiffPreview)
}

private fun toggleDiffPreviewOrientationOnPropertyChange(uiProperties: VcsLogUiProperties, parent: Disposable,
                                                         changeShowDiffPreviewOrientation: (Boolean) -> Unit) {
  onBooleanPropertyChange(uiProperties, MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT, parent, changeShowDiffPreviewOrientation)
}

private fun onBooleanPropertyChange(uiProperties: VcsLogUiProperties,
                                    property: VcsLogUiProperties.VcsLogUiProperty<Boolean>,
                                    parent: Disposable,
                                    onPropertyChangeAction: (Boolean) -> Unit) {
  val propertiesChangeListener: VcsLogUiProperties.PropertiesChangeListener = object : VcsLogUiProperties.PropertiesChangeListener {
    override fun <T> onPropertyChanged(p: VcsLogUiProperties.VcsLogUiProperty<T>) {
      if (property == p) {
        onPropertyChangeAction(uiProperties.get(property))
      }
    }
  }
  uiProperties.addChangeListener(propertiesChangeListener, parent)
}