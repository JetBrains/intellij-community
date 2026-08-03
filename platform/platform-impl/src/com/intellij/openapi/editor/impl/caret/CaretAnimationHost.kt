// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret

import com.intellij.openapi.editor.impl.caret.model.CaretAnimationSettings
import com.intellij.openapi.editor.impl.caret.model.CaretPlacement
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle

internal interface CaretGeometry {
  fun placements(): List<CaretPlacement>

  fun currentLocations(): Array<CaretRectangle>
}

internal interface CaretPresentation {
  fun showAt(locations: Array<CaretRectangle>)

  fun fadeTo(opacity: Float)

  fun repaint(locations: Array<CaretRectangle>)

  fun repaintCurrent()
}

internal interface CaretAnimationConditions {
  fun isCaretShown(): Boolean

  fun isFrozen(): Boolean

  fun millisSinceActivity(): Long

  fun settings(): CaretAnimationSettings
}

internal data class CaretAnimationHost(
  val geometry: CaretGeometry,
  val presentation: CaretPresentation,
  val conditions: CaretAnimationConditions,
)
