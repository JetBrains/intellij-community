// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.collaboration.ui.codereview.BaseHtmlEditorPane
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.Graphics2DDelegate
import com.intellij.util.ui.ExtendableHTMLViewFactory
import com.intellij.util.ui.HTMLEditorKitBuilder
import java.awt.*
import java.awt.image.ImageObserver
import javax.swing.text.Element
import javax.swing.text.FlowView
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.ImageView
import javax.swing.text.html.InlineView

internal class HtmlEditorPane() : BaseHtmlEditorPane() {

  init {
    editorKit = HTMLEditorKitBuilder().withViewFactoryExtensions(ExtendableHTMLViewFactory.Extensions.WORD_WRAP, GHExtensions()).build()
  }

  constructor(@NlsSafe body: String) : this() {
    setBody(body)
  }

  private class GHExtensions : ExtendableHTMLViewFactory.Extension {
    override fun invoke(elem: Element, view: View): View {
      if (view is ImageView) {
        return MyScalingImageView(elem)
      }
      if (ICON_INLINE_ELEMENT_NAME == elem.name) {
        val icon = elem.attributes.getAttribute(HTML.Attribute.SRC)?.let {
          val path = it as String

          IconLoader.findIcon(path, ExtendableHTMLViewFactory::class.java, true, false)
        }

        if (icon != null) {
          return object : InlineView(elem) {

            override fun getPreferredSpan(axis: Int): Float {
              when (axis) {
                X_AXIS -> return icon.iconWidth.toFloat() + super.getPreferredSpan(axis)
                else -> return super.getPreferredSpan(axis)
              }
            }

            override fun paint(g: Graphics, allocation: Shape) {
              super.paint(g, allocation)
              icon.paintIcon(null, g, allocation.bounds.x, allocation.bounds.y)
            }
          }
        }
      }
      return view
    }
  }

  // Copied from: com.intellij.codeInsight.documentation.render.DocRenderer.MyScalingImageView
  private class MyScalingImageView(element: Element) : ImageView(element) {

    private var myAvailableWidth = 0

    override fun getResizeWeight(axis: Int) = 1

    override fun getMaximumSpan(axis: Int) = getPreferredSpan(axis)

    override fun getPreferredSpan(axis: Int): Float {
      val baseSpan = super.getPreferredSpan(axis)
      if (axis == X_AXIS) return baseSpan

      var availableWidth = getAvailableWidth()
      if (availableWidth <= 0) return baseSpan

      val baseXSpan = super.getPreferredSpan(X_AXIS)
      if (baseXSpan <= 0) return baseSpan

      if (availableWidth > baseXSpan) {
        availableWidth = baseXSpan.toInt()
      }
      if (myAvailableWidth > 0 && availableWidth != myAvailableWidth) {
        preferenceChanged(null, false, true)
      }
      myAvailableWidth = availableWidth

      return baseSpan * availableWidth / baseXSpan
    }

    private fun getAvailableWidth(): Int {
      var v: View? = this
      while (v != null) {
        val parent = v.parent
        if (parent is FlowView) {
          val childCount = parent.getViewCount()
          for (i in 0 until childCount) {
            if (parent.getView(i) === v) {
              return parent.getFlowSpan(i)
            }
          }
        }
        v = parent
      }
      return 0
    }

    override fun paint(g: Graphics, a: Shape) {
      val targetRect = if (a is Rectangle) a else a.bounds
      val scalingGraphics = object : Graphics2DDelegate(g as Graphics2D) {
        override fun drawImage(img: Image, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver): Boolean {
          var newWidth = width
          var newHeight = height

          val maxWidth = 0.coerceAtLeast(targetRect.width - 2 * (x - targetRect.x)) // assuming left and right insets are the same
          val maxHeight = 0.coerceAtLeast(targetRect.height - 2 * (y - targetRect.y)) // assuming top and bottom insets are the same

          if (width > maxWidth) {
            newHeight = height * maxWidth / width
            newWidth = maxWidth
          }

          if (height > maxHeight) {
            newWidth = width * maxHeight / height
            newHeight = maxHeight
          }

          return super.drawImage(img, x, y, newWidth, newHeight, observer)
        }
      }
      super.paint(scalingGraphics, a)
    }
  }

  companion object {
    private const val ICON_INLINE_ELEMENT_NAME = "icon-inline" // NON-NLS
  }
}