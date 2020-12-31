// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.kimpl

import com.intellij.openapi.ui.popup.Balloon
import java.awt.Dimension
import javax.swing.JComponent

data class LearningBalloonConfig(
  val side: Balloon.Position,
  val dimension: Dimension = Dimension(500, 200),
  val duplicateMessage: Boolean = false,
  val highlightingComponent: JComponent? = null,
  val gotItCallBack: (() -> Unit)? = null
)