// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorUtil
import com.intellij.ui.Graphics2DDelegate
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.codereview.BaseHtmlEditorPane
import org.jetbrains.plugins.github.GithubIcons
import java.awt.*
import java.awt.image.ImageObserver
import javax.swing.text.Element
import javax.swing.text.FlowView
import javax.swing.text.ParagraphView
import javax.swing.text.View
import javax.swing.text.html.BlockView
import javax.swing.text.html.ImageView
import javax.swing.text.html.StyleSheet

internal class HtmlEditorPane() : BaseHtmlEditorPane(GithubIcons::class.java) {
  constructor(@NlsSafe body: String) : this() {
    setBody(body)
  }

  override fun createViewFactory(iconsClass: Class<*>) = GHViewFactory()

  protected class GHViewFactory : BaseHtmlEditorPane.HtmlEditorViewFactory(GithubIcons::class.java) {
    override fun create(elem: Element): View {
      val view = super.create(elem)
      if (view is ImageView) {
        return MyScalingImageView(elem)
      }
      if (elem.name == "blockquote") {
        return GitHubQuoteView(elem)
      }
      if (view is ParagraphView) {
        return GHParagraphView(elem)
      }
      return view
    }
  }

  private class GitHubQuoteView(element: Element) : BlockView(element, Y_AXIS) {

    override fun setPropertiesFromAttributes() {
      super.setPropertiesFromAttributes()
      setInsets(topInset, 0, bottomInset, rightInset)
    }

    override fun getStyleSheet(): StyleSheet = super.getStyleSheet().apply {
      val borderWidth = JBUI.scale(2)
      val borderColor = ColorUtil.toHex(DarculaUIUtil.getOutlineColor(true, false))
      val padding = JBUI.scale(10)
      //language=CSS
      addRule("""
        html body blockquote p {
          border-left: ${borderWidth}px solid ${borderColor};
          padding-left: ${padding}px;
        }
      """.trimIndent())
    }
  }

  private class GHParagraphView(elem: Element) : MyParagraphView(elem) {
    init {
      //language=CSS
      styleSheet.addRule("""
        p {
          margin-bottom: ${JBUI.scale(10)}px;
        }
      """.trimIndent())
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
}