// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.attach.dialog

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.actionSystem.ex.TooltipDescriptionProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class AttachDialogSettings : NonEmptyActionGroup(), RightAlignedToolbarAction, TooltipDescriptionProvider, DumbAware {
  init {
    templatePresentation.icon =
      if (ExperimentalUI.isNewUI()) AllIcons.Actions.More
      else AllIcons.General.GearPlain
  }
}