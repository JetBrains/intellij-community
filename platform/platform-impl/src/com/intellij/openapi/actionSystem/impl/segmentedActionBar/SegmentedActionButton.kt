// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton

open class SegmentedActionButton(action: AnAction,
                                 presentation: Presentation,
                                 place: String) : ActionButton(action, presentation, place,
                                                               ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {

  override fun getButtonLook(): ActionButtonLook {
    return SegmentedActionToolbarComponent.segmentedButtonLook
  }
}