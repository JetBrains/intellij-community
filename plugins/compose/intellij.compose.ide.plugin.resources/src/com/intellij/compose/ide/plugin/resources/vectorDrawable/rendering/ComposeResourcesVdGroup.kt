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
package com.intellij.compose.ide.plugin.resources.vectorDrawable.rendering

import com.intellij.openapi.diagnostic.logger
import org.w3c.dom.NamedNodeMap
import java.awt.Graphics2D
import java.awt.geom.AffineTransform

/**
 * Based on [com.android.ide.common.vectordrawable.VdGroup]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/7406bf062c551d16620f84cd4b9a5f12a5043cdf:sdk-common/src/main/java/com/android/ide/common/vectordrawable/VdGroup.java
 *
 * Key differences from Android original:
 * - Uses [use] extension for proper [java.awt.Graphics.dispose] calls on created graphics context
 */
internal class ComposeResourcesVdGroup : ComposeResourcesVdElement() {
  override val isGroup: Boolean = true

  private var rotate = 0.0f
  private var pivotX = 0.0f
  private var pivotY = 0.0f
  private var scaleX = 1.0f
  private var scaleY = 1.0f
  private var translateX = 0.0f
  private var translateY = 0.0f

  private val localMatrix = AffineTransform()
  private val children = mutableListOf<ComposeResourcesVdElement>()

  override fun draw(g: Graphics2D, currentMatrix: AffineTransform, scaleX: Float, scaleY: Float) {
    val stackedMatrix = AffineTransform(currentMatrix).also { it.concatenate(localMatrix) }
    (g.create() as Graphics2D).use { gGroup ->
      children.forEach { it.draw(gGroup, stackedMatrix, scaleX, scaleY) }
    }
  }

  override fun parseAttributes(attributes: NamedNodeMap) {
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i)
      setNameValue(attr.nodeName, attr.nodeValue)
    }

    localMatrix.setToIdentity()

    applyTranslation(-pivotX, -pivotY)
    applyScale(scaleX, scaleY)
    applyRotation(rotate)
    applyTranslation(translateX + pivotX, translateY + pivotY)
  }

  override fun toString(): String =
    "Group: Name: $name translateX: $translateX translateY: $translateY scaleX: $scaleX scaleY: $scaleY pivotX: $pivotX pivotY: $pivotY rotate: $rotate"

  fun add(element: ComposeResourcesVdElement) = children.add(element)

  fun size(): Int = children.size

  private fun setNameValue(attrName: String?, value: String) {
    when (attrName) {
      "android:rotation" -> rotate = value.toFloat()
      "android:pivotX" -> pivotX = value.toFloat()
      "android:pivotY" -> pivotY = value.toFloat()
      "android:translateX" -> translateX = value.toFloat()
      "android:translateY" -> translateY = value.toFloat()
      "android:scaleX" -> scaleX = value.toFloat()
      "android:scaleY" -> scaleY = value.toFloat()
      "android:name" -> name = value
      else -> LOG.debug("Ignoring unsupported attribute: '$attrName'")
    }
  }

  private fun applyTranslation(dx: Float, dy: Float) =
    localMatrix.preConcatenate(AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble()))

  private fun applyScale(sx: Float, sy: Float) =
    localMatrix.preConcatenate(AffineTransform.getScaleInstance(sx.toDouble(), sy.toDouble()))

  private fun applyRotation(angle: Float) =
    localMatrix.preConcatenate(AffineTransform.getRotateInstance(Math.toRadians(angle.toDouble())))

  companion object {
    private val LOG = logger<ComposeResourcesVdGroup>()
  }
}