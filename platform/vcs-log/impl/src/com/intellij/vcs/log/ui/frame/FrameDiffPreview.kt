// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.tools.combined.CombinedDiffRegistry
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
import com.intellij.vcs.log.impl.onPropertyChange
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent
import kotlin.math.roundToInt

internal abstract class FrameDiffPreview(
  val uiProperties: VcsLogUiProperties,
  mainComponent: JComponent,
  @NonNls splitterProportionKey: String,
  defaultProportion: Float = 0.7f,
  parentDisposable: Disposable,
) : Disposable {
  private val previewDiffSplitter: Splitter = OnePixelSplitter(uiProperties[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT],
                                                               splitterProportionKey, defaultProportion)

  val mainComponent: JComponent get() = previewDiffSplitter

  private var isDisposed = false
  private var diffViewer: DiffEditorViewer? = null

  init {
    previewDiffSplitter.firstComponent = mainComponent

    uiProperties.onPropertyChange(this) { p ->
      if (CommonUiProperties.SHOW_DIFF_PREVIEW == p) {
        updateDiffPreviewState()
      }
      else if (MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT == p) {
        changeDiffPreviewOrientation(uiProperties[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT])
      }
    }
    CombinedDiffRegistry.addStateListener({ updateDiffPreviewState(forceRecreate = true) }, this)

    invokeLater { updateDiffPreviewState() }
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

  private fun updateDiffPreviewState(forceRecreate: Boolean = false) {
    if (isDisposed) return
    val shouldBeShown = uiProperties[CommonUiProperties.SHOW_DIFF_PREVIEW]

    val isShown = diffViewer != null
    if (!forceRecreate && shouldBeShown == isShown) return

    if (diffViewer != null) {
      previewDiffSplitter.secondComponent = null
      Disposer.dispose(diffViewer!!.disposable)
      diffViewer = null
    }

    if (shouldBeShown) {
      val newDiffViewer = createViewer()
      val component = newDiffViewer.component
      previewDiffSplitter.secondComponent = component
      val defaultMinimumSize = component.minimumSize
      val actionButtonSize = ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      component.minimumSize = JBUI.size(defaultMinimumSize.width.coerceAtMost((actionButtonSize.width * 1.5f).roundToInt()),
                                        defaultMinimumSize.height.coerceAtMost((actionButtonSize.height * 1.5f).roundToInt()))
      diffViewer = newDiffViewer
    }
  }

  private fun changeDiffPreviewOrientation(bottom: Boolean) {
    previewDiffSplitter.orientation = bottom
  }
}