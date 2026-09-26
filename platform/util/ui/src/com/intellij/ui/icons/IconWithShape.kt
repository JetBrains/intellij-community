// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.ui.svg.ParsedSvgDocument
import org.jetbrains.annotations.ApiStatus
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.Area
import javax.swing.Icon

@ApiStatus.Experimental
interface IconWithShape : Icon {
  fun getShape(): Shape?
}

@ApiStatus.Internal
fun computeShape(svg: ParsedSvgDocument): Shape {
  val shape = svg.document.computeShape()
  return applyStroke(shape)
}

private fun applyStroke(shape: Shape): Area {
  val stroke = BasicStroke(2.0f)
  val result = Area(stroke.createStrokedShape(shape))
  result.add(Area(shape))
  return result
}
