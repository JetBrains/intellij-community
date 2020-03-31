// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.JBUI
import icons.GithubIcons
import java.awt.Graphics
import java.awt.Shape
import javax.swing.JEditorPane
import javax.swing.SizeRequirements
import javax.swing.text.DefaultCaret
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.HTML
import javax.swing.text.html.InlineView
import javax.swing.text.html.ParagraphView
import kotlin.math.max

internal class HtmlEditorPane() : JEditorPane() {
  constructor(body: String) : this() {
    setBody(body)
  }

  init {
    editorKit = object : JBHtmlEditorKit(true) {
      override fun getViewFactory(): ViewFactory {
        return object : JBHtmlFactory() {
          override fun create(elem: Element): View {
            if ("icon-inline" == elem.name) {
              val icon = elem.attributes.getAttribute(HTML.Attribute.SRC)
                ?.let { IconLoader.getIcon(it as String, GithubIcons::class.java) }

              if (icon != null) {
                return object : InlineView(elem) {

                  override fun getPreferredSpan(axis: Int): Float {
                    when (axis) {
                      View.X_AXIS -> return icon.iconWidth.toFloat() + super.getPreferredSpan(axis)
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

            val view = super.create(elem)
            if (view is ParagraphView) {
              return object : ParagraphView(elem) {
                override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
                  var r = r
                  if (r == null) {
                    r = SizeRequirements()
                  }
                  r.minimum = layoutPool.getMinimumSpan(axis).toInt()
                  r.preferred = max(r.minimum, layoutPool.getPreferredSpan(axis).toInt())
                  r.maximum = Integer.MAX_VALUE
                  r.alignment = 0.5f
                  return r
                }
              }
            }
            return view
          }
        }
      }
    }

    isEditable = false
    isOpaque = false
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    margin = JBUI.emptyInsets()

    val caret = caret as DefaultCaret
    caret.updatePolicy = DefaultCaret.NEVER_UPDATE
  }

  fun setBody(body: String) {
    if (body.isEmpty()) {
      text = ""
    }
    else {
      text = "<html><body>$body</body></html>"
    }
  }

  override fun updateUI() {
    super.updateUI()
    UISettings.setupComponentAntialiasing(this)
  }
}