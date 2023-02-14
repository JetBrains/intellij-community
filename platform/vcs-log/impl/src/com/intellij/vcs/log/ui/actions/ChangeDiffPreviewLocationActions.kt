// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.vcs.log.impl.CommonUiProperties.SHOW_DIFF_PREVIEW
import com.intellij.vcs.log.impl.MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogInternalDataKeys

class MoveDiffPreviewToBottomAction : ChangeDiffPreviewLocationAction()

class MoveDiffPreviewToRightAction : ChangeDiffPreviewLocationAction() {

  override fun isSelected(e: AnActionEvent): Boolean = !super.isSelected(e)

  override fun setSelected(e: AnActionEvent, state: Boolean) = super.setSelected(e, !state)
}

abstract class ChangeDiffPreviewLocationAction : BooleanPropertyToggleAction() {

  override fun update(e: AnActionEvent) {
    super.update(e)

    val properties = e.getData(VcsLogInternalDataKeys.LOG_UI_PROPERTIES) ?: return
    e.presentation.isEnabled = properties.exists(SHOW_DIFF_PREVIEW) && properties[SHOW_DIFF_PREVIEW]
  }

  override fun getProperty(): VcsLogUiProperties.VcsLogUiProperty<Boolean> = DIFF_PREVIEW_VERTICAL_SPLIT
}

class DiffPreviewLocationActionGroup : DefaultActionGroup() {
  init {
    templatePresentation.isHideGroupIfEmpty = true
  }
}