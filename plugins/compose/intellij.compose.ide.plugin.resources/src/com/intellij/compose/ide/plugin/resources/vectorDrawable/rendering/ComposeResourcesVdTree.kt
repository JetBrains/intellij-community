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

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.w3c.dom.Document
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.geom.NoninvertibleTransformException
import java.awt.image.BufferedImage

/**
 * Based on [com.android.ide.common.vectordrawable.VdTree]
 * Java source: https://cs.android.com/android-studio/platform/tools/base/+/20e699a4b31ae8f5f2a7a621d0bc7d8642ae3536:sdk-common/src/main/java/com/android/ide/common/vectordrawable/VdTree.java
 *
 * Key differences from Android original:
 * - Uses [JBColor] and [Gray] instead of `java.awt.Color` for theme-aware colors
 * - Uses [UIUtil.createImage] instead of `AssetUtil.newArgbBufferedImage`
 * - Uses [use] extension for proper [Graphics.dispose] calls (prevents memory leaks)
 * - Added [Graphics2D.clipRect] in [drawIntoImage] to prevent drawing outside image bounds
 * - Safe parsing: uses `toFloatOrNull()` instead of `Float.parseFloat()` to prevent UI thread crashes
 * - Scale is applied via matrix transform (Android applies scale directly to Graphics2D)
 */
internal class ComposeResourceDrawableTree {
  var baseWidth: Float = 1f
    private set
  var baseHeight: Float = 1f
    private set
  var portWidth: Float = 1f
    private set
  var portHeight: Float = 1f
    private set

  private val rootGroup = ComposeResourcesVdGroup()
  private var rootAlpha = 1f
  private var rootTint = 0

  fun drawIntoImage(image: BufferedImage) {
    val width = image.width
    val height = image.height

    (image.graphics as Graphics2D).use { gFinal ->
      // Fix: clip to image bounds to prevent drawing artifacts outside the image area
      gFinal.clipRect(0, 0, width, height)
      gFinal.color = Gray.TRANSPARENT
      gFinal.fillRect(0, 0, width, height)

      if (rootAlpha < 1f) {
        val alphaImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        (alphaImage.graphics as Graphics2D).use { gTemp ->
          drawTree(gTemp, width, height)
        }
        gFinal.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, rootAlpha)
        gFinal.drawImage(alphaImage, 0, 0, null)
      }
      else {
        drawTree(gFinal, width, height)
      }

      if (rootTint != 0) {
        val tintImage = UIUtil.createImage(null, width, height, BufferedImage.TYPE_INT_ARGB)
        (tintImage.graphics as Graphics2D).use { gTemp ->
          gTemp.paint = JBColor(rootTint, rootTint)
          gTemp.fillRect(0, 0, width, height)
        }

        gFinal.composite = AlphaComposite.SrcIn

        try {
          gFinal.drawImage(tintImage, gFinal.transform.createInverse(), null)
        }
        catch (_: NoninvertibleTransformException) {
        }
      }
    }
  }

  fun parse(doc: Document) {
    val rootNodeList = doc.getElementsByTagName("vector")
    check(rootNodeList.length == 1) { "Expected exactly one <vector> element" }

    val rootNode = rootNodeList.item(0)
    parseRootNode(rootNode)
    parseTree(rootNode, rootGroup)
  }

  private fun drawTree(g: Graphics2D, w: Int, h: Int) {
    val scaleX = w.toFloat() / portWidth
    val scaleY = h.toFloat() / portHeight
    val rootMatrix = AffineTransform()
    rootGroup.draw(g, rootMatrix, scaleX, scaleY)
  }

  private fun parseRootNode(rootNode: Node) {
    if (rootNode.hasAttributes()) {
      parseSize(rootNode.attributes)
    }
  }

  private fun parseSize(attributes: NamedNodeMap) {
    for (i in 0 until attributes.length) {
      val name = attributes.item(i).nodeName
      val value = attributes.item(i).nodeValue

      when (name) {
        "android:width" -> baseWidth = parseDimension(value)
        "android:height" -> baseHeight = parseDimension(value)
        "android:viewportWidth" -> portWidth = value.toFloat()
        "android:viewportHeight" -> portHeight = value.toFloat()
        "android:alpha" -> rootAlpha = value.toFloat()
        "android:tint" -> rootTint = ComposeResourcesVdElement.parseColorValue(value)
      }
    }
  }

  private fun parseDimension(value: String): Float {
    val match = DIMENSION_PATTERN.matchEntire(value)
    return match?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
  }

  companion object {
    private val DIMENSION_PATTERN = Regex("""^\s*(\d+(?:\.\d+)?)\s*[a-zA-Z]+\s*$""")

    private fun parseTree(currentNode: Node, currentGroup: ComposeResourcesVdGroup) {
      val childrenNodes = currentNode.childNodes

      for (i in 0 until childrenNodes.length) {
        val child = childrenNodes.item(i)
        if (child.nodeType != Node.ELEMENT_NODE) continue

        when (child.nodeName) {
          "group" -> {
            val newGroup = ComposeResourcesVdGroup().apply { parseAttributes(child.attributes) }
            currentGroup.add(newGroup)
            parseTree(child, newGroup)
          }
          "path" -> {
            val newPath = ComposeResourcesVdPath().apply { parseAttributes(child.attributes) }
            newPath.addGradientIfExists(child)
            currentGroup.add(newPath)
          }
          "clip-path" -> {
            val newClipPath = ComposeResourcesVdPath().apply { parseAttributes(child.attributes) }
            newClipPath.isClipPath = true
            currentGroup.add(newClipPath)
          }
        }
      }
    }
  }
}

internal inline fun <T : Graphics, R> T.use(block: (T) -> R): R {
  try {
    return block(this)
  }
  finally {
    this.dispose()
  }
}