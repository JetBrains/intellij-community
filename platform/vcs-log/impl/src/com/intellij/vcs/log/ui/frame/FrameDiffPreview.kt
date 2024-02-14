// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.vcs.changes.ui.TreeHandlerChangesTreeTracker
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.impl.CommonUiProperties
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import kotlin.math.roundToInt

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