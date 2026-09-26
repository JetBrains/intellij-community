/*
 * Copyright (C) 2015 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.vectorDrawable.svgConverter

import com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering.ComposeResourcesVdPath
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D

/**
 * Based on [com.android.ide.common.vectordrawable.VdPath.Node]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/719cd9aba22ed37b7e1ace12ca545d369030f355:sdk-common/src/main/java/com/android/ide/common/vectordrawable/VdPath.java
 *
 * Key differences:
 * - Refactored Java class-scoped methods into top-level internal functions
 * - Replaced manual loop state in [hasRelMoveAfterClose] with functional [zipWithNext]
 */
internal fun hasRelMoveAfterClose(nodes: Array<ComposeResourcesVdPath.Node>): Boolean {
  return nodes.asSequence().zipWithNext().any { (prev, curr) ->
    (prev.type == 'z' || prev.type == 'Z') && curr.type == 'm'
  }
}

internal fun transform(totalTransform: AffineTransform, nodes: Array<ComposeResourcesVdPath.Node>) {
  val currentPoint = Point2D.Float()
  val currentSegmentStartPoint = Point2D.Float()
  var previousType = INIT_TYPE

  for (node in nodes) {
    transformNode(node, totalTransform, currentPoint, currentSegmentStartPoint, previousType)
    previousType = node.type
  }
}

internal fun nodeListToString(nodes: Array<ComposeResourcesVdPath.Node>, svgTree: ComposeResourcesSvgTree): String = buildString {
  for (node in nodes) {
    append(node.type)
    val len = node.params.size
    val implicitLineTo = (node.type == 'm' || node.type == 'M') && len > 2
    val lineToType = if (node.type == 'm') 'l' else 'L'

    for (j in 0 until len) {
      if (j > 0) append(if (j % 2 != 0) "," else " ")
      if (implicitLineTo && j == 2) append(lineToType)
      val param = node.params[j]
      require(param.isFinite()) { "Invalid number: $param" }
      append(svgTree.formatCoordinate(param.toDouble()))
    }
  }
}

private const val INIT_TYPE = ' '

private val COMMAND_STEP_MAP = mapOf(
  'z' to 0, 'Z' to 0,
  'm' to 2, 'M' to 2,
  'l' to 2, 'L' to 2,
  'h' to 1, 'H' to 1,
  'v' to 1, 'V' to 1,
  'c' to 6, 'C' to 6,
  's' to 4, 'S' to 4,
  'q' to 4, 'Q' to 4,
  't' to 2, 'T' to 2,
  'a' to 7, 'A' to 7
)

private fun transformNode(
  node: ComposeResourcesVdPath.Node,
  totalTransform: AffineTransform,
  currentPoint: Point2D.Float,
  currentSegmentStartPoint: Point2D.Float,
  previousType: Char,
) {
  val paramsLen = node.params.size
  val tempParams = FloatArray(2 * paramsLen)
  val step = COMMAND_STEP_MAP[node.type] ?: 0
  val needsTransform = !isTranslationOnly(totalTransform)

  var currentX = currentPoint.x
  var currentY = currentPoint.y
  var currentSegmentStartX = currentSegmentStartPoint.x
  var currentSegmentStartY = currentSegmentStartPoint.y

  when (node.type) {
    'z', 'Z' -> {
      currentX = currentSegmentStartX
      currentY = currentSegmentStartY
    }

    'M' -> {
      currentSegmentStartX = node.params[0]
      currentSegmentStartY = node.params[1]
      currentX = node.params[paramsLen - 2]
      currentY = node.params[paramsLen - 1]
      totalTransform.transform(node.params, 0, node.params, 0, paramsLen / 2)
    }

    'L', 'T', 'C', 'S', 'Q' -> {
      currentX = node.params[paramsLen - 2]
      currentY = node.params[paramsLen - 1]
      totalTransform.transform(node.params, 0, node.params, 0, paramsLen / 2)
    }

    'm' -> {
      if (previousType == 'z' || previousType == 'Z') {
        node.type = 'M'
        node.params[0] += currentSegmentStartX
        node.params[1] += currentSegmentStartY
        currentSegmentStartX = node.params[0]
        currentSegmentStartY = node.params[1]
        for (i in step until paramsLen step step) {
          node.params[i] += node.params[i - step]
          node.params[i + 1] += node.params[i + 1 - step]
        }
        currentX = node.params[paramsLen - 2]
        currentY = node.params[paramsLen - 1]
        totalTransform.transform(node.params, 0, node.params, 0, paramsLen / 2)
      }
      else {
        currentX += node.params[0]
        currentY += node.params[1]
        currentSegmentStartX = currentX
        currentSegmentStartY = currentY

        when {
          previousType == INIT_TYPE -> totalTransform.transform(node.params, 0, node.params, 0, 1)
          needsTransform -> deltaTransform(totalTransform, node.params, 0, 2)
        }

        for (i in 2 until paramsLen step step) {
          currentX += node.params[i]
          currentY += node.params[i + 1]
        }

        if (needsTransform) deltaTransform(totalTransform, node.params, 2, paramsLen - 2)
      }
    }

    'l', 't', 'c', 's', 'q' -> {
      for (i in 0 until paramsLen step step) {
        currentX += node.params[i + step - 2]
        currentY += node.params[i + step - 1]
      }
      if (needsTransform) deltaTransform(totalTransform, node.params, 0, paramsLen)
    }

    'H' -> {
      node.type = 'L'
      for (i in 0 until paramsLen) {
        tempParams[i * 2] = node.params[i]
        tempParams[i * 2 + 1] = currentY
        currentX = node.params[i]
      }
      totalTransform.transform(tempParams, 0, tempParams, 0, paramsLen)
      node.params = tempParams.copyOf(paramsLen * 2)
    }

    'V' -> {
      node.type = 'L'
      for (i in 0 until paramsLen) {
        tempParams[i * 2] = currentX
        tempParams[i * 2 + 1] = node.params[i]
        currentY = node.params[i]
      }
      totalTransform.transform(tempParams, 0, tempParams, 0, paramsLen)
      node.params = tempParams.copyOf(paramsLen * 2)
    }

    'h' -> {
      for (i in 0 until paramsLen) {
        currentX += node.params[i]
        tempParams[i * 2] = node.params[i]
        tempParams[i * 2 + 1] = 0f
      }
      if (needsTransform) {
        node.type = 'l'
        deltaTransform(totalTransform, tempParams, 0, 2 * paramsLen)
        node.params = tempParams.copyOf(paramsLen * 2)
      }
    }

    'v' -> {
      for (i in 0 until paramsLen) {
        tempParams[i * 2] = 0f
        tempParams[i * 2 + 1] = node.params[i]
        currentY += node.params[i]
      }
      if (needsTransform) {
        node.type = 'l'
        deltaTransform(totalTransform, tempParams, 0, 2 * paramsLen)
        node.params = tempParams.copyOf(paramsLen * 2)
      }
    }

    'A' -> {
      for (i in 0 until paramsLen step step) {
        if (needsTransform) {
          applyEllipseTransform(node, i, totalTransform, currentX, currentY, node.params[i + 5], node.params[i + 6])
        }
        currentX = node.params[i + 5]
        currentY = node.params[i + 6]
        totalTransform.transform(node.params, i + 5, node.params, i + 5, 1)
      }
    }

    'a' -> {
      for (i in 0 until paramsLen step step) {
        val oldX = currentX
        val oldY = currentY
        currentX += node.params[i + 5]
        currentY += node.params[i + 6]

        if (needsTransform) {
          applyEllipseTransform(node, i, totalTransform, oldX, oldY, currentX, currentY)
          deltaTransform(totalTransform, node.params, i + 5, 2)
        }
      }
    }

    else -> throw IllegalStateException("Unexpected type ${node.type}")
  }

  currentPoint.setLocation(currentX, currentY)
  currentSegmentStartPoint.setLocation(currentSegmentStartX, currentSegmentStartY)
}

private fun applyEllipseTransform(
  node: ComposeResourcesVdPath.Node, i: Int, transform: AffineTransform,
  startX: Float, startY: Float, endX: Float, endY: Float,
) {
  val solver = ComposeResourcesEllipseSolver(
    transform, startX, startY,
    node.params[i], node.params[i + 1], node.params[i + 2],
    node.params[i + 3], node.params[i + 4], endX, endY
  )
  node.params[i] = solver.majorAxis
  node.params[i + 1] = solver.minorAxis
  node.params[i + 2] = solver.rotationDegree
  if (solver.directionChanged) node.params[i + 4] = 1 - node.params[i + 4]
}

private fun isTranslationOnly(totalTransform: AffineTransform): Boolean =
  totalTransform.type == AffineTransform.TYPE_IDENTITY || totalTransform.type == AffineTransform.TYPE_TRANSLATION

private fun deltaTransform(totalTransform: AffineTransform, coordinates: FloatArray, offset: Int, paramsLen: Int) {
  val doubleArray = DoubleArray(paramsLen) { coordinates[it + offset].toDouble() }
  totalTransform.deltaTransform(doubleArray, 0, doubleArray, 0, paramsLen / 2)
  for (i in 0 until paramsLen) coordinates[i + offset] = doubleArray[i].toFloat()
}

