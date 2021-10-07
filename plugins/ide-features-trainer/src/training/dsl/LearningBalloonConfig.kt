// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.dsl

import com.intellij.openapi.ui.popup.Balloon
import javax.swing.JComponent

data class LearningBalloonConfig(
  val side: Balloon.Position,
  /** 0 means to use one line in the text of the balloon with automatic width detection */
  val width: Int,
  val duplicateMessage: Boolean = false,
  val highlightingComponent: JComponent? = null,
  val delayBeforeShow: Int = 0,
  val animationCycle: Int = 0,
  /** -1 means to place the pointer in the center of the balloon */
  val cornerToPointerDistance: Int = -1,
  val gotItCallBack: (() -> Unit)? = null
)