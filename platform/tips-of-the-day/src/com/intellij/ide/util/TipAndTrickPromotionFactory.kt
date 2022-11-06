// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

/**
 * Adds an ability to place custom UI component to the top of Tip of the day dialog for any particular tip.
 * @see [TipPanel.setPromotionForCurrentTip]
 */
@ApiStatus.Internal
interface TipAndTrickPromotionFactory {
  fun createPromotionPanel(project: Project, tip: TipAndTrickBean): JPanel?

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName<TipAndTrickPromotionFactory>("com.intellij.tipAndTrickPromotionFactory")
  }
}
